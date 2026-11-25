package com.osatum.wbn.application.report.survey.dto;

public record SurveyReportResult(
        byte[] content,
        Integer year,
        String language,
        String filename
) {
}
