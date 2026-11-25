package com.osatum.wbn.web.user.management.mapper;

import com.osatum.wbn.application.user.dto.UserCreateDto;
import com.osatum.wbn.web.user.management.form.UserCreateForm;
import org.springframework.stereotype.Component;

@Component
public class UserCreateFormMapper {
    public UserCreateDto toDto(Long institutionId, UserCreateForm form) {
        UserCreateDto dto = new UserCreateDto();
        dto.setInstitutionId(institutionId);
        dto.setEmail(form.getEmail());
        dto.setFirstName(form.getFirstName());
        dto.setLastName(form.getLastName());
        dto.setOrcid(form.getOrcid());
        dto.setPassword(form.getPassword());
        dto.setRole(form.getRole());
        dto.setRoleOptions(form.getRoleOptions());
        dto.setAttachExistingUser(form.isAttachExistingUser());
        return dto;
    }

    public UserCreateForm toForm(UserCreateDto dto) {
        UserCreateForm form = new UserCreateForm();
        form.setEmail(dto.getEmail());
        form.setFirstName(dto.getFirstName());
        form.setLastName(dto.getLastName());
        form.setOrcid(dto.getOrcid());
        form.setPassword(dto.getPassword());
        form.setRole(dto.getRole());
        form.setRoleOptions(dto.getRoleOptions());
        form.setAttachExistingUser(dto.isAttachExistingUser());
        return form;
    }
}
