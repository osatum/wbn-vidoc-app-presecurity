package com.osatum.wbn.domain.model.institution;

import java.util.Set;

public enum OperatingProfile {
    BASIC(SearchView.BASIC, Set.of(SystemFeature.BASIC_SEARCH)),
    ADVANCED(SearchView.ADVANCED, Set.of(SystemFeature.ADVANCED_SEARCH, SystemFeature.DECISION_CARD)),
    BOTH(SearchView.ADVANCED, Set.of(SystemFeature.BASIC_SEARCH, SystemFeature.ADVANCED_SEARCH, SystemFeature.DECISION_CARD));

    private final SearchView defaultSearchView;
    private final Set<SystemFeature> availableFeatures;

    OperatingProfile(SearchView defaultSearchView, Set<SystemFeature> availableFeatures) {
        this.defaultSearchView = defaultSearchView;
        this.availableFeatures = Set.copyOf(availableFeatures);
    }

    public SearchView getDefaultSearchView() {
        return defaultSearchView;
    }

    public Set<SystemFeature> getAvailableFeatures() {
        return availableFeatures;
    }

    public boolean supports(SearchView view) {
        return view != null && availableFeatures.contains(view.getFeature());
    }
}
