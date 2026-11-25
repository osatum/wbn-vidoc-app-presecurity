package com.osatum.wbn.application.institution.assembler.simple;


import com.osatum.wbn.application.institution.dto.InstitutionAccessDto;
import com.osatum.wbn.application.institution.dto.InstitutionsDetectionDto;
import com.osatum.wbn.shared.assembler.DefaultParameters;
import lombok.Getter;

import java.util.List;

@Getter
public class InstitutionsDetectionDtoParameters extends DefaultParameters<List<InstitutionAccessDto>, InstitutionsDetectionDto> {
    public InstitutionsDetectionDtoParameters(List<InstitutionAccessDto> entity, String clientIp) {
        super(entity, null);
        this.clientIp = clientIp;
    }

    private String clientIp;
}
