package com.osatum.wbn.web.user.management.mapper;

import com.osatum.wbn.application.user.dto.UserBasicDataDto;
import com.osatum.wbn.web.user.management.form.UserBasicDataForm;
import org.springframework.stereotype.Component;

@Component
public class UserBasicDataFormMapper {
    public UserBasicDataDto toDto(UserBasicDataForm form) {
        UserBasicDataDto dto = new UserBasicDataDto();
        dto.setFirstName(form.getFirstName());
        dto.setLastName(form.getLastName());
        dto.setOrcid(form.getOrcid());
        return dto;
    }

    public UserBasicDataForm toForm(UserBasicDataDto dto) {
        UserBasicDataForm form = new UserBasicDataForm();
        form.setFirstName(dto.getFirstName());
        form.setLastName(dto.getLastName());
        form.setOrcid(dto.getOrcid());
        return form;
    }
}
