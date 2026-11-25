package com.osatum.wbn.application.questionnaire.dto.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Repozytoria zewnętrzne ({@code survey.externalRepositories.*}).
 */
@Data
public class ExternalRepositories implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Repozytoria ogólne (ID) – {@code survey.externalRepositories.general}.  * <p><b>translate:</b> SURVEY_CREATE.SELECT</p>
 */
    private List<Repository> general = new ArrayList<>();

    /** Inne repozytoria – tekstowo ({@code survey.externalRepositories.others}). */
    private List<String> others = new ArrayList<>();

    /** Bibliografie z ostatniego roku (ID) – {@code survey.externalRepositories.bibliographyLastYear}. */
    private List<Long> bibliographyLastYear = new ArrayList<>();

    /** Artykuły z ostatniego roku (ID) – {@code survey.externalRepositories.articlesLastYear}. */
    private List<Long> articlesLastYear = new ArrayList<>();
}
