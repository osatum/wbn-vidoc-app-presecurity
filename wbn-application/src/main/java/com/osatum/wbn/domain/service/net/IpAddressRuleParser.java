package com.osatum.wbn.domain.service.net;

import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.model.institution.IpRuleType;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;

public final class IpAddressRuleParser {

    private static final String IPV4_OCTET = "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final String IPV4_PATTERN = "^" + IPV4_OCTET + "(?:\\." + IPV4_OCTET + "){3}$";
    private static final String IPV6_CHARACTERS_PATTERN = "^[0-9a-fA-F:.]+$";

    private IpAddressRuleParser() {
    }

    public static NormalizedIpRule parse(
            IpRuleType ruleType,
            String ruleRaw,
            IpAddressFamily requestedFamily
    ) {
        if (ruleType == null || ruleRaw == null || ruleRaw.isBlank()) {
            throw new IllegalArgumentException("IP rule type and value are required");
        }

        String normalizedRaw = ruleRaw.trim();
        IpAddressFamily addressFamily = requestedFamily == null
                ? inferAddressFamily(ruleType, normalizedRaw)
                : requestedFamily;

        return switch (ruleType) {
            case SINGLE_IP -> singleIp(normalizedRaw, addressFamily);
            case CIDR -> cidr(normalizedRaw, addressFamily);
            case RANGE -> range(normalizedRaw, addressFamily);
            case WILDCARD -> wildcard(normalizedRaw, addressFamily);
        };
    }

    public static ParsedIpAddress parseClientAddress(String value) {
        return parseAddress(value, null, true);
    }

    public static String normalizeClientAddress(String value) {
        return parseClientAddress(value).canonicalValue();
    }

    public static boolean isWithinRange(ParsedIpAddress address, byte[] from, byte[] to, IpAddressFamily addressFamily) {
        if (address == null || from == null || to == null || addressFamily == null) {
            return false;
        }
        if (address.addressFamily() != addressFamily
                || from.length != address.bytes().length
                || to.length != address.bytes().length) {
            return false;
        }
        return compareUnsigned(address.bytes(), from) >= 0 && compareUnsigned(address.bytes(), to) <= 0;
    }

    public static boolean matchesIpv4Wildcard(ParsedIpAddress address, String ruleRaw) {
        if (address == null || address.addressFamily() != IpAddressFamily.IPV4 || ruleRaw == null) {
            return false;
        }

        String wildcard = normalizeIpv4Wildcard(ruleRaw);
        String[] ruleOctets = wildcard.split("\\.", -1);
        if (ruleOctets.length != 4) {
            return false;
        }

        String[] addressOctets = address.canonicalValue().split("\\.");
        for (int index = 0; index < ruleOctets.length; index++) {
            String ruleOctet = ruleOctets[index];
            if ("*".equals(ruleOctet)) {
                continue;
            }
            if (!ruleOctet.equals(addressOctets[index])) {
                return false;
            }
        }
        return true;
    }

    public static String toAddressString(byte[] bytes) {
        if (bytes == null || (bytes.length != 4 && bytes.length != 16)) {
            throw new IllegalArgumentException("Invalid IP address bytes");
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Invalid IP address bytes", exception);
        }
    }

    public static long ipv4BytesToLong(byte[] bytes) {
        if (bytes == null || bytes.length != 4) {
            throw new IllegalArgumentException("Not an IPv4 address");
        }
        return ((long) bytes[0] & 0xff) << 24
                | ((long) bytes[1] & 0xff) << 16
                | ((long) bytes[2] & 0xff) << 8
                | ((long) bytes[3] & 0xff);
    }

    private static NormalizedIpRule singleIp(String ruleRaw, IpAddressFamily addressFamily) {
        ParsedIpAddress address = parseRuleAddress(ruleRaw, addressFamily);
        int prefix = addressFamily == IpAddressFamily.IPV4 ? 32 : 128;
        return new NormalizedIpRule(addressFamily, address.canonicalValue(), address.bytes(), address.bytes(), prefix);
    }

