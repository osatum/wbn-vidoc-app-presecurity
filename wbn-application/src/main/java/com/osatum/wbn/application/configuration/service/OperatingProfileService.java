package com.osatum.wbn.application.configuration.service;

import com.osatum.wbn.application.configuration.dto.OperatingContextDto;
import com.osatum.wbn.application.journal.authorization.CanJournalView;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.institution.SearchView;
import com.osatum.wbn.domain.model.institution.SystemFeature;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** Ustala efektywny profil jednostki z dziedziczeniem konfiguracji i egzekwuje dostęp niezależnie od formularza UI. */
@Service
@RequiredArgsConstructor
public class OperatingProfileService {
    private final ConfigurationService configurationService;
    private final InstitutionRepository institutionRepository;
    private final CurrentActorProvider currentActorProvider;

    @CanJournalView
    @Transactional(readOnly = true)
    public OperatingContextDto currentContext() {
        var actor = currentActorProvider.currentActor();
        if (actor.institutionType() != InstitutionType.RESEARCH_UNIT || actor.institutionId() == null) {
            throw new AccessDeniedException("Research unit access is required");
        }
        var institution = institutionRepository.findById(actor.institutionId())
                .orElseThrow(() -> new AccessDeniedException("Research unit access is unavailable"));
        if (institution.getInstitutionType() != InstitutionType.RESEARCH_UNIT
                || institution.getState() != EntityState.ACTIVE) {
            throw new AccessDeniedException("Research unit access is unavailable");
        }
        var defaultProfile = configurationService.getDefaultOperatingProfile();
        var configuredProfile = institution.getOperatingProfile();
        var effectiveProfile = configuredProfile == null ? defaultProfile : configuredProfile;
        return new OperatingContextDto(institution.getId(), configuredProfile, defaultProfile, effectiveProfile,
                effectiveProfile.getAvailableFeatures().stream()
                        .filter(feature -> feature != SystemFeature.DECISION_CARD || configurationService.isDecisionCardEnabled())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()), effectiveProfile.getDefaultSearchView());
    }

    /** Dostęp zależy od efektywnego profilu i flagi funkcji, nie od wybranego formularza BASIC/ADVANCED. */
    @CanJournalView
    @Transactional(readOnly = true)
    public void requireDecisionCard() {
        if (!currentContext().availableFeatures().contains(SystemFeature.DECISION_CARD)) {
            throw new AccessDeniedException("Decision card is not available for the current operating context");
        }
    }

    /** Odrzucenie opóźnionej telemetrii po zmianie dostępu nie może wycofać pozostałego audytu w paczce. */
    @CanJournalView
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public OperatingContextDto decisionAuditContext() {
        return currentContext();
    }

    @CanJournalView
    @Transactional(readOnly = true)
    public SearchView requireSearchView(SearchView requestedView) {
        var context = currentContext();
        var view = requestedView == null ? context.defaultSearchView() : requestedView;
        if (!context.availableFeatures().contains(view.getFeature())) {
            throw new AccessDeniedException("Search view is not available for the current operating profile");
        }
        return view;
    }
}
