package com.osatum.wbn.application.institution.mapper;

import com.osatum.wbn.application.institution.dto.InstitutionMembershipListDto;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InstitutionMembershipMapper {
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "firstName", source = "user.firstName")
    @Mapping(target = "lastName", source = "user.lastName")
    @Mapping(target = "orcid", source = "user.orcid")
    @Mapping(target = "userState", source = "user.state")
    InstitutionMembershipListDto toListDto(InstitutionMembership membership);
}
