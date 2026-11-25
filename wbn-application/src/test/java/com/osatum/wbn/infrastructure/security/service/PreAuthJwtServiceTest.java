package com.osatum.wbn.infrastructure.security.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreAuthJwtServiceTest {

    private static final String SECRET = "test-pre-auth-secret-with-at-least-32-characters";

    @Test
    void createsAndValidatesPreAuthToken() {
        var service = new PreAuthJwtService(SECRET, 5);

        String token = service.createPreAuthToken(42L, 7L);

        assertThat(service.requireUserIdFromPreAuthToken(token)).isEqualTo(42L);
    }

    @Test
    void rejectsTooShortSecret() {
        assertThatThrownBy(() -> new PreAuthJwtService("short", 5))
                .isInstanceOf(IllegalStateException.class);
    }
}
