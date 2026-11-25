package com.osatum.wbn.infrastructure.security.service;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.user.UserRepository;
import com.osatum.wbn.infrastructure.security.principal.MembershipAccess;
import com.osatum.wbn.infrastructure.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JwtPrincipalService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserPrincipal buildUserPrincipal(Long userId, Long membershipId ){
        return buildUserPrincipal(userId, membershipId, null);
    }

    @Transactional(readOnly = true)
    public UserPrincipal buildUserPrincipal(Long userId, Long membershipId, Long loginSessionId ){

        var user = userRepository.findById(userId).orElseThrow();

        var membership = user.getMemberships().stream()
                .filter(m -> m.getId().equals(membershipId))
                .findFirst()
                .orElseThrow();

        var institutionId = membership.getInstitution().getId();
        InstitutionType institutionType = membership.getInstitution().getInstitutionType();
        InstitutionRole institutionRole = membership.getRole();
        String institutionName = membership.getInstitution().getName();
        return UserPrincipal.builder()
                .userId(userId)
                .membershipId(membershipId)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .orcid(user.getOrcid())
                .name(buildName(user))
                .key(buildKey(user))
                .email(user.getEmail())
                .institutionId(institutionId)
                .institutionName(institutionName)
                .institutionType(institutionType)
                .institutionRole(institutionRole)
                .loginSessionId(loginSessionId)
                .accesses(findAccesses(institutionId, user))
                .build();
    }

    private String buildKey(AppUser user) {
        return user.getFirstName().substring(0,1).toUpperCase() + " " + user.getLastName().substring(0,1).toUpperCase();
    }

    private String buildName(AppUser user) {
        return user.getFirstName() + " " + user.getLastName();
    }

    private List<MembershipAccess> findAccesses(Long institutionId, AppUser user) {
        return user.getMemberships().stream()
                .filter(x -> !institutionId.equals(x.getInstitution().getId()))
                .filter(x -> EntityState.ACTIVE.equals(x.getState()))
                .filter(x -> EntityState.ACTIVE.equals(x.getInstitution().getState()))
                .map(x ->
                        MembershipAccess.builder()
                                .membershipId(x.getId())
                                .name(x.getInstitution().getName())
                                .institutionId(x.getInstitution().getId())
                                .institutionRole(x.getRole())
                                .institutionType(x.getInstitution().getInstitutionType())
                                .build()
                )
                .toList();
    }
}
