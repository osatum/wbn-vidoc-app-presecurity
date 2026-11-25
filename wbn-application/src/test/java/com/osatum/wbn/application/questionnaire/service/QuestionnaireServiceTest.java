package com.osatum.wbn.application.questionnaire.service;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.questionnaire.assembler.QuestionnaireViewMapper;
import com.osatum.wbn.application.questionnaire.view.QuestionnaireView;
import com.osatum.wbn.application.report.survey.assembler.SurveyReportDtoAssembler;
import com.osatum.wbn.application.report.survey.assembler.SurveyReportDtoParameters;
import com.osatum.wbn.application.report.survey.dto.SurveyReportDto;
import com.osatum.wbn.application.report.survey.sevice.SurveyReportGenerator;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.repository.questionnaire.QuestionnaireRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class QuestionnaireServiceTest {

    @ParameterizedTest
    @ValueSource(ints = {2024, 2025})
    void selectedPublishedYearIsUsedForBothQuestionnaireAndReport(int year) {
        var configuration = spy(new ConfigurationService());
        org.mockito.Mockito.doReturn(2025).when(configuration).getCurrentRatingYear();
        org.mockito.Mockito.doReturn(List.of(2025, 2024)).when(configuration).getAvailableSearchYears();
        QuestionnaireRepository repository = mock();
        QuestionnaireViewMapper mapper = mock();
        SurveyReportDtoAssembler assembler = mock();
        SurveyReportGenerator generator = mock();
        var service = new QuestionnaireService(mapper, repository, configuration, assembler, generator);
        var questionnaire = new Questionnaire();
        questionnaire.setYear(year);
        when(repository.findQuestionnaireByYear(123L, year)).thenReturn(Optional.of(questionnaire));
        when(mapper.toView(questionnaire)).thenReturn(new QuestionnaireView());
        var reportDto = new SurveyReportDto();
        when(assembler.toDto(any(SurveyReportDtoParameters.class))).thenReturn(reportDto);

        assertThat(service.findQuestionnaire(123L, year)).isPresent();
        var report = service.generateQuestionnaireReport(123L, "pl", year).orElseThrow();

        verify(generator).generate(eq(reportDto), eq(year), eq("pl"), any(OutputStream.class));
        assertThat(report.year()).isEqualTo(year);
        assertThat(report.filename()).contains("survey-" + year + "-");
        verify(repository, org.mockito.Mockito.times(2)).findQuestionnaireByYear(123L, year);
    }

    @ParameterizedTest
    @ValueSource(ints = {2024, 2025})
    void includesDetailedSectionVisibilityInQuestionnaireForCurrentRatingYear(int year) {
        QuestionnaireViewMapper mapper = mock();
        QuestionnaireRepository repository = mock();
        ConfigurationService configurationService = spy(new ConfigurationService());
        SurveyReportDtoAssembler surveyReportDtoAssembler = mock();
        SurveyReportGenerator surveyReportGenerator = mock();
        QuestionnaireService service = new QuestionnaireService(
                mapper,
                repository,
                configurationService,
                surveyReportDtoAssembler,
                surveyReportGenerator
        );
        ReflectionTestUtils.setField(service, "detailedSectionEnabled", true);

        Long journalId = 1L;
        Questionnaire questionnaire = mock();
        QuestionnaireView questionnaireView = new QuestionnaireView();
        when(configurationService.getCurrentRatingYear()).thenReturn(year);
        when(repository.findQuestionnaireByYear(journalId, year)).thenReturn(Optional.of(questionnaire));
        when(mapper.toView(questionnaire)).thenReturn(questionnaireView);

        QuestionnaireView result = service.findCurrentQuestionnaire(journalId).orElseThrow();

        assertThat(result.isDetailedSectionEnabled()).isTrue();
        verify(repository).findQuestionnaireByYear(journalId, year);
    }

    @ParameterizedTest
    @ValueSource(ints = {2024, 2025})
    void reportUsesTheCurrentRatingYearQuestionnaireAndFilename(int year) {
        QuestionnaireRepository repository = mock();
        ConfigurationService configuration = spy(new ConfigurationService());
        SurveyReportDtoAssembler assembler = mock();
        SurveyReportGenerator generator = mock();
        var service = new QuestionnaireService(mock(QuestionnaireViewMapper.class), repository,
                configuration, assembler, generator);
        var questionnaire = new Questionnaire();
        questionnaire.setYear(year);
        var reportDto = new SurveyReportDto();
        when(configuration.getCurrentRatingYear()).thenReturn(year);
        when(repository.findQuestionnaireByYear(123L, year)).thenReturn(Optional.of(questionnaire));
        when(assembler.toDto(any(SurveyReportDtoParameters.class))).thenReturn(reportDto);

        var report = service.generateCurrentQuestionnaireReport(123L, "pl").orElseThrow();

        var parameters = ArgumentCaptor.forClass(SurveyReportDtoParameters.class);
        verify(assembler).toDto(parameters.capture());
        assertThat(parameters.getValue().getEntity()).isEqualTo(List.of(questionnaire));
        verify(generator).generate(eq(reportDto), eq(year), eq("pl"), any(OutputStream.class));
        assertThat(report.year()).isEqualTo(year);
        assertThat(report.filename()).isEqualTo("survey-" + year + "-123-pl.xls");
    }
}
