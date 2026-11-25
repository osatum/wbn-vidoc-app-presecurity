package com.osatum.wbn.application.institution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstitutionEmailDomainCreateDto {
    @NotBlank
    @Size(max = 255)
    private String domain;
}