    private static NormalizedIpRule cidr(String ruleRaw, IpAddressFamily addressFamily) {
        String[] parts = ruleRaw.split("/", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Invalid CIDR rule");
        }

        ParsedIpAddress address = parseRuleAddress(parts[0].trim(), addressFamily);
        int prefix = parsePrefix(parts[1].trim(), addressFamily);
        byte[] from = networkAddress(address.bytes(), prefix);
        byte[] to = broadcastAddress(from, prefix);
        String canonicalValue = toAddressString(from) + "/" + prefix;
        return new NormalizedIpRule(addressFamily, canonicalValue, from, to, prefix);
    }

    private static NormalizedIpRule range(String ruleRaw, IpAddressFamily addressFamily) {
        if (addressFamily != IpAddressFamily.IPV4) {
            throw new IllegalArgumentException("IPv6 supports only SINGLE_IP and CIDR rules");
        }

        String[] parts = ruleRaw.split("-", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Invalid IP range rule");
        }

        ParsedIpAddress from = parseRuleAddress(parts[0].trim(), addressFamily);
        ParsedIpAddress to = parseRuleAddress(parts[1].trim(), addressFamily);
        if (compareUnsigned(from.bytes(), to.bytes()) > 0) {
            throw new IllegalArgumentException("Invalid IP range: from must not exceed to");
        }
        return new NormalizedIpRule(
                addressFamily,
                from.canonicalValue() + "-" + to.canonicalValue(),
                from.bytes(),
                to.bytes(),
                null
        );
    }

    private static NormalizedIpRule wildcard(String ruleRaw, IpAddressFamily addressFamily) {
        if (addressFamily != IpAddressFamily.IPV4) {
            throw new IllegalArgumentException("IPv6 supports only SINGLE_IP and CIDR rules");
        }

        String normalizedWildcard = normalizeIpv4Wildcard(ruleRaw);
        String[] octets = normalizedWildcard.split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 wildcard rule");
        }

