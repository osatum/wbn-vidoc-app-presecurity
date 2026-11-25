package com.osatum.wbn.domain.model.institution;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "institution")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Institution extends GeneratedIdEntity {

    @Column(nullable = false)
    private String name;

    @Column(length = 4000)
    private String description;

    @Column(name = "contact_data", length = 4000)
    private String contactData;

    @Enumerated(EnumType.STRING)
    @Column(name = "institution_type", nullable = false)
    private InstitutionType institutionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operating_profile", length = 32)
    private OperatingProfile operatingProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_mode", nullable = false)
    private AuthMode authMode;

    @Column(name = "allow_registration", nullable = false)
    private boolean allowRegistration;

    @Column(name = "allow_other_ip", nullable = false, columnDefinition = "boolean default false")
    private boolean allowOtherIp;

    @Column(name = "allow_other_email_domain", nullable = false, columnDefinition = "boolean default false")
    private boolean allowOtherEmailDomain;

    @Column(name = "auto_activate_new_accounts", nullable = false)
    private boolean autoActivateNewAccounts;

    @OneToMany(mappedBy = "institution", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<InstitutionMembership> memberships;

    @OneToMany(mappedBy = "institution", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<InstitutionIpRule> institutionIpRules;

    @OneToMany(mappedBy = "institution", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<InstitutionEmailDomain> emailDomains;
}
