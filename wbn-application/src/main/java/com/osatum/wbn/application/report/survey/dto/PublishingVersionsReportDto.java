package com.osatum.wbn.application.report.survey.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublishingVersionsReportDto {
    private String journalId;
    private String electronicVersion;
    private String articlesFormats;
    private String electronicVersionWww;
    private String electronicVersionInformation;
    private String electronicArticleMetadata;
    private String aop;
    private String printedVersion;
    private String coverInformation;
    private String circulation;
    private String printedArticleMetadata;
    private String publishingPolicy;
    private String electronicVersionsPercentage;
}
