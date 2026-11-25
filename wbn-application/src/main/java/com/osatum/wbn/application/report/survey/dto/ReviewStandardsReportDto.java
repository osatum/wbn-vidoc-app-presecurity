package com.osatum.wbn.application.report.survey.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewStandardsReportDto {
    private String journalId;
    private String linkToReviewProcedure;
    private String infoReviewProcedure;
    private String publishedReviewersList;
    private String min2Reviewers;
    private String publicForm;
    private String publicCriteria;
    private String reviewerStatement;
    private String prepareReview;
}
