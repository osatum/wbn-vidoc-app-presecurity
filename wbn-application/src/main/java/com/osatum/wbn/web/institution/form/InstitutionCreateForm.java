package com.osatum.wbn.web.institution.form;

import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.institution.OperatingProfile;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.osatum.wbn.domain.service.net.IpAddressRuleParser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Getter
@Setter
public class InstitutionCreateForm {
    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 4000)
    private String description;

    @Size(max = 4000)
    private String contactData;

    @NotNull
    private InstitutionType institutionType;

    private OperatingProfile operatingProfile;

    @JsonIgnore
    private boolean operatingProfileSpecified;

    @JsonSetter("operatingProfile")
    public void setOperatingProfile(OperatingProfile operatingProfile) {
        this.operatingProfile = operatingProfile;
        this.operatingProfileSpecified = true;
    }

    @AssertTrue(message = "Operating profile is only available for research units")
    public boolean isOperatingProfileValid() {
        return operatingProfile == null || institutionType == InstitutionType.RESEARCH_UNIT;
    }

    @NotNull
    private AuthMode authMode;

    private boolean allowRegistration;

    private boolean allowOtherIp;

    private boolean allowOtherEmailDomain;

    private boolean autoActivateNewAccounts;

    @NotNull
    @Valid
    private List<InstitutionIpRuleCreateForm> institutionIpRules = new ArrayList<>();

    @NotNull
    @Valid
    private List<InstitutionEmailDomainCreateForm> emailDomains = new ArrayList<>();

    @AssertTrue(message = "Email domains must be unique")
    public boolean isEmailDomainsUnique() {
        if (emailDomains == null) {
            return true;
        }

        Set<String> uniqueDomains = new HashSet<>();
        for (InstitutionEmailDomainCreateForm emailDomain : emailDomains) {
            if (emailDomain == null || emailDomain.getDomain() == null) {
                continue;
            }

            String normalized = emailDomain.getDomain().trim().toLowerCase(Locale.ROOT);
            if (!uniqueDomains.add(normalized)) {
                return false;
            }
        }

        return true;
    }

    @AssertTrue(message = "IP rules must be unique")
    public boolean isInstitutionIpRulesUnique() {
        if (institutionIpRules == null) {
            return true;
        }

        Set<String> uniqueRules = new HashSet<>();
        for (InstitutionIpRuleCreateForm ipRule : institutionIpRules) {
            if (ipRule == null || ipRule.getRuleType() == null || ipRule.getRuleRaw() == null) {
                continue;
            }

            String normalized;
            try {
                var parsed = IpAddressRuleParser.parse(
                        ipRule.getRuleType(),
                        ipRule.getRuleRaw(),
                        ipRule.getAddressFamily()
                );
                normalized = parsed.addressFamily() + ":" + parsed.ruleRaw();
            } catch (IllegalArgumentException exception) {
                continue;
            }
            if (!uniqueRules.add(normalized)) {
                return false;
            }
        }

        return true;
    }
}
