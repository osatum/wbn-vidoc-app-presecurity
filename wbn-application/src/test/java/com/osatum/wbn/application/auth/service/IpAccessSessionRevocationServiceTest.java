package com.osatum.wbn.application.auth.service;

import com.osatum.wbn.domain.model.auth.LoginActorType;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import com.osatum.wbn.domain.model.auth.RefreshToken;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.auth.LoginSessionRepository;
import com.osatum.wbn.domain.repository.auth.RefreshTokenRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IpAccessSessionRevocationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    @Test
    void revokesInstitutionAndIpRestrictedUserSessionsOnly() {
        var loginSessionRepository = mock(LoginSessionRepository.class);
        var refreshTokenRepository = mock(RefreshTokenRepository.class);
        var membershipRepository = mock(InstitutionMembershipRepository.class);
        var service = new IpAccessSessionRevocationService(
                loginSessionRepository,
                refreshTokenRepository,
                membershipRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var restrictedUserSession = userSession(1L, 10L);
        var unrestrictedUserSession = userSession(2L, 20L);
        var institutionSession = institutionSession(3L);
        var restrictedMembership = membership(10L, false);
        var unrestrictedMembership = membership(20L, true);
        var restrictedToken = mock(RefreshToken.class);
        var institutionToken = mock(RefreshToken.class);

        when(loginSessionRepository.findActiveByInstitutionId(100L, NOW)).thenReturn(List.of(
                restrictedUserSession,
                unrestrictedUserSession,
                institutionSession
        ));
        when(membershipRepository.findByUserIdAndInstitutionIdAndRole(10L, 100L, InstitutionRole.INSTITUTION_USER))
                .thenReturn(Optional.of(restrictedMembership));
        when(membershipRepository.findByUserIdAndInstitutionIdAndRole(20L, 100L, InstitutionRole.INSTITUTION_USER))
                .thenReturn(Optional.of(unrestrictedMembership));
        when(refreshTokenRepository.findByLoginSessionIdAndRevokedFalse(1L)).thenReturn(List.of(restrictedToken));
        when(refreshTokenRepository.findByLoginSessionIdAndRevokedFalse(3L)).thenReturn(List.of(institutionToken));

        service.revokeActiveSessionsForInstitution(100L);

        assertThat(restrictedUserSession.getStatus()).isEqualTo(LoginSessionStatus.REVOKED);
        assertThat(restrictedUserSession.getEndedAt()).isEqualTo(NOW);
        assertThat(unrestrictedUserSession.getStatus()).isEqualTo(LoginSessionStatus.ACTIVE);
        assertThat(institutionSession.getStatus()).isEqualTo(LoginSessionStatus.REVOKED);
        verify(restrictedToken).setRevoked(true);
        verify(institutionToken).setRevoked(true);
        verify(loginSessionRepository).save(restrictedUserSession);
        verify(loginSessionRepository).save(institutionSession);
        verify(loginSessionRepository, never()).save(unrestrictedUserSession);
    }

    private static LoginSession userSession(Long sessionId, Long userId) {
        var user = new AppUser();
        user.setId(userId);
        var institution = new Institution();
        institution.setId(100L);
        var session = new LoginSession();
        session.setId(sessionId);
        session.setActorType(LoginActorType.USER);
        session.setUser(user);
        session.setInstitution(institution);
        session.setInstitutionRole(InstitutionRole.INSTITUTION_USER);
        session.setStatus(LoginSessionStatus.ACTIVE);
        return session;
    }

    private static LoginSession institutionSession(Long sessionId) {
        var institution = new Institution();
        institution.setId(100L);
        var session = new LoginSession();
        session.setId(sessionId);
        session.setActorType(LoginActorType.INSTITUTION);
        session.setInstitution(institution);
        session.setStatus(LoginSessionStatus.ACTIVE);
        return session;
    }

    private static InstitutionMembership membership(Long userId, boolean loginAllowedFromAnyIp) {
        var user = new AppUser();
        user.setId(userId);
        var institution = new Institution();
        institution.setId(100L);
        var membership = new InstitutionMembership();
        membership.setUser(user);
        membership.setInstitution(institution);
        membership.setRole(InstitutionRole.INSTITUTION_USER);
        membership.setLoginAllowedFromAnyIp(loginAllowedFromAnyIp);
        return membership;
    }
}
