package com.osatum.wbn.web.user.passwordreset.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetRequestForm {
    @NotBlank
    @Email
    @Size(max = 128)
    private String email;
}
