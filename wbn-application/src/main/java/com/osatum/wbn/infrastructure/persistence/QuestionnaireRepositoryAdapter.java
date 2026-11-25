package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class QuestionnaireRepositoryAdapter implements com.osatum.wbn.domain.repository.questionnaire.QuestionnaireRepository {
    private final QuestionnaireSpringDataRepository repository;

    @Override
    public Optional<Questionnaire> findQuestionnaireByYear(Long journalId, Integer year) {
        List<Questionnaire> questionnaires = repository.findByYearAndJournalId(year, journalId);
        if (questionnaires.size() > 1) {
            throw new IllegalArgumentException("Ambiguous questionnaire for year " + year);
        }
        if (questionnaires.isEmpty()){
            return Optional.empty();
        }
        return Optional.of(questionnaires.get(0));
    }

    @Override
    public List<Questionnaire> findQuestionnairesByYear(Integer year) {
        return repository.findByYear(year);
    }

    @Override
    public List<Integer> findDistinctYears() {
        return repository.findDistinctYears();
    }
}
