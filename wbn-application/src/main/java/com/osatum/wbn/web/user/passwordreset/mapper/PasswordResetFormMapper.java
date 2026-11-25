package com.osatum.wbn.web.user.passwordreset.mapper;

import com.osatum.wbn.application.user.dto.PasswordResetConfirmDto;
import com.osatum.wbn.application.user.dto.PasswordResetRequestDto;
import com.osatum.wbn.web.user.passwordreset.form.PasswordResetConfirmForm;
import com.osatum.wbn.web.user.passwordreset.form.PasswordResetRequestForm;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetFormMapper {
    public PasswordResetRequestDto toDto(PasswordResetRequestForm form) {
        PasswordResetRequestDto dto = new PasswordResetRequestDto();
        dto.setEmail(form.getEmail());
        return dto;
    }

    public PasswordResetConfirmDto toDto(PasswordResetConfirmForm form) {
        PasswordResetConfirmDto dto = new PasswordResetConfirmDto();
        dto.setPassword(form.getPassword());
        dto.setPasswordRepeat(form.getPasswordRepeat());
        return dto;
    }
}
