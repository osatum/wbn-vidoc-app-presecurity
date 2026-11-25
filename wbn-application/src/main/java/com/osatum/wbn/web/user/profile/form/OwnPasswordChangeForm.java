package com.osatum.wbn.web.user.profile.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OwnPasswordChangeForm {
    @NotBlank
    @Size(min = 8, max = 128)
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @NotBlank
    @Size(min = 8, max = 128)
    private String passwordRepeat;
}
