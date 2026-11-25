package com.osatum.wbn.application.report.survey.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CooperationReportDto {
    private String journalId;
    private String authorUnique;
    private Double authorExternalAffiliationPercentage;
    private Double authorForeignAffiliationPercentage;
    private String reviewerUnique;
    private Double reviewerExternalAffiliationPercentage;
    private Double reviewerForeignAffiliationPercentage;
    private String directorsUnique;
    private Double directorsExternalAffiliationPercentage;
    private Double directorsForeignAffiliationPercentage;
}
