package com.osatum.wbn.application.report.survey.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScientificProfileReportDto {
    private String journalId;
    private String mainScientificProfile;
    private String otherScientificProfiles;
    private String mainTrCategory;
    private String otherTrCategories;
    private String mainAsjcCategory;
    private String otherAsjcCategories;
}
