package com.osatum.wbn.application.institution.mapper;


import com.osatum.wbn.application.institution.dto.InstitutionAccessDto;
import com.osatum.wbn.domain.model.institution.Institution;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(componentModel = "spring")
public interface InstitutionAccessDtoMapper {
    @Mappings({
            @Mapping(target = "memberships", ignore = true),
            @Mapping(target = "hasUsers", ignore = true),
            @Mapping(target = "requiresAuth",
                    expression = "java(institution.getAuthMode() == com.osatum.wbn.domain.model.institution.AuthMode.AUTH_REQUIRED)")
    })
    InstitutionAccessDto toDto(Institution institution);
}

