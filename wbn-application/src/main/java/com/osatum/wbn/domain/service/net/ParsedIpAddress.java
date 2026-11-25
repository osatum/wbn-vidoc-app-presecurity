package com.osatum.wbn.domain.service.net;

import com.osatum.wbn.domain.model.institution.IpAddressFamily;

import java.util.Arrays;

public record ParsedIpAddress(
        IpAddressFamily addressFamily,
        byte[] bytes,
        String canonicalValue
) {
    public ParsedIpAddress {
        bytes = copy(bytes);
    }

    @Override
    public byte[] bytes() {
        return copy(bytes);
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}
