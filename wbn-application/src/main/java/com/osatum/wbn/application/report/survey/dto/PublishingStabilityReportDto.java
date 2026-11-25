package com.osatum.wbn.application.report.survey.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublishingStabilityReportDto {
    private String journalId;
    private String articlePublished1Amount;
    private String articlePublished1ScientificAmount;
    private String articlePublished1ReceivedManuscriptCount;
    private String articlePublished1RejectedManuscriptCount;
    private String articlePublished1PublishedIssuesAmount;
    private String articlePublished1FrequencyCode;
    private String articlePublished2Amount;
    private String articlePublished2ScientificAmount;
    private String articlePublished2ReceivedManuscriptCount;
    private String articlePublished2RejectedManuscriptCount;
    private String articlePublished2PublishedIssuesAmount;
    private String articlePublished2FrequencyCode;
    private String articlePublished3Amount;
    private String articlePublished3ScientificAmount;
    private String articlePublished3ReceivedManuscriptCount;
    private String articlePublished3RejectedManuscriptCount;
    private String articlePublished3PublishedIssuesAmount;
    private String articlePublished3FrequencyCode;
    private String articlePublished4Amount;
    private String articlePublished4ScientificAmount;
    private String articlePublished4ReceivedManuscriptCount;
    private String articlePublished4RejectedManuscriptCount;
    private String articlePublished4PublishedIssuesAmount;
    private String articlePublished4FrequencyCode;
/*
    private String articleLanguage1PreviousValue;
    private String articleLanguage2PreviousValue;

 */
    private String nd;
    private String concatenateNumberPrevious;
    private String publishingBreakPrevious;
    /*
    private String articleLanguage1CurrentValue;
    private String articleLanguage2CurrentValue;

     */
    private Double englishTitleAndAbstractInAllArticlesPercentage;
    private String concatenateNumber;
    private String publishingBreak;
}
