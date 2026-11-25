package com.osatum.wbn.application.user.mapper;

import com.osatum.wbn.application.user.dto.UserBasicDataDto;
import com.osatum.wbn.application.user.dto.UserCreateDto;
import com.osatum.wbn.application.user.dto.UserCreateResultDto;
import com.osatum.wbn.application.user.dto.UserCreateStatus;
import com.osatum.wbn.application.user.dto.UserListViewDto;
import com.osatum.wbn.application.user.dto.UserMembershipDto;
import com.osatum.wbn.application.user.dto.UserStateChangeResultDto;
import com.osatum.wbn.application.user.dto.UserStateChangeStatus;
import com.osatum.wbn.application.user.dto.UserViewDto;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.user.AppUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.BeanMapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserListViewDto listViewDto(AppUser appUser);
    UserViewDto viewDto(AppUser appUser);
    UserBasicDataDto basicDataDto(AppUser appUser);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.OffsetDateTime.now())")
    @Mapping(target = "editedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "editedBy", ignore = true)
    @Mapping(target = "state", expression = "java(com.osatum.wbn.domain.model.EntityState.ACTIVE)")
    @Mapping(target = "email", source = "normalizedEmail")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "firstName", expression = "java(normalize(dto.getFirstName()))")
    @Mapping(target = "lastName", expression = "java(normalize(dto.getLastName()))")
    @Mapping(target = "orcid", expression = "java(dto.getOrcid() == null || dto.getOrcid().isBlank() ? null : dto.getOrcid().trim().toUpperCase(java.util.Locale.ROOT))")
    @Mapping(target = "passwordHash", source = "passwordHash")
    @Mapping(target = "roles", source = "applicationRoles")
    @Mapping(target = "memberships", ignore = true)
    AppUser createEntity(UserCreateDto dto, String normalizedEmail, String passwordHash, String applicationRoles);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.OffsetDateTime.now())")
    @Mapping(target = "editedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "editedBy", ignore = true)
    @Mapping(target = "state", expression = "java(com.osatum.wbn.domain.model.EntityState.ACTIVE)")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "institution", source = "institution")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "loginAllowedFromAnyIp", expression = "java(true)")
    @Mapping(target = "activatedAt", ignore = true)
    @Mapping(target = "activatedBy", ignore = true)
    InstitutionMembership createMembership(AppUser user, Institution institution, InstitutionRole role);

    @Mapping(target = "institutionName", source = "institution.name")
    @Mapping(target = "institutionId", source = "institution.id")
    @Mapping(target = "institutionType", source = "institution.institutionType")
    UserMembershipDto membershipDto(InstitutionMembership membership);

    @Mapping(target = "firstName", expression = "java(normalize(dto.getFirstName()))")
    @Mapping(target = "lastName", expression = "java(normalize(dto.getLastName()))")
    @Mapping(target = "orcid", expression = "java(dto.getOrcid() == null || dto.getOrcid().isBlank() ? null : dto.getOrcid().trim().toUpperCase(java.util.Locale.ROOT))")
    void updateBasicData(UserBasicDataDto dto, @MappingTarget AppUser user);

    default UserCreateResultDto createResult(UserCreateStatus status, AppUser user, InstitutionMembership membership) {
        UserCreateResultDto result = new UserCreateResultDto();
        result.setStatus(status);
        result.setUser(viewDto(user));
        result.setMembership(membership == null ? null : membershipDto(membership));
        return result;
    }

    default UserStateChangeResultDto stateChangeResult(
            UserStateChangeStatus status,
            AppUser user,
            List<InstitutionMembership> memberships
    ) {
        UserStateChangeResultDto result = new UserStateChangeResultDto();
        result.setStatus(status);
        result.setUser(user == null ? null : viewDto(user));
        result.setMemberships(memberships == null ? List.of() : memberships.stream().map(this::membershipDto).toList());
        return result;
    }

    default String normalize(String value) {
        return value == null ? null : value.trim();
    }

}
