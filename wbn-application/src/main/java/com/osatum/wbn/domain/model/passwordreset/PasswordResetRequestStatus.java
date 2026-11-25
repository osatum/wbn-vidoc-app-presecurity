package com.osatum.wbn.domain.model.passwordreset;

public enum PasswordResetRequestStatus {
    EMAIL_QUEUED,
    LINK_CLICKED,
    COMPLETED,
    EXPIRED,
    INVALIDATED,
    USER_NOT_FOUND,
    USER_NOT_ACTIVE,
    RATE_LIMITED,
    FAILED
}
