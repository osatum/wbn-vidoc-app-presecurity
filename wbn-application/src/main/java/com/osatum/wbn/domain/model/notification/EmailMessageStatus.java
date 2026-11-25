package com.osatum.wbn.domain.model.notification;

public enum EmailMessageStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    RETRYING,
    CANCELLED
}
