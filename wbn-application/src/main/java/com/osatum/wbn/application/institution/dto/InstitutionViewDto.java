package com.osatum.wbn.application.institution.dto;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.institution.OperatingProfile;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class InstitutionViewDto {
    private Long id;
    private String name;
    private String description;
    private String contactData;
    private InstitutionType institutionType;
    private OperatingProfile operatingProfile;
    private AuthMode authMode;
    private boolean allowRegistration;
    private boolean allowOtherIp;
    private boolean allowOtherEmailDomain;
    private boolean autoActivateNewAccounts;
    private List<InstitutionIpRuleDto> institutionIpRules;
    private List<InstitutionEmailDomainDto> emailDomains;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime editedAt;
    private String editedBy;
    private EntityState state;
}
