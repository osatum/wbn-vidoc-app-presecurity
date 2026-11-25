package com.osatum.wbn.application.system.authorization;

import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("systemAuthorization")
@RequiredArgsConstructor
public class SystemAuthorization {
    private final CurrentActorProvider currentActorProvider;
    public boolean canSystemManage() {
        var actor = currentActorProvider.currentActor();
        return InstitutionType.SYSTEM_OPERATOR.equals(actor.institutionType()) && InstitutionRole.INSTITUTION_ADMIN.equals(actor.institutionRole());
    }
}
