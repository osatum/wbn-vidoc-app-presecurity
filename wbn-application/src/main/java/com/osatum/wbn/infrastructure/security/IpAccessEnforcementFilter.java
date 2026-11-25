package com.osatum.wbn.infrastructure.security;

import com.osatum.wbn.application.auth.service.LoginSessionService;
import com.osatum.wbn.application.shared.request.ClientRequestMetadataInput;
import com.osatum.wbn.application.shared.request.ClientRequestMetadataResolutionService;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.repository.auth.RefreshTokenRepository;
import com.osatum.wbn.domain.service.institution.InstitutionIpMatcherService;
import com.osatum.wbn.infrastructure.persistence.springdata.InstitutionMembershipSpringDataRepository;
import com.osatum.wbn.infrastructure.security.principal.InstitutionPrincipal;
import com.osatum.wbn.infrastructure.security.principal.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class IpAccessEnforcementFilter extends OncePerRequestFilter {

    private final ClientRequestMetadataResolutionService requestMetadataResolutionService;
    private final InstitutionIpMatcherService ipMatcherService;
    private final InstitutionMembershipSpringDataRepository membershipRepository;
    private final LoginSessionService loginSessionService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = requestMetadataResolutionService.resolve(new ClientRequestMetadataInput(
                request.getRemoteAddr(),
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getHeader("User-Agent")
        )).ipAddress();

        if (isAccessAllowed(authentication, clientIp)) {
            filterChain.doFilter(request, response);
            return;
        }

        revokeSession(authentication);
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"IP_ACCESS_DENIED\",\"message\":\"Access is not allowed from this IP address\"}");
    }

    private boolean isAccessAllowed(Authentication authentication, String clientIp) {
        return switch (authentication.getPrincipal()) {
            case InstitutionPrincipal institutionPrincipal -> isActiveSession(institutionPrincipal.loginSessionId())
                    && ipMatcherService.matchesInstitution(institutionPrincipal.institutionId(), clientIp);
            case UserPrincipal userPrincipal -> isUserAccessAllowed(userPrincipal, clientIp);
            default -> true;
        };
    }

    private boolean isUserAccessAllowed(UserPrincipal principal, String clientIp) {
        if (!isActiveSession(principal.getLoginSessionId()) || principal.getMembershipId() == null) {
            return false;
        }

        return membershipRepository.findById(principal.getMembershipId())
                .filter(membership -> belongsToPrincipal(membership, principal))
                .map(membership -> membership.isLoginAllowedFromAnyIp()
                        || ipMatcherService.matchesInstitution(membership.getInstitution().getId(), clientIp))
                .orElse(false);
    }

    private boolean isActiveSession(Long loginSessionId) {
        return loginSessionId == null || loginSessionService.isActive(loginSessionId);
    }

    private boolean belongsToPrincipal(InstitutionMembership membership, UserPrincipal principal) {
        return membership.getUser().getId().equals(principal.getUserId())
                && membership.getInstitution().getId().equals(principal.getInstitutionId())
                && membership.getRole() == principal.getInstitutionRole();
    }

    private void revokeSession(Authentication authentication) {
        Long loginSessionId = switch (authentication.getPrincipal()) {
            case InstitutionPrincipal institutionPrincipal -> institutionPrincipal.loginSessionId();
            case UserPrincipal userPrincipal -> userPrincipal.getLoginSessionId();
            default -> null;
        };
        if (loginSessionId == null) {
            return;
        }

        refreshTokenRepository.findByLoginSessionIdAndRevokedFalse(loginSessionId).forEach(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
        loginSessionService.revoke(loginSessionId);
        log.info("Revoked login session {} after an IP access denial", loginSessionId);
    }
}
