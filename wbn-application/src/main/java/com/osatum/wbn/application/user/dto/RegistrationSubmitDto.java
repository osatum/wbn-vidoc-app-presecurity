package com.osatum.wbn.application.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationSubmitDto {
    private Long institutionId;
    private String email;
    private String firstName;
    private String lastName;
    private String orcid;
    private String password;
    private boolean termsAndPrivacyPolicyAccepted;
    private boolean personalDataProcessingAcknowledged;
}
