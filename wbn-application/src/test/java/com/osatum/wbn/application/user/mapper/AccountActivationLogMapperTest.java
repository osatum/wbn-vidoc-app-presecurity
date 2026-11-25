package com.osatum.wbn.application.user.mapper;

import com.osatum.wbn.application.user.mapper.AccountActivationLogMapper;
import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.registration.AccountActivationLog;
import com.osatum.wbn.domain.model.registration.AccountActivationStatus;
import com.osatum.wbn.domain.model.registration.ActivationToken;
import com.osatum.wbn.domain.model.user.AppUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccountActivationLogMapperTest {
    private final AccountActivationLogMapper mapper = new AccountActivationLogMapper();

    @Test
    void mapsActivationLogToListDto() {
        AccountActivationLog log = activationLog();

        var dto = mapper.toListDto(log);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getUserId()).isEqualTo(10L);
        assertThat(dto.getUserEmail()).isEqualTo("jan@example.edu");
        assertThat(dto.getInstitutionId()).isEqualTo(20L);
        assertThat(dto.getInstitutionName()).isEqualTo("Research Unit");
        assertThat(dto.getMembershipId()).isEqualTo(30L);
        assertThat(dto.getActivationTokenId()).isEqualTo(40L);
        assertThat(dto.getAttemptedAt()).isEqualTo(Instant.parse("2026-06-12T10:00:00Z"));
        assertThat(dto.getStatus()).isEqualTo(AccountActivationStatus.SUCCESS_AUTO_ACTIVATED);
        assertThat(dto.getBrowser()).isEqualTo("Chrome");
        assertThat(dto.getOperatingSystem()).isEqualTo("Windows");
        assertThat(dto.getTermsAndPrivacyPolicyAccepted()).isTrue();
        assertThat(dto.getPersonalDataProcessingAcknowledged()).isTrue();
    }

    private AccountActivationLog activationLog() {
        AppUser user = new AppUser();
        user.setId(10L);
        user.setEmail("jan@example.edu");
        user.setFirstName("Jan");
        user.setLastName("Kowalski");

        Institution institution = new Institution();
        institution.setId(20L);
        institution.setName("Research Unit");
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setAuthMode(AuthMode.AUTH_REQUIRED);

        InstitutionMembership membership = new InstitutionMembership();
        membership.setId(30L);

        ActivationToken token = new ActivationToken();
        token.setId(40L);

        AccountActivationLog log = new AccountActivationLog();
        log.setId(1L);
        log.setUser(user);
        log.setInstitution(institution);
        log.setMembership(membership);
        log.setActivationToken(token);
        log.setAttemptedAt(Instant.parse("2026-06-12T10:00:00Z"));
        log.setStatus(AccountActivationStatus.SUCCESS_AUTO_ACTIVATED);
        log.setIpAddress("192.0.2.10");
        log.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36");
        log.setTermsAndPrivacyPolicyAccepted(true);
        log.setPersonalDataProcessingAcknowledged(true);
        return log;
    }
}
