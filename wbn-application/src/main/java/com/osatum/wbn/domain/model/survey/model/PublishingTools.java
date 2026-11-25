package com.osatum.wbn.domain.model.survey.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Narzędzia do publikowania ({@code survey.publishingTools.*}).
 */
@Data
public class PublishingTools implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Czy używane są narzędzia publikowania ({@code survey.publishingTools.used}).  * <p><b>translate:</b> SURVEY_CREATE.LABEL_PUBLISHING_TOOLS_TOOLTIP, SURVEY_CREATE.NO, SURVEY_CREATE.YES_CHOOSE</p>
 */
    private Boolean used;

    /** Identyfikatory wybranych narzędzi ({@code survey.publishingTools.ids}). */
    private List<String> ids = new ArrayList<>();

    /** Inne narzędzia – opis tekstowy ({@code survey.publishingTools.other}). */
    private String other;
}
