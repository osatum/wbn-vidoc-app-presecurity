package com.osatum.wbn.application.configuration.dto;

import com.osatum.wbn.domain.model.institution.OperatingProfile;
import com.osatum.wbn.domain.model.institution.SearchView;
import com.osatum.wbn.domain.model.institution.SystemFeature;

import java.util.Set;

public record OperatingContextDto(Long institutionId, OperatingProfile configuredProfile,
                                  OperatingProfile defaultProfile, OperatingProfile effectiveProfile,
                                  Set<SystemFeature> availableFeatures, SearchView defaultSearchView) {
}
