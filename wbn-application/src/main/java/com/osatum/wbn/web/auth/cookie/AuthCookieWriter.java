package com.osatum.wbn.web.auth.cookie;

import com.osatum.wbn.application.auth.dto.AuthResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieWriter {

    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String PRE_AUTH = "PRE_AUTH";

    private static final String AUTH_PATH = "/api/auth";
    private static final Duration PRE_AUTH_TTL = Duration.ofMinutes(5);

    private final boolean secure;
    private final String sameSite;
    private final Duration refreshTtl;

    public AuthCookieWriter(
            @Value("${app.security.cookie.secure:false}") boolean secure,
            @Value("${app.security.cookie.same-site:Lax}") String sameSite,
            @Value("${app.security.jwt.refresh-ttl-days}") long refreshTtlDays
    ) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
    }

    public AuthResult write(AuthResult result, HttpServletResponse response) {
        if (result.refreshToken() != null) {
            addCookie(response, REFRESH_TOKEN, result.refreshToken(), refreshTtl);
            clearCookie(response, PRE_AUTH);
        }
        if (result.preAuthToken() != null) {
            addCookie(response, PRE_AUTH, result.preAuthToken(), PRE_AUTH_TTL);
        }
        return result;
    }

    public void clearAuthenticationCookies(HttpServletResponse response) {
        clearCookie(response, REFRESH_TOKEN);
        clearCookie(response, PRE_AUTH);
    }

    private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        var cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(AUTH_PATH)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        addCookie(response, name, "", Duration.ZERO);
    }
}
