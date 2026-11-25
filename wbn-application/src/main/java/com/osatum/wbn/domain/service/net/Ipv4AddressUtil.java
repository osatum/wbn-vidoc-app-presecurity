package com.osatum.wbn.domain.service.net;

import java.net.InetAddress;

public final class Ipv4AddressUtil {

    private Ipv4AddressUtil() {
    }

    public static long ipv4ToLong(String ipAddress) {
        try {
            byte[] bytes = InetAddress.getByName(ipAddress).getAddress();
            if (bytes.length != 4) {
                throw new IllegalArgumentException("Not an IPv4 address: " + ipAddress);
            }
            return ((long) bytes[0] & 0xff) << 24
                    | ((long) bytes[1] & 0xff) << 16
                    | ((long) bytes[2] & 0xff) << 8
                    | ((long) bytes[3] & 0xff);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress, exception);
        }
    }

    public static String longToIpv4(long value) {
        if (value < 0 || value > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("IPv4 value out of range: " + value);
        }

        return ((value >> 24) & 0xff) + "."
                + ((value >> 16) & 0xff) + "."
                + ((value >> 8) & 0xff) + "."
                + (value & 0xff);
    }

    public static long[] cidrToRange(String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CIDR: " + cidr);
        }

        int prefix = Integer.parseInt(parts[1].trim());
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Invalid CIDR prefix: " + cidr);
        }

        long base = ipv4ToLong(parts[0].trim());
        long mask = prefix == 0 ? 0 : 0xFFFFFFFFL << (32 - prefix);
        mask &= 0xFFFFFFFFL;
        long network = base & mask;
        long broadcast = network | (~mask & 0xFFFFFFFFL);
        return new long[]{network, broadcast};
    }

    public static long[] wildcardToRange(String pattern) {
        String[] octets = pattern.split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("Invalid wildcard pattern: " + pattern);
        }

        StringBuilder from = new StringBuilder();
        StringBuilder to = new StringBuilder();
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index].trim();
            if (octet.equals("*") || octet.isEmpty()) {
                from.append(0);
                to.append(255);
            } else {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) {
                    throw new IllegalArgumentException("Invalid octet: " + pattern);
                }
                from.append(value);
                to.append(value);
            }
            if (index < 3) {
                from.append('.');
                to.append('.');
            }
        }
        return new long[]{ipv4ToLong(from.toString()), ipv4ToLong(to.toString())};
    }
}
