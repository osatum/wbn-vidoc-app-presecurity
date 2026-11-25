package com.osatum.wbn.application.report.survey.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JournalReportDto {
    /* Identyfikator czasopisma */
    private String journalId;
    /* p-ISSN*/
    private String pissn;
    /* e-ISSN*/
    private String eissn;
    /* Tytuł oryginalny*/
    private String titleOrginal;
    /* Tytuł w języku angielskim*/
    private String titleEnglish;
    /* Tytuł skrócony*/
    private String titleShort;
    /* Strona www */
    private String website;
    /* Kraj pochodzenia czasopisma */
    private String country;
    /* Charakter czasopisma */
    private String kind;
    /* Wersja drukowana */
    private String printVersion;
    /* Wersja elektroniczna */
    private String electronicVersion;
    /* Dostęp do pełnych treści */
    private String paidArticles;
}
