package com.osatum.wbn.domain.service.net;

import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.model.institution.IpRuleType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IpAddressRuleParserTest {

    @Test
    void matchesIpv6AddressInsideCidrRange() {
        var rule = IpAddressRuleParser.parse(IpRuleType.CIDR, "2001:db8:1234::12/48", IpAddressFamily.IPV6);

        assertThat(rule.addressFamily()).isEqualTo(IpAddressFamily.IPV6);
        assertThat(rule.cidrPrefix()).isEqualTo(48);
        assertThat(IpAddressRuleParser.isWithinRange(
                IpAddressRuleParser.parseClientAddress("2001:db8:1234:ffff::1"),
                rule.ipFrom(),
                rule.ipTo(),
                rule.addressFamily()
        )).isTrue();
        assertThat(IpAddressRuleParser.isWithinRange(
                IpAddressRuleParser.parseClientAddress("2001:db8:1235::1"),
                rule.ipFrom(),
                rule.ipTo(),
                rule.addressFamily()
        )).isFalse();
    }

    @Test
    void normalizesIpv4MappedIpv6ClientAddressToIpv4() {
        var address = IpAddressRuleParser.parseClientAddress("::ffff:192.0.2.10");

        assertThat(address.addressFamily()).isEqualTo(IpAddressFamily.IPV4);
        assertThat(address.canonicalValue()).isEqualTo("192.0.2.10");
    }

    @Test
    void preservesIpv4AndIpv6PrefixBoundaries() {
        var ipv4 = IpAddressRuleParser.parse(IpRuleType.CIDR, "198.51.100.10/32", IpAddressFamily.IPV4);
        var allIpv6 = IpAddressRuleParser.parse(IpRuleType.CIDR, "::/0", IpAddressFamily.IPV6);
        var singleIpv6 = IpAddressRuleParser.parse(
                IpRuleType.SINGLE_IP,
                "2001:0db8:0000:0000:0000:0000:0000:0001",
                IpAddressFamily.IPV6
        );

        assertThat(IpAddressRuleParser.isWithinRange(
                IpAddressRuleParser.parseClientAddress("198.51.100.10"),
                ipv4.ipFrom(),
                ipv4.ipTo(),
                ipv4.addressFamily()
        )).isTrue();
        assertThat(IpAddressRuleParser.isWithinRange(
                IpAddressRuleParser.parseClientAddress("198.51.100.11"),
                ipv4.ipFrom(),
                ipv4.ipTo(),
                ipv4.addressFamily()
        )).isFalse();
        assertThat(IpAddressRuleParser.isWithinRange(
                IpAddressRuleParser.parseClientAddress("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"),
                allIpv6.ipFrom(),
                allIpv6.ipTo(),
                allIpv6.addressFamily()
        )).isTrue();
        assertThat(singleIpv6.cidrPrefix()).isEqualTo(128);
        assertThat(IpAddressRuleParser.isWithinRange(
                IpAddressRuleParser.parseClientAddress("2001:db8::2"),
                singleIpv6.ipFrom(),
                singleIpv6.ipTo(),
                singleIpv6.addressFamily()
        )).isFalse();
    }

    @Test
    void rejectsUnsupportedIpv6RuleKindsAndNonLiteralValues() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                IpAddressRuleParser.parse(IpRuleType.RANGE, "2001:db8::1-2001:db8::2", IpAddressFamily.IPV6)
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
                IpAddressRuleParser.parse(IpRuleType.SINGLE_IP, "example.org", IpAddressFamily.IPV6)
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
                IpAddressRuleParser.parse(IpRuleType.SINGLE_IP, "::ffff:192.0.2.10", IpAddressFamily.IPV6)
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
                IpAddressRuleParser.parse(IpRuleType.CIDR, "2001:db8::/129", IpAddressFamily.IPV6)
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
                IpAddressRuleParser.parseClientAddress("[2001:db8::1]:443")
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
                IpAddressRuleParser.parseClientAddress("fe80::1%eth0")
        );
    }
}
