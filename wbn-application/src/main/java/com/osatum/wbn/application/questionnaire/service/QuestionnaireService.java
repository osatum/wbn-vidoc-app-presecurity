package com.osatum.wbn.application.questionnaire.service;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.questionnaire.assembler.QuestionnaireViewMapper;
import com.osatum.wbn.application.questionnaire.view.QuestionnaireView;
import com.osatum.wbn.application.report.survey.assembler.SurveyReportDtoAssembler;
import com.osatum.wbn.application.report.survey.assembler.SurveyReportDtoParameters;
import com.osatum.wbn.application.report.survey.dto.SurveyReportDto;
import com.osatum.wbn.application.report.survey.dto.SurveyReportResult;
import com.osatum.wbn.application.report.survey.sevice.SurveyReportGenerator;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.repository.questionnaire.QuestionnaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class QuestionnaireService {
    private final QuestionnaireViewMapper mapper;
    private final QuestionnaireRepository repository;
    private final ConfigurationService configurationService;
    private final SurveyReportDtoAssembler surveyReportDtoAssembler;
    private final SurveyReportGenerator surveyReportGenerator;

    @Value("${app.questionnaire.detailed-section-enabled:true}")
    private boolean detailedSectionEnabled;

    @Transactional
    public Optional<QuestionnaireView> findCurrentQuestionnaire(Long journalId) {
        return findQuestionnaire(journalId, null);
    }

    @Transactional(readOnly = true)
    public Optional<QuestionnaireView> findQuestionnaire(Long journalId, Integer surveyYear) {
        Integer year = configurationService.resolveSearchYear(surveyYear);

        Optional<Questionnaire> oQuestionnaire = repository.findQuestionnaireByYear(journalId, year);
        if (oQuestionnaire.isEmpty()) {
            return Optional.empty();
        }
        QuestionnaireView questionnaireView = mapper.toView(oQuestionnaire.get());
        questionnaireView.setDetailedSectionEnabled(detailedSectionEnabled);
        return Optional.of(questionnaireView);
    }

    @Transactional(readOnly = true)
    public Optional<SurveyReportResult> generateCurrentQuestionnaireReport(Long journalId, String language) {
        return generateQuestionnaireReport(journalId, language, null);
    }

    @Transactional(readOnly = true)
    public Optional<SurveyReportResult> generateQuestionnaireReport(Long journalId, String language, Integer surveyYear) {
        Integer year = configurationService.resolveSearchYear(surveyYear);

        Optional<Questionnaire> questionnaire = repository.findQuestionnaireByYear(journalId, year);
        if (questionnaire.isEmpty()) {
            return Optional.empty();
        }

        Questionnaire currentQuestionnaire = questionnaire.get();
        Integer reportYear = currentQuestionnaire.getYear() == null ? year : currentQuestionnaire.getYear();
        String reportLanguage = normalizeLanguage(language);
        SurveyReportDto reportDto = surveyReportDtoAssembler.toDto(
                new SurveyReportDtoParameters(List.of(currentQuestionnaire), null, Locale.forLanguageTag(reportLanguage))
        );
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        surveyReportGenerator.generate(reportDto, reportYear, reportLanguage, outputStream);
        byte[] content = outputStream.toByteArray();
        String filename = "survey-" + reportYear + "-" + journalId + "-" + reportLanguage + ".xls";
        return Optional.of(new SurveyReportResult(content, reportYear, reportLanguage, filename));
    }

    @Transactional(readOnly = true)
    public List<Integer> findAvailableYears() {
        return repository.findDistinctYears();
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "pl";
        }

        String normalized = language.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        String languageCode = Locale.forLanguageTag(normalized).getLanguage();
        return languageCode == null || languageCode.isBlank() ? "pl" : languageCode;
    }
}
