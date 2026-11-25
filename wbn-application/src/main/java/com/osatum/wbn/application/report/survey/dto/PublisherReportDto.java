package com.osatum.wbn.application.report.survey.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublisherReportDto {
    private String journalId;
    private String publisher;
    private String address;
    private String phone;
    private String email;
}
