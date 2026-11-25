package com.osatum.wbn.application.analytics.view;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AnalyticsJournalView {
    private Long journalId;

    /*

    private AnalyticsContainerView indicatorsIF;
    private AnalyticsContainerView indicatorsSJR;
    private AnalyticsContainerView indicatorsTC;
    private AnalyticsContainerView indicatorsSNIP;
    private AnalyticsContainerView indicatorsCS;
    private AnalyticsContainerView indicatorsAI;
    private AnalyticsContainerView indicatorsCNCI;
private AnalyticsContainerView articleAmount;
    */


    private AnalyticsContainerView articleScientificAmount;
    private AnalyticsContainerView articleManuscriptReceived;
    private AnalyticsContainerView authorUnique;
    private AnalyticsContainerView reviewerForeign;
    private List<AnalyticsCountryView> reviewersByCountry;
     /*
    private AnalyticsContainerView authorExternal;
    private AnalyticsContainerView authorForeign;

    private AnalyticsContainerView reviewerUnique;
    private AnalyticsContainerView reviewerExternal;


    private AnalyticsContainerView directorUnique;
    private AnalyticsContainerView directorExternal;
    private AnalyticsContainerView directorForeign;

     */
}
