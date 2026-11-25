package com.osatum.wbn.application.report.survey.sevice;

import com.osatum.wbn.application.report.survey.assembler.SurveyReportDtoAssembler;
import com.osatum.wbn.application.report.survey.assembler.SurveyReportDtoParameters;
import com.osatum.wbn.application.report.survey.dto.SurveyReportDto;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.RatingIcv;
import com.osatum.wbn.domain.model.questionnaire.RatingIcvStatus;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SurveyReportJournalService {
    private static final Locale POLISH_LOCALE = Locale.forLanguageTag("pl");

    private final JournalRepository journalRepository;
    private final SurveyReportDtoAssembler surveyReportDtoAssembler;

    @Transactional(readOnly = true)
    public Optional<LocalizedSurveyReportDto> assemble(Long journalId, Integer year) {
        return journalRepository.findById(journalId)
                .flatMap(journal -> findQuestionnaire(journal, year))
                .filter(this::isVisibleInReport)
                .map(this::assembleLanguages);
    }

    @Transactional(readOnly = true)
    public Optional<SurveyReportDto> assemble(Long journalId, Integer year, Locale locale) {
        Objects.requireNonNull(locale, "locale must not be null");
        return journalRepository.findById(journalId)
                .flatMap(journal -> findQuestionnaire(journal, year))
                .filter(this::isVisibleInReport)
                .map(questionnaire -> assemble(questionnaire, locale));
    }

    private Optional<Questionnaire> findQuestionnaire(Journal journal, Integer year) {
        if (journal.getQuestionnaires() == null) {
            return Optional.empty();
        }
        return journal.getQuestionnaires().stream()
                .filter(questionnaire -> year.equals(questionnaire.getYear()))
                .min(Comparator.comparing(Questionnaire::getId, Comparator.nullsLast(Long::compareTo)));
    }

    private boolean isVisibleInReport(Questionnaire questionnaire) {
        RatingIcv rating = questionnaire.getRating();
        if (rating == null) {
            return false;
        }
        if (RatingIcvStatus.PUBLISHED.equals(rating.getRatingStatus())) {
            return true;
        }
        return RatingIcvStatus.NOT_INDEXED.equals(rating.getRatingStatus())
                && rating.getReasonNotIndexing() != null
                && rating.getReasonNotIndexing().isQuestionarieVisible();
    }

    private LocalizedSurveyReportDto assembleLanguages(Questionnaire questionnaire) {
        SurveyReportDto polish = assemble(questionnaire, POLISH_LOCALE);
        SurveyReportDto english = assemble(questionnaire, Locale.ENGLISH);
        return new LocalizedSurveyReportDto(polish, english);
    }

    private SurveyReportDto assemble(Questionnaire questionnaire, Locale locale) {
        return surveyReportDtoAssembler.toDto(
                new SurveyReportDtoParameters(List.of(questionnaire), null, locale)
        );
    }

    public record LocalizedSurveyReportDto(SurveyReportDto polish, SurveyReportDto english) {
    }
}
