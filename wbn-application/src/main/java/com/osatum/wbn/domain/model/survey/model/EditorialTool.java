package com.osatum.wbn.domain.model.survey.model;

import java.io.Serializable;

/**
 * Narzędzia redakcyjne i edytorskie ({@code survey.infoEditorialTools.*}).
 *
 * Odpowiada checkboxom:
 * - survey.infoEditorialTools.antiPlagiarismSystem
 * - survey.infoEditorialTools.editorialSystem
 * - survey.infoEditorialTools.manuscriptSystem
 * - survey.infoEditorialTools.exportTools
 * - survey.infoEditorialTools.statistics
 * - survey.infoEditorialTools.newsletters
 * - survey.infoEditorialTools.none
 */
public enum EditorialTool implements Serializable {

    /** System antyplagiatowy ({@code survey.infoEditorialTools.antiPlagiarismSystem}) */
    ANTI_PLAGIARISM_SYSTEM,

    /** System redakcyjny ({@code survey.infoEditorialTools.editorialSystem}) */
    EDITORIAL_SYSTEM,

    /** System obsługi manuskryptów ({@code survey.infoEditorialTools.manuscriptSystem}) */
    MANUSCRIPT_SYSTEM,

    /** Narzędzia eksportu ({@code survey.infoEditorialTools.exportTools}) */
    EXPORT_TOOLS,

    /** Statystyki ({@code survey.infoEditorialTools.statistics}) */
    STATISTICS,

    /** Newslettery ({@code survey.infoEditorialTools.newsletters}) */
    NEWSLETTERS,

    /** Żadne z powyższych ({@code survey.infoEditorialTools.none}) */
    NONE
}
