package com.osatum.wbn.domain.model.survey.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Media społecznościowe ({@code survey.socialMedias.*}).
 */
@Data
public class SocialMedias implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Czy używane są media społecznościowe ({@code survey.socialMedias.used}).  * <p><b>translate:</b> SURVEY_CREATE.NO, SURVEY_CREATE.YES_CHOOSE</p>
 */
    private Boolean used;

    /** Identyfikatory wybranych kanałów ({@code survey.socialMedias.ids}). */
    private List<SocialMedia> ids = new ArrayList<>();

    /** Inne kanały – opis tekstowy ({@code survey.socialMedias.other}). */
    private String other;
}
