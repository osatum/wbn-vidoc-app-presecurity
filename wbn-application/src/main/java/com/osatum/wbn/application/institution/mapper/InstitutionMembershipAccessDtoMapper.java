package com.osatum.wbn.application.institution.mapper;


import com.osatum.wbn.application.institution.dto.InstitutionMembershipAccessDto;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InstitutionMembershipAccessDtoMapper {
    InstitutionMembershipAccessDto toDto(InstitutionMembership membership);
}
