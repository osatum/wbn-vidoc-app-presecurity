package com.osatum.wbn.application.questionnaire.dto;


import com.osatum.wbn.application.questionnaire.dto.model.Issn;
import com.osatum.wbn.application.questionnaire.dto.model.JournalTitle;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Sekcja 1: Czasopismo ({@code SURVEY_CREATE.HEADING_SECTION_1}).
 * <p>
 * Formularz pozwala uzupełnić:
 * <ul>
 *   <li>listę ISSN ({@code SURVEY_CREATE.T_ISSNS_TITLE}, tabela: {@code SURVEY_CREATE.T_ISSNS_ISSN}, {@code SURVEY_CREATE.T_ISSNS_TYPE}, ...),</li>
 *   <li>listę tytułów czasopisma ({@code SURVEY_CREATE.T_TITLES_ORIGINAL}, {@code SURVEY_CREATE.T_TITLES_ENGLISH}, {@code SURVEY_CREATE.T_TITLES_SHORT}, {@code SURVEY_CREATE.T_TITLES_CITATION}).</li>
 * </ul>
 */
@Data
/**
 * JournalSection
 * <p><b>Klucze translate użyte w tej sekcji (HTML):</b> SURVEY_CREATE.ADD_ISSN; SURVEY_CREATE.ADD_TITLE; SURVEY_CREATE.DELETE; SURVEY_CREATE.EDIT; SURVEY_CREATE.ISSN_TOOLTIP; SURVEY_CREATE.NO; SURVEY_CREATE.T_ISSNS_ACTUAL; SURVEY_CREATE.T_ISSNS_ISSN; SURVEY_CREATE.T_ISSNS_TITLE; SURVEY_CREATE.T_ISSNS_TYPE; SURVEY_CREATE.T_ISSNS_YEARFROM; SURVEY_CREATE.T_ISSNS_YEARTO; SURVEY_CREATE.T_TITLES_ACTUAL; SURVEY_CREATE.T_TITLES_ENGLISH; SURVEY_CREATE.T_TITLES_ORIGINAL; SURVEY_CREATE.T_TITLES_SHORT; SURVEY_CREATE.T_TITLES_TITLE; SURVEY_CREATE.T_TITLES_YEARFROM; SURVEY_CREATE.T_TITLES_YEARTO; SURVEY_CREATE.YES</p>
 */
public class JournalDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Numery ISSN przypisane do czasopisma.
     * <p>
     * Źródło: {@code SurveyData.issns}.
     * W formularzu dodawane/edyowane w tabeli sekcji 1 (przycisk {@code SURVEY_CREATE.ADD_ISSN}).
     * </p>
     */
    private List<Issn> issns = new ArrayList<>();

    /**
     * Tytuły czasopisma (różne typy tytułów).
     * <p>
     * Źródło: {@code SurveyData.titles}.
     * W formularzu dodawane/edyowane w tabeli sekcji 1 (przycisk {@code SURVEY_CREATE.ADD_TITLE}).
     * </p>
     */
    private List<JournalTitle> titles = new ArrayList<>();
}
