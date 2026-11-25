package com.osatum.wbn.domain.service.net;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ipv4AddressUtilTest {

    @Test
    void convertsIpv4InBothDirections() {
        long value = Ipv4AddressUtil.ipv4ToLong("192.168.10.25");

        assertThat(Ipv4AddressUtil.longToIpv4(value)).isEqualTo("192.168.10.25");
    }

    @Test
    void resolvesCidrAndWildcardRanges() {
        assertThat(Ipv4AddressUtil.cidrToRange("10.20.30.0/24"))
                .containsExactly(
                        Ipv4AddressUtil.ipv4ToLong("10.20.30.0"),
                        Ipv4AddressUtil.ipv4ToLong("10.20.30.255")
                );
        assertThat(Ipv4AddressUtil.wildcardToRange("10.20.*.*"))
                .containsExactly(
                        Ipv4AddressUtil.ipv4ToLong("10.20.0.0"),
                        Ipv4AddressUtil.ipv4ToLong("10.20.255.255")
                );
    }

    @Test
    void rejectsInvalidIpv4Value() {
        assertThatThrownBy(() -> Ipv4AddressUtil.longToIpv4(0x1_0000_0000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
