package com.osatum.wbn.application.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginSessionExpirationJob {

    private final LoginSessionService loginSessionService;

    /**
     * Cyklicznie oznacza wygasłe aktywne sesje jako zakończone.
     */
    @Scheduled(fixedDelayString = "${app.security.sessions.expiration-scan-delay-ms:60000}")
    public void markExpiredSessions() {
        loginSessionService.markExpiredSessions();
    }
}
