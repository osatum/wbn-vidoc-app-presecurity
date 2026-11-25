package com.osatum.wbn.web.user.management.mapper;

import com.osatum.wbn.application.user.dto.UserPasswordDto;
import com.osatum.wbn.web.user.management.form.UserPasswordForm;
import org.springframework.stereotype.Component;

@Component
public class UserPasswordFormMapper {
    public UserPasswordDto toDto(UserPasswordForm form) {
        UserPasswordDto dto = new UserPasswordDto();
        dto.setPassword(form.getPassword());
        dto.setPasswordRepeat(form.getPasswordRepeat());
        return dto;
    }
}
