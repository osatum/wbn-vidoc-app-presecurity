package com.osatum.wbn.application.configuration.dto;

import java.util.List;

public record SearchYearsDto(Integer defaultYear, List<Integer> availableYears) {
    public SearchYearsDto {
        availableYears = List.copyOf(availableYears);
    }
}
