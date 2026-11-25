package com.osatum.wbn.application.report.survey.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DigitalizationReportDto {
    private String journalId;
    private String website;
    private String websiteLanguages;
    private String doiPrefix;
    private String doiType;
    private String infoWebsiteTitles;
    private String infoWebsiteIssn;
    private String infoWebsiteDirectors;
    private String infoWebsiteEditOfficeInfo;
    private String articlePublishedLicense;
    private String systemForPublishedArticlesName;
    private String socialMediaIds;
    private String publishingToolIds;
    private String citationsTools;
    private String antiPlagiarismSystem;
    private String statistics;
    private String newsletters;
}
