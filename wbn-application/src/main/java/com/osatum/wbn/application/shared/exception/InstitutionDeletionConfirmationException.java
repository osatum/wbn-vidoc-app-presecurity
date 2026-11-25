package com.osatum.wbn.application.shared.exception;

public class InstitutionDeletionConfirmationException extends RuntimeException {

    private final Reason reason;

    public InstitutionDeletionConfirmationException(Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        INVALID("Institution deletion confirmation is invalid."),
        EXPIRED("Institution deletion confirmation has expired."),
        USED("Institution deletion confirmation was already used.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }
}
