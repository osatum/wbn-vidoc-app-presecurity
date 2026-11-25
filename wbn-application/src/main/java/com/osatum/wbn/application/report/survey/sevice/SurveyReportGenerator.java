package com.osatum.wbn.application.report.survey.sevice;

import com.osatum.wbn.application.report.survey.dto.SurveyReportDto;

import java.io.OutputStream;

public interface SurveyReportGenerator {
    void generate(SurveyReportDto surveyReport, Integer year, String language, OutputStream output);
}
