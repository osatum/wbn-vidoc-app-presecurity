package com.osatum.wbn.application.questionnaire.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ScientistMetric {

    private static final long serialVersionUID = 1L;

    private Integer unique;
    @Schema(nullable = true)
    private Integer uniqueExternal;
    @Schema(nullable = true)
    private Integer uniqueForeign;
    private Double externalAffiliationPercentage;
    private Double foreignAffiliationPercentage;
}
