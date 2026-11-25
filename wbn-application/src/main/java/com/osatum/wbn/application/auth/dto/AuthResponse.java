package com.osatum.wbn.application.auth.dto;

import com.osatum.wbn.application.institution.dto.InstitutionAccessDto;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class AuthResponse {


    private final String accessToken;
    private final AuthStatus status;
    private final InstitutionType institutionType;
    private final InstitutionRole institutionRole;
    private final List<InstitutionAccessDto> institutions;
}
