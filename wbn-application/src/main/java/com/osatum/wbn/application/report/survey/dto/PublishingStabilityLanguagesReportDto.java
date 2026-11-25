package com.osatum.wbn.application.report.survey.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublishingStabilityLanguagesReportDto {
    private String journalId;
    private String language;
    private String previousValue;
    private String currentValue;
}
