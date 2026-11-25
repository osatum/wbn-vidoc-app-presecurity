package com.osatum.wbn.application.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetConfirmDto {
    private String password;
    private String passwordRepeat;
}
