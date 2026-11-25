package com.osatum.wbn.application.journal.view;


import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.questionnaire.RatingIcvStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class JournalListView extends AbstractJournalView {

    private String title;
    private JournalFeesView fees;
    private Integer publicationDays;
    private String publisher;

    private List<String> publishers;
    private List<String> editorialOffices;

    private String cover;

    private Integer previousIcvYear;
    private Double previousIcvPoints;
    private RatingStatus previousStatus;

    private Integer lastMniswYear;
    private Double lastMniswPoints;
    private String lastMniswList;

    private Integer numberOfPublications;
    private Integer percentFullText;
    private Integer percentAbstracts;
    private Integer percentKeywords;
    private Integer percentReferences;

}
