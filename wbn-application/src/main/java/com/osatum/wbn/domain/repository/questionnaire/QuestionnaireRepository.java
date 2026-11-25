package com.osatum.wbn.domain.repository.questionnaire;

import com.osatum.wbn.domain.model.questionnaire.Questionnaire;

import java.util.List;
import java.util.Optional;

public interface QuestionnaireRepository {
    Optional<Questionnaire> findQuestionnaireByYear(Long journalId, Integer year);
    List<Questionnaire> findQuestionnairesByYear(Integer year);
    List<Integer> findDistinctYears();
}
