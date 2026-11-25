package com.osatum.wbn.application.institution.assembler.simple;


import com.osatum.wbn.application.institution.dto.InstitutionAccessDto;
import com.osatum.wbn.application.institution.dto.InstitutionsDetectionDto;
import com.osatum.wbn.shared.assembler.AssemblerDto;

import java.util.List;

public class InstitutionsDetectionDtoAssembler implements AssemblerDto<List<InstitutionAccessDto>, InstitutionsDetectionDto, InstitutionsDetectionDtoParameters> {
    @Override
    public InstitutionsDetectionDto toDto(InstitutionsDetectionDtoParameters parameters) {
        InstitutionsDetectionDto dto = parameters.getDto() == null ? new InstitutionsDetectionDto() : parameters.getDto();
        dto.setInstitutions(parameters.getEntity());
        dto.setInstitutionsDetected(parameters.getEntity() != null && !parameters.getEntity().isEmpty());
        return dto;
    }
}
