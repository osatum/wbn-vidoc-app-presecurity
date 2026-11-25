package com.osatum.wbn.domain.model.survey;




import com.osatum.wbn.domain.model.survey.model.JournalInstitution;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Sekcja 9: Wydawca ({@code SURVEY_CREATE.HEADING_SECTION_9}).
 * <p>
 * W formularzu prezentowana jako tabela instytucji ({@code SURVEY_CREATE.T_PUBLISHERS_HEADER}).
 */
@Data
/**
 * PublisherSection
 * <p><b>Klucze translate użyte w tej sekcji (HTML):</b> SURVEY_CREATE.ADD; SURVEY_CREATE.DELETE; SURVEY_CREATE.EDIT; SURVEY_CREATE.T_PUBLISHERS_COUNTRY; SURVEY_CREATE.T_PUBLISHERS_DEPARTMENT; SURVEY_CREATE.T_PUBLISHERS_EMAIL; SURVEY_CREATE.T_PUBLISHERS_HEADER; SURVEY_CREATE.T_PUBLISHERS_HEADER_TOOLTIP; SURVEY_CREATE.T_PUBLISHERS_NAME; SURVEY_CREATE.T_PUBLISHERS_TELEPHONE</p>
 */
public class Publisher implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Lista instytucji wydawcy ({@code msg.institutions}).
     * <p>Źródło: {@code SurveyData.institutions} filtrowane po roli 'P'.</p>
     */
    private List<JournalInstitution> publisherInstitutions = new ArrayList<>();
}
