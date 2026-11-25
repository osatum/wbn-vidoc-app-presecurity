package com.osatum.wbn.application.institution.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionsDetectionDto {
    private String clientIp;
    private List<InstitutionAccessDto> institutions;
    private boolean globalLoginAvailable;
    private boolean institutionsDetected;
}
