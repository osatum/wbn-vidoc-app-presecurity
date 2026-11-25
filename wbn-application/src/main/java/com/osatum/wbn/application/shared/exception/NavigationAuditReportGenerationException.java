package com.osatum.wbn.application.shared.exception;

public class NavigationAuditReportGenerationException extends RuntimeException {
    public NavigationAuditReportGenerationException(Throwable cause) {
        super("Could not generate the activity report.", cause);
    }
}
