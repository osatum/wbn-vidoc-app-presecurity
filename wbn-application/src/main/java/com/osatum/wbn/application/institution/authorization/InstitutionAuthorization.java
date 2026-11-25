package com.osatum.wbn.application.institution.authorization;

import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("institutionAuthorization")
@RequiredArgsConstructor
public class InstitutionAuthorization {
    private final CurrentActorProvider currentActorProvider;
    public boolean canInstitutionManage() {
        var actor = currentActorProvider.currentActor();
        return InstitutionType.SYSTEM_OPERATOR.equals(actor.institutionType()) && InstitutionRole.INSTITUTION_ADMIN.equals(actor.institutionRole());
    }
}
