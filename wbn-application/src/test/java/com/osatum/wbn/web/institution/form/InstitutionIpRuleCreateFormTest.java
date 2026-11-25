package com.osatum.wbn.web.institution.form;

import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.model.institution.IpRuleType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstitutionIpRuleCreateFormTest {

    @Test
    void validatesIpv6SingleAddressAndCidr() {
        var form = new InstitutionIpRuleCreateForm();
        form.setAddressFamily(IpAddressFamily.IPV6);
        form.setRuleType(IpRuleType.CIDR);
        form.setRuleRaw("2001:db8:1::/64");

        assertThat(form.isRuleFormatValid()).isTrue();
    }

    @Test
    void rejectsIpv6Range() {
        var form = new InstitutionIpRuleCreateForm();
        form.setAddressFamily(IpAddressFamily.IPV6);
        form.setRuleType(IpRuleType.RANGE);
        form.setRuleRaw("2001:db8::1-2001:db8::2");

        assertThat(form.isRuleFormatValid()).isFalse();
    }
}
