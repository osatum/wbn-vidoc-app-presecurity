package com.osatum.wbn.application.report.survey.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SurveyReportDto {
    private List<JournalReportDto> journals;
    private List<ScientificProfileReportDto> scientificProfiles;
    private List<EditorialAddressReportDto> editorialAddresses;
    private List<EditorialBoardReportDto> editorialBoards;
    private List<PublisherReportDto> publishers;
    private List<PublishingVersionsReportDto> publishingVersions;
    private List<PublishingStabilityReportDto> publishingStabilities;
    private List<PublishingStabilityLanguagesReportDto> publishingStabilitiesLanguages;
    private List<ReviewStandardsReportDto> reviewStandards;
    private List<AuthorInformationReportDto> authorInformation;
    private List<DigitalizationReportDto> digitalization;
    private List<ScientificDatabaseReportDto> scientificDatabases;
    private List<CooperationReportDto> cooperation;
    private List<CooperationReviewerReportDto> cooperationReviewers;
    private List<CooperationScientificCouncilReportDto> cooperationScientificCouncil;
}
