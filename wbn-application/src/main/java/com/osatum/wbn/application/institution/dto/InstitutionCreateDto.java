package com.osatum.wbn.application.institution.dto;

import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.institution.OperatingProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class InstitutionCreateDto {
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
    private boolean operatingProfileSpecified;

    public void setOperatingProfile(OperatingProfile operatingProfile) {
        this.operatingProfile = operatingProfile;
        this.operatingProfileSpecified = true;
    }

    @NotNull
    private AuthMode authMode;

    private boolean allowRegistration;

    private boolean allowOtherIp;

    private boolean allowOtherEmailDomain;

    private boolean autoActivateNewAccounts;

    @Valid
    private List<InstitutionIpRuleCreateDto> institutionIpRules = new ArrayList<>();

    @Valid
    private List<InstitutionEmailDomainCreateDto> emailDomains = new ArrayList<>();
}
