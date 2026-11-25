package com.osatum.wbn.domain.model.institution;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.domain.model.user.AppUser;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "institution_membership",
        indexes = {
                @Index(name = "idx_membership_institution", columnList = "institution_id"),
                @Index(name = "idx_membership_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionMembership extends GeneratedIdEntity {


    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstitutionRole role;

    @Column(name = "login_allowed_from_any_ip", nullable = false)
    private boolean loginAllowedFromAnyIp;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activated_by_user_id")
    private AppUser activatedBy;
}
