package com.osatum.wbn.infrastructure.security;

import com.osatum.wbn.application.auth.service.LoginSessionService;
import com.osatum.wbn.infrastructure.security.principal.InstitutionPrincipal;
import com.osatum.wbn.infrastructure.security.principal.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginSessionActivityFilter extends OncePerRequestFilter {

    private final LoginSessionService loginSessionService;

    @Value("${app.security.sessions.last-seen-throttle-seconds:60}")
    private long lastSeenThrottleSeconds;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        touchSessionIfAuthenticated(request);
        filterChain.doFilter(request, response);
    }

    private void touchSessionIfAuthenticated(HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/auth/")) {
            return;
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        Long loginSessionId = switch (authentication.getPrincipal()) {
            case UserPrincipal userPrincipal -> userPrincipal.getLoginSessionId();
            case InstitutionPrincipal institutionPrincipal -> institutionPrincipal.loginSessionId();
            default -> null;
        };

        if (loginSessionId == null) {
            return;
        }

        loginSessionService.touchIfDue(
                loginSessionId,
                Duration.ofSeconds(Math.max(1, lastSeenThrottleSeconds))
        );
    }
}
