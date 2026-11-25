package com.osatum.wbn.application.shared.exception;

public class InstitutionDeletionNotAllowedException extends RuntimeException {

    public InstitutionDeletionNotAllowedException(String message) {
        super(message);
    }
}
