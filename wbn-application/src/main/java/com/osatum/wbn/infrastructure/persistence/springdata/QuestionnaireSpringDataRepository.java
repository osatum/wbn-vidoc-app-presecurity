package com.osatum.wbn.infrastructure.persistence.springdata;


import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionnaireSpringDataRepository extends JpaRepository<Questionnaire, Long> {
    @Query("SELECT q FROM Questionnaire q WHERE q.year = ?1 AND q.journal.id = ?2")
    List<Questionnaire> findByYearAndJournalId(Integer year, Long journalId);

    List<Questionnaire> findByYear(Integer year);

    @Query("SELECT DISTINCT q.year FROM Questionnaire q WHERE q.year IS NOT NULL ORDER BY q.year DESC")
    List<Integer> findDistinctYears();
}
