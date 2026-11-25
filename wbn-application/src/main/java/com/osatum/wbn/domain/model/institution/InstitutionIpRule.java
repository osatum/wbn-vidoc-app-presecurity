package com.osatum.wbn.domain.model.institution;

import com.osatum.wbn.domain.model.BaseEntity;
import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "institution_ip_rule", indexes = {
        @Index(name = "idx_ip_rule_institution", columnList = "institution_id"),
        @Index(name = "idx_ip_rule_range", columnList = "ip_from_long,ip_to_long"),
        @Index(name = "idx_ip_rule_family", columnList = "institution_id,ip_family")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionIpRule extends GeneratedIdEntity {


    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private IpRuleType ruleType;

    // surowy zapis reguły (np. "10.0.0.1", "10.0.0.0/24", "10.20.*.*")
    @Column(name = "rule_raw", nullable = false)
    private String ruleRaw;

    @Enumerated(EnumType.STRING)
    @Column(name = "ip_family", length = 8)
    private IpAddressFamily addressFamily;

    // znormalizowany zakres dla szybkiego dopasowania (IPv4)
    @Column(name = "ip_from_long")
    private Long ipFromLong;

    @Column(name = "ip_to_long")
    private Long ipToLong;

    @Column(name = "ip_from_bytes")
    private byte[] ipFromBytes;

    @Column(name = "ip_to_bytes")
    private byte[] ipToBytes;

    @Column(name = "cidr_prefix")
    private Integer cidrPrefix;

    @Column(nullable = false)
    private int priority;
}
