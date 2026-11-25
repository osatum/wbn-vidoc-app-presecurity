package com.osatum.wbn.domain.model.user;


import com.osatum.wbn.domain.model.BaseEntity;
import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppUser extends GeneratedIdEntity {

    @Column(nullable = false, unique = true, length = 128)
    private String email;

    @Column(nullable = false, length = 128)
    private String firstName;

    @Column(nullable = false, length = 128)
    private String lastName;

    @Column(length = 19)
    private String orcid;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // np. "ROLE_USER,ROLE_ADMIN"
    @Column(nullable = false, length = 1024)
    private String roles;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<InstitutionMembership> memberships;
}
