package com.osatum.wbn.application.report.survey.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScientificDatabaseReportDto {
    private String journalId;
    private String externalRepositoriesGeneral;
    private String indicatorSjrPrevious;
    private String indicatorTcPrevious;
    private String indicatorSnipPrevious;
    private String indicatorCsPrevious;
    private String indicatorIfPrevious;
    private String indicatorAiPrevious;
    private String indicatorCnciPrevious;
    private String indicatorSjrCurrent;
    private String indicatorTcCurrent;
    private String indicatorSnipCurrent;
    private String indicatorCsCurrent;
    private String indicatorIfCurrent;
    private String indicatorAiCurrent;
    private String indicatorCnciCurrent;
}
