package com.osatum.wbn.web.user.management.form;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserCreateForm {
    @NotBlank
    @Email
    @Size(max = 128)
    private String email;

    @NotBlank
    @Size(max = 128)
    private String firstName;

    @NotBlank
    @Size(max = 128)
    private String lastName;

    @Size(max = 19)
    @Pattern(regexp = "^\\s*$|\\d{4}-\\d{4}-\\d{4}-\\d{3}[0-9Xx]$")
    private String orcid;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @NotNull
    private InstitutionRole role;

    private List<InstitutionRole> roleOptions;

    private boolean attachExistingUser;
}
