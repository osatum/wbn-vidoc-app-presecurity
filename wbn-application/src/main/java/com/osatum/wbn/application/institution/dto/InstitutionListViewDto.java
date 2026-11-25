package com.osatum.wbn.application.institution.dto;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class InstitutionListViewDto {
    private Long id;
    private String name;
    private String description;
    private String contactData;
    private InstitutionType institutionType;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime editedAt;
    private String editedBy;
    private EntityState state;
}
