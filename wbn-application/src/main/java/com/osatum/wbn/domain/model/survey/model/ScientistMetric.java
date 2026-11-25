package com.osatum.wbn.domain.model.survey.model;

import lombok.Data;

import java.io.Serializable;

@Data
/**
 * Metryka naukowa naukowcow
 */
public class ScientistMetric  implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer unique;
    private Integer uniqueExternal;
    private Integer uniqueForeign;
    private Double externalAffiliationPercentage;
    private Double foreignAffiliationPercentage;
}
