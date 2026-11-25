package com.osatum.wbn.web.user.datadeletion.mapper;

import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.user.dto.UserDataDeletionRequestDto;
import com.osatum.wbn.web.user.datadeletion.form.UserDataDeletionRequestForm;
import org.springframework.stereotype.Component;

@Component
public class UserDataDeletionRequestFormMapper {
    public UserDataDeletionRequestDto toDto(UserDataDeletionRequestForm form, UserActor actor) {
        UserDataDeletionRequestDto dto = new UserDataDeletionRequestDto();
        dto.setEmail(form.getEmail());
        dto.setUserId(actor.userId());
        dto.setInstitutionId(actor.institutionId());
        dto.setInstitutionType(actor.institutionType());
        return dto;
    }
}
