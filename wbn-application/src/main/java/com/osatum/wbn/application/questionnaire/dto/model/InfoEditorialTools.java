package com.osatum.wbn.application.questionnaire.dto.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Narzędzia redakcyjne i edytorskie ({@code survey.infoEditorialTools.*}).
 */
@Data
public class InfoEditorialTools implements Serializable {

    private static final long serialVersionUID = 1L;

    /** System antyplagiatowy ({@code survey.infoEditorialTools.antiPlagiarismSystem}). */
    private Boolean antiPlagiarismSystem = false;

    /** System redakcyjny ({@code survey.infoEditorialTools.editorialSystem}).  * <p><b>translate:</b> SURVEY_CREATE.LABEL_EDITORIAL_TOOLS_EDITORIAL_SYSTEM_TOOLTIP</p>
 */
    private Boolean editorialSystem = false;

    /** System obsługi manuskryptów ({@code survey.infoEditorialTools.manuscriptSystem}). */
    private Boolean manuscriptSystem = false;

    /** Narzędzia eksportu ({@code survey.infoEditorialTools.exportTools}). */
    private Boolean exportTools = false;

    /** Statystyki ({@code survey.infoEditorialTools.statistics}). */
    private Boolean statistics = false;

    /** Newslettery ({@code survey.infoEditorialTools.newsletters}). */
    private Boolean newsletters = false;

    /** Żadne z powyższych ({@code survey.infoEditorialTools.none}). */
    private Boolean none = false;
}
