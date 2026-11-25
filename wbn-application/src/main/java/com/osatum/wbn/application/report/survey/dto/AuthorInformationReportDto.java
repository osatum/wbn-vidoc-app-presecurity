package com.osatum.wbn.application.report.survey.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthorInformationReportDto {
    private String journalId;
    private String instructionLink;
    private String articleStructureInstruction;
    private String literatureInstruction;
    private String originalKindStatement;
    private String authorContributionStatement;
    private String financeSourceStatement;
    private String orcidRequired;
    private String citationsStyle;
    private String manuscriptProcessingTimeUrl;
    private String avgInitialAssessment;
    private String avgReview;
    private String avgPublication;
    private String manuscriptProcessingCostUrl;
    private String submissionFeeType;
    private String publicationFeeType;
    private String openAccessFeeType;
    private String apcFeeType;
    private String rapidReviewFeeType;
}
