package com.osatum.wbn.domain.model.institution;

public enum SearchView {
    BASIC(SystemFeature.BASIC_SEARCH),
    ADVANCED(SystemFeature.ADVANCED_SEARCH);

    private final SystemFeature feature;

    SearchView(SystemFeature feature) {
        this.feature = feature;
    }

    public SystemFeature getFeature() {
        return feature;
    }
}
