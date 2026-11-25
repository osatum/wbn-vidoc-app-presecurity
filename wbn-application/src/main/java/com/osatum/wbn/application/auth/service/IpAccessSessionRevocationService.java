package com.osatum.wbn.application.auth.service;

import com.osatum.wbn.domain.model.auth.LoginSessionEndReason;
import com.osatum.wbn.domain.model.auth.LoginActorType;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.repository.auth.LoginSessionRepository;
import com.osatum.wbn.domain.repository.auth.RefreshTokenRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class IpAccessSessionRevocationService {

    private final LoginSessionRepository loginSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final InstitutionMembershipRepository membershipRepository;
    private final Clock clock;

    @Transactional
    public void revokeActiveSessionsForInstitution(Long institutionId) {
        loginSessionRepository.findActiveByInstitutionId(institutionId, clock.instant()).forEach(session -> {
            if (!isRestrictedToInstitutionNetwork(session)) {
                return;
            }
            refreshTokenRepository.findByLoginSessionIdAndRevokedFalse(session.getId()).forEach(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
            session.setEndedAt(clock.instant());
            session.setEndReason(LoginSessionEndReason.REVOKED);
            session.setStatus(com.osatum.wbn.domain.model.auth.LoginSessionStatus.REVOKED);
            loginSessionRepository.save(session);
        });
    }

    private boolean isRestrictedToInstitutionNetwork(LoginSession session) {
        if (session.getActorType() == LoginActorType.INSTITUTION) {
            return true;
        }
        if (session.getActorType() != LoginActorType.USER
                || session.getUser() == null
                || session.getInstitution() == null
                || session.getInstitutionRole() == null) {
            return true;
        }

        return membershipRepository.findByUserIdAndInstitutionIdAndRole(
                        session.getUser().getId(),
                        session.getInstitution().getId(),
                        session.getInstitutionRole()
                )
                .map(membership -> !membership.isLoginAllowedFromAnyIp())
                .orElse(true);
    }
}
