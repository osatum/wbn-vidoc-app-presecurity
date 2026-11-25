package com.osatum.wbn.infrastructure.security;

import com.osatum.wbn.application.security.Actor;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.InstitutionActor;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.infrastructure.security.principal.InstitutionPrincipal;
import com.osatum.wbn.infrastructure.security.principal.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SpringSecurityCurrentActorProvider implements CurrentActorProvider {

    @Override
    public Actor currentActor() {
        return findCurrentActor().orElseThrow(() -> new AccessDeniedException("Unauthenticated"));
    }

    @Override
    public Optional<Actor> findCurrentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        var principal = authentication.getPrincipal();

        return Optional.ofNullable(switch (principal) {
            case UserPrincipal userPrincipal -> new UserActor(
                    userPrincipal.getUserId(),
                    userPrincipal.getEmail(),
                    userPrincipal.getInstitutionId(),
                    userPrincipal.getInstitutionType(),
                    userPrincipal.getInstitutionRole(),
                    userPrincipal.getLoginSessionId()
            );

            case InstitutionPrincipal institutionPrincipal -> new InstitutionActor(
                    institutionPrincipal.institutionId(),
                    institutionPrincipal.institutionType(),
                    institutionPrincipal.institutionRole(),
                    institutionPrincipal.name(),
                    institutionPrincipal.loginSessionId()
            );

            default -> null;
        });
    }
}
