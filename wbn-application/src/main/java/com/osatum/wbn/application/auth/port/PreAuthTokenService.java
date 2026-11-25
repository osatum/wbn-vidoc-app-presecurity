package com.osatum.wbn.application.auth.port;

public interface PreAuthTokenService {

    String createPreAuthToken(Long userId, Long institutionId);

    long requireUserIdFromPreAuthToken(String token);
}
