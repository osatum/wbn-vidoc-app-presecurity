package com.osatum.wbn.application.questionnaire.dto.model;


import com.osatum.jml.migration.dto.questionnaire.survey.model.JournalIndicator;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * Wskaźniki jakości naukowej, wprowadzane w sekcji 6.
 * <p>
 * Formularz zawiera pola "poprzedni" i "bieżący" dla wskaźników (np. IF, SJR).
 * </p>
 */
@Data
public class ScientificQuality implements Serializable {

    private static final long serialVersionUID = 1L;

    //Impact Factor:
    private JournalIndicator indicatorIF;
    //Scimago Journal Rank (SJR):
    private JournalIndicator indicatorSJR;
    // Total Cites:
    private JournalIndicator indicatorTC;
    //Source Normalized Impact per Paper (SNIP):
    private JournalIndicator indicatorSNIP;
    //CiteScore:
    private JournalIndicator indicatorCS;
    // Article Influence:
    private JournalIndicator indicatorAI;
    // Category Normalized Citation Impact:
    private JournalIndicator indicatorCNCI;
    // Journal Citation Indicator:
    @Schema(nullable = true)
    private JournalIndicator indicatorJCI;


}
