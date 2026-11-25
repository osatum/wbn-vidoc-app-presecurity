package com.osatum.wbn.application.shared.request;

import com.osatum.wbn.domain.service.net.IpAddressRuleParser;
import com.osatum.wbn.domain.service.net.NormalizedIpRule;
import com.osatum.wbn.domain.service.net.ParsedIpAddress;
import com.osatum.wbn.domain.model.institution.IpRuleType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class ClientRequestMetadataResolutionService {

    private static final int MAX_USER_AGENT_LENGTH = 1024;

    private final List<NormalizedIpRule> trustedProxies;

    public ClientRequestMetadataResolutionService(
            @Value("${app.web.trusted-proxies:}") String trustedProxies
    ) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::parseTrustedProxy)
                .toList();
    }

    public ClientRequestMetadata resolve(ClientRequestMetadataInput input) {
        String remoteAddress = normalizeIp(input.remoteAddress());
        String clientIp = resolveIpAddress(input, remoteAddress);
        return new ClientRequestMetadata(clientIp, resolveUserAgent(input.userAgent()));
    }

    private String resolveIpAddress(ClientRequestMetadataInput input, String remoteAddress) {
        if (remoteAddress.isBlank()) {
            return "";
        }
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = normalize(input.forwardedFor());
        if (!forwardedFor.isBlank()) {
            var chain = new ArrayList<String>();
            for (String rawAddress : forwardedFor.split(",", -1)) {
                String address = normalizeIp(rawAddress);
                if (address.isBlank()) {
                    return "";
                }
                chain.add(address);
            }
            chain.add(remoteAddress);

            for (int index = chain.size() - 1; index >= 0; index--) {
                String candidate = chain.get(index);
                if (!isTrustedProxy(candidate)) {
                    return candidate;
                }
            }
        }

        String realIp = normalize(input.realIp());
        return realIp.isBlank() ? "" : normalizeIp(realIp);
    }

    private String normalizeIp(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return "";
        }
        try {
            return IpAddressRuleParser.normalizeClientAddress(normalized);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private NormalizedIpRule parseTrustedProxy(String value) {
        IpRuleType ruleType = value.contains("/") ? IpRuleType.CIDR : IpRuleType.SINGLE_IP;
        return IpAddressRuleParser.parse(ruleType, value, null);
    }

    private boolean isTrustedProxy(String value) {
        if (value.isBlank()) {
            return false;
        }
        try {
            ParsedIpAddress address = IpAddressRuleParser.parseClientAddress(value);
            return trustedProxies.stream().anyMatch(rule -> IpAddressRuleParser.isWithinRange(
                    address,
                    rule.ipFrom(),
                    rule.ipTo(),
                    rule.addressFamily()
            ));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String resolveUserAgent(String value) {
        String userAgent = normalize(value);
        if (userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() > MAX_USER_AGENT_LENGTH
                ? userAgent.substring(0, MAX_USER_AGENT_LENGTH)
                : userAgent;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace("\r", "").replace("\n", "").trim();
    }
}
