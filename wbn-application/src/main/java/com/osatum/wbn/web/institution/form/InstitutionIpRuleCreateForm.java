package com.osatum.wbn.web.institution.form;

import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.model.institution.IpRuleType;
import com.osatum.wbn.domain.service.net.IpAddressRuleParser;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstitutionIpRuleCreateForm {
    @NotNull
    private IpRuleType ruleType;

    private IpAddressFamily addressFamily;

    @NotBlank
    @Size(max = 255)
    private String ruleRaw;

    @PositiveOrZero
    private int priority;

    @AssertTrue(message = "IP rule format does not match selected rule type")
    public boolean isRuleFormatValid() {
        if (ruleType == null || ruleRaw == null || ruleRaw.isBlank()) {
            return true;
        }
        try {
            IpAddressRuleParser.parse(ruleType, ruleRaw, addressFamily);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
