package com.osatum.wbn.application.journal.authorization;

import com.osatum.wbn.application.security.Actor;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component("journalAuthorization")
@RequiredArgsConstructor
public class JournalAuthorization {
    private final CurrentActorProvider currentActorProvider;
    public boolean canJournalView() {
        var actor = currentActorProvider.currentActor();
        return InstitutionType.RESEARCH_UNIT.equals(actor.institutionType()) && checkRole(actor);
    }

    private boolean checkRole(Actor actor) {
        InstitutionRole[] roles = new InstitutionRole[] {InstitutionRole.INSTITUTION_VIEWER, InstitutionRole.INSTITUTION_USER, InstitutionRole.INSTITUTION_ADMIN};
        return Arrays.stream(roles).anyMatch(x -> x.equals(actor.institutionRole()));
    }
}
