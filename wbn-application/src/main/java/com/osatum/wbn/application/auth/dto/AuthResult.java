package com.osatum.wbn.application.auth.dto;

public record AuthResult(
        AuthResponse response,
        String refreshToken,
        String preAuthToken
) {

    public static AuthResult withRefreshToken(AuthResponse response, String refreshToken) {
        return new AuthResult(response, refreshToken, null);
    }

    public static AuthResult withPreAuthToken(AuthResponse response, String preAuthToken) {
        return new AuthResult(response, null, preAuthToken);
    }
}
