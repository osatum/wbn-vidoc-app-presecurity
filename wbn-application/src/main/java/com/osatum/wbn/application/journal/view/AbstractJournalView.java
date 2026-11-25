package com.osatum.wbn.application.journal.view;

import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.questionnaire.RatingIcvStatus;
import com.osatum.wbn.domain.model.questionnaire.ReasonNotIndexing;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AbstractJournalView {
    private Long id;
    private String titleOriginal;
    private String titleEnglish;
    private String titleShort;
    private String doi;
    private CountryView country;
    private StudyStatus studyStatus;
    private String pissn;
    private String eissn;
    private List<LanguageView> languages;

    private Integer currentIcvYear;
    private Double currentIcvPoints;
    private RatingStatus currentStatus;
    private ReasonNotIndexing currentReasonNotIndexing;
    private Boolean currentReasonNotIndexingFlag;
}
