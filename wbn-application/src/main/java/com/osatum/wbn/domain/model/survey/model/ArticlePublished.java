package com.osatum.wbn.domain.model.survey.model;

import com.osatum.wbn.domain.model.journal.JournalFrequency;
import lombok.Data;

import java.io.Serializable;

/**
 * Dane roczne o opublikowanych artykułach ({@code survey.articlePublishedSurveyList}).
 * <p><b>translate:</b> SURVEY_CREATE.EDIT, SURVEY_CREATE.T_FREQUENCY, SURVEY_CREATE.T_PUBLISHED_ISSUE_AMOUNT, SURVEY_CREATE.T_REVIEWED_AMOUNT</p>
 */
@Data
public class ArticlePublished implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Rok.
     */
    private Integer year;

    /**
     * Łączna liczba opublikowanych artykułów.
     */
    private Integer amount;

    /**
     * Liczba artykułów naukowych.
     */
    private Integer scientificAmount;

    /**
     * Liczba artykułów recenzowanych.
     */
    private Integer reviewedAmount;

    /**
     * Liczba otrzymanych manuskryptów (jeśli zbierane).
     */
    private Integer receivedManuscriptCount;

    /**
     * Liczba odrzuconych manuskryptów (jeśli zbierane).
     */
    private Integer rejectedManuscriptCount;

    /**
     * Liczba wydanych zeszytów/numerów w roku.
     */
    private Integer publishedIssuesAmount;

    /**
     * Częstotliwość wydawania w danym roku (ID słownika).
     */
    private JournalFrequency frequencyCode;
}