        StringBuilder from = new StringBuilder();
        StringBuilder to = new StringBuilder();
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if ("*".equals(octet)) {
                from.append('0');
                to.append("255");
            } else {
                if (!octet.matches("^" + IPV4_OCTET + "$")) {
                    throw new IllegalArgumentException("Invalid IPv4 wildcard rule");
                }
                from.append(octet);
                to.append(octet);
            }
            if (index < octets.length - 1) {
                from.append('.');
                to.append('.');
            }
        }

        ParsedIpAddress fromAddress = parseRuleAddress(from.toString(), addressFamily);
        ParsedIpAddress toAddress = parseRuleAddress(to.toString(), addressFamily);
        return new NormalizedIpRule(addressFamily, normalizedWildcard, fromAddress.bytes(), toAddress.bytes(), null);
    }

    private static IpAddressFamily inferAddressFamily(IpRuleType ruleType, String ruleRaw) {
        if (ruleType == IpRuleType.WILDCARD) {
            return IpAddressFamily.IPV4;
        }

        String addressPart = ruleRaw;
        if (ruleType == IpRuleType.CIDR) {
            addressPart = ruleRaw.split("/", -1)[0];
        } else if (ruleType == IpRuleType.RANGE) {
            addressPart = ruleRaw.split("-", -1)[0];
        }

        return addressPart.contains(":") ? IpAddressFamily.IPV6 : IpAddressFamily.IPV4;
    }

    private static ParsedIpAddress parseRuleAddress(String value, IpAddressFamily addressFamily) {
        ParsedIpAddress address = parseAddress(value, addressFamily, false);
        if (addressFamily == IpAddressFamily.IPV6 && value.contains(".")) {
            throw new IllegalArgumentException("IPv4-mapped IPv6 addresses are not valid IP rules");
        }
        return address;
    }

    private static ParsedIpAddress parseAddress(
            String value,
            IpAddressFamily expectedFamily,
            boolean allowIpv4MappedIpv6
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IP address is required");
        }

        String candidate = value.trim();
        if (candidate.indexOf('[') >= 0 || candidate.indexOf(']') >= 0 || candidate.indexOf('%') >= 0) {
            throw new IllegalArgumentException("IP address must not contain a port or zone identifier");
        }

        if (candidate.matches(IPV4_PATTERN)) {
            return fromBytes(parseIpv4Bytes(candidate), expectedFamily);
        }

        if (!candidate.contains(":") || !candidate.matches(IPV6_CHARACTERS_PATTERN)) {
            throw new IllegalArgumentException("Invalid IP address");
        }

        try {
            byte[] bytes = InetAddress.getByName(candidate).getAddress();
            if (bytes.length == 4 && !allowIpv4MappedIpv6) {
                throw new IllegalArgumentException("IPv4-mapped IPv6 addresses are not valid IP rules");
            }
            return fromBytes(bytes, expectedFamily);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Invalid IP address", exception);
        }
    }

    private static ParsedIpAddress fromBytes(byte[] bytes, IpAddressFamily expectedFamily) {
        IpAddressFamily actualFamily = switch (bytes.length) {
            case 4 -> IpAddressFamily.IPV4;
            case 16 -> IpAddressFamily.IPV6;
            default -> throw new IllegalArgumentException("Invalid IP address length");
        };
        if (expectedFamily != null && expectedFamily != actualFamily) {
            throw new IllegalArgumentException("IP address family does not match selected rule family");
        }
        return new ParsedIpAddress(actualFamily, bytes, toAddressString(bytes));
    }

    private static byte[] parseIpv4Bytes(String value) {
        String[] octets = value.split("\\.", -1);
        byte[] bytes = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            bytes[index] = (byte) Integer.parseInt(octets[index]);
        }
        return bytes;
    }

    private static int parsePrefix(String value, IpAddressFamily addressFamily) {
        try {
            int prefix = Integer.parseInt(value);
            int maximum = addressFamily == IpAddressFamily.IPV4 ? 32 : 128;
            if (prefix < 0 || prefix > maximum) {
                throw new IllegalArgumentException("Invalid CIDR prefix");
            }
            return prefix;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid CIDR prefix", exception);
        }
    }

    private static byte[] networkAddress(byte[] bytes, int prefix) {
        byte[] result = Arrays.copyOf(bytes, bytes.length);
        int completeBytes = prefix / Byte.SIZE;
        int remainder = prefix % Byte.SIZE;

        if (remainder != 0 && completeBytes < result.length) {
            int mask = 0xff << (Byte.SIZE - remainder);
            result[completeBytes] = (byte) (result[completeBytes] & mask);
            completeBytes++;
        }
        Arrays.fill(result, completeBytes, result.length, (byte) 0);
        return result;
    }

    private static byte[] broadcastAddress(byte[] network, int prefix) {
        byte[] result = Arrays.copyOf(network, network.length);
        int completeBytes = prefix / Byte.SIZE;
        int remainder = prefix % Byte.SIZE;

        if (remainder != 0 && completeBytes < result.length) {
            int hostMask = (1 << (Byte.SIZE - remainder)) - 1;
            result[completeBytes] = (byte) (result[completeBytes] | hostMask);
            completeBytes++;
        }
        Arrays.fill(result, completeBytes, result.length, (byte) 0xff);
        return result;
    }

    private static int compareUnsigned(byte[] first, byte[] second) {
        if (first.length != second.length) {
            return Integer.compare(first.length, second.length);
        }
        for (int index = 0; index < first.length; index++) {
            int comparison = Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static String normalizeIpv4Wildcard(String value) {
        String normalized = value.trim().replace(" ", "");
        if (normalized.endsWith("..")) {
            String prefix = normalized.substring(0, normalized.length() - 2);
            normalized = prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix;
            normalized += ".*.*";
        }
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1) + ".*.*";
        }

        String[] octets = normalized.split("\\.", -1);
        if (octets.length > 0 && octets.length < 4) {
            normalized += ".*".repeat(4 - octets.length);
        }
        return normalized;
    }
}
