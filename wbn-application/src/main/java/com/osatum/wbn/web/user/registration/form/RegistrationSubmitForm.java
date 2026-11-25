package com.osatum.wbn.web.user.registration.form;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationSubmitForm {
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

    @AssertTrue
    private boolean termsAndPrivacyPolicyAccepted;

    @AssertTrue
    private boolean personalDataProcessingAcknowledged;
}
