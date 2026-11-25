package com.osatum.wbn.web.user.management.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserBasicDataForm {
    @NotBlank
    @Size(max = 128)
    private String firstName;

    @NotBlank
    @Size(max = 128)
    private String lastName;

    @Size(max = 19)
    @Pattern(regexp = "^\\s*$|\\d{4}-\\d{4}-\\d{4}-\\d{3}[0-9Xx]$")
    private String orcid;
}
