package com.osatum.wbn.application.user.port;

import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequest;
import com.osatum.wbn.domain.model.registration.ActivationToken;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequest;

public interface UserNotificationPort {

    EmailMessageLog queueActivationMessage(
            EmailType emailType,
            AppUser user,
            Institution institution,
            InstitutionMembership membership,
            ActivationToken activationToken,
            String rawActivationToken
    );

    EmailMessageLog queuePasswordResetMessage(
            AppUser user,
            PasswordResetRequest passwordResetRequest,
            String rawPasswordResetToken
    );

    EmailMessageLog queueUserDataDeletionConfirmationMessage(
            AppUser user,
            Institution institution,
            UserDataDeletionRequest userDataDeletionRequest,
            String rawUserDataDeletionToken
    );
}
