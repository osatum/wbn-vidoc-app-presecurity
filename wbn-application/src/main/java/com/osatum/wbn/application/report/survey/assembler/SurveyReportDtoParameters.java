package com.osatum.wbn.application.report.survey.assembler;

import com.osatum.wbn.application.report.survey.dto.SurveyReportDto;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.shared.assembler.DefaultParameters;

import java.util.List;
import java.util.Locale;

public class SurveyReportDtoParameters extends DefaultParameters<List<Questionnaire>, SurveyReportDto> {

    private final Locale locale;

    public SurveyReportDtoParameters(List<Questionnaire> entity, SurveyReportDto dto) {
        this(entity, dto, Locale.forLanguageTag("pl"));
    }

    public SurveyReportDtoParameters(List<Questionnaire> entity, SurveyReportDto dto, Locale locale) {
        super(entity, dto);
        this.locale = locale == null ? Locale.forLanguageTag("pl") : locale;
    }

    public Locale getLocale() {
        return locale;
    }
}
