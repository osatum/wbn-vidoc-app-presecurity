package com.osatum.wbn.domain.model.institution;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "institution_email_domain",
        uniqueConstraints = @UniqueConstraint(name = "uk_institution_email_domain", columnNames = {"institution_id", "domain"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionEmailDomain extends GeneratedIdEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Column(nullable = false)
    private String domain;
}
