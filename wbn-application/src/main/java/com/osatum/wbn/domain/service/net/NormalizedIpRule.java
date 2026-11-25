package com.osatum.wbn.domain.service.net;

import com.osatum.wbn.domain.model.institution.IpAddressFamily;

import java.util.Arrays;

public record NormalizedIpRule(
        IpAddressFamily addressFamily,
        String ruleRaw,
        byte[] ipFrom,
        byte[] ipTo,
        Integer cidrPrefix
) {
    public NormalizedIpRule {
        ipFrom = copy(ipFrom);
        ipTo = copy(ipTo);
    }

    @Override
    public byte[] ipFrom() {
        return copy(ipFrom);
    }

    @Override
    public byte[] ipTo() {
        return copy(ipTo);
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}
