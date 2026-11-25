package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.auth.LoginActorType;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import com.osatum.wbn.domain.model.auth.RefreshToken;
import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.notification.EmailMessageStatus;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequest;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequestStatus;
import com.osatum.wbn.domain.model.registration.AccountActivationLog;
import com.osatum.wbn.domain.model.registration.AccountActivationStatus;
import com.osatum.wbn.domain.model.registration.ActivationToken;
import com.osatum.wbn.domain.model.registration.ActivationTokenStatus;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequest;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequestStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PersistenceRegressionTestConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gdpr-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Transactional
class UserPersonalDataDeletionRepositoryAdapterTest {
    private static final String ANONYMIZED_EMAIL = "anonymous@deleted.invalid";
    private static final String ANONYMIZED_IP_ADDRESS = "0.0.0.0";
    private static final String ANONYMIZED_ACTOR = "anonymous";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserPersonalDataDeletionRepositoryAdapter repository;

    @Test
    void keepsUserActivityAnonymizedAndAllowsAccountDeletion() {
        TestData data = createTestData();

        repository.deleteRefreshTokensForUser(data.userId());
        repository.anonymizeAccountActivationLogsForUser(data.userId());
        repository.anonymizePasswordResetRequestsForUser(data.userId(), data.userEmail());
        repository.anonymizeUserDataDeletionRequestsForUser(data.userId(), data.userEmail());
        repository.anonymizeEmailMessageLogsForUser(data.userId(), data.userEmail());
        repository.anonymizeLoginSessionsForUser(data.userId(), data.userEmail());
        repository.anonymizeNavigationAuditEventsForUser(data.userId());
        repository.deleteActivationTokensForUser(data.userId());
        repository.clearMembershipActivatedByUser(data.userId());
        repository.deleteMembershipsForUser(data.userId());

        entityManager.remove(entityManager.find(AppUser.class, data.userId()));
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(AppUser.class, data.userId())).isNull();
        assertThat(count("RefreshToken")).isZero();
        assertThat(count("ActivationToken")).isZero();
        assertThat(count("InstitutionMembership")).isZero();

        AccountActivationLog activationLog = entityManager.find(AccountActivationLog.class, data.accountActivationLogId());
        assertThat(activationLog).isNotNull();
        assertThat(activationLog.getUser()).isNull();
        assertThat(activationLog.getMembership()).isNull();
        assertThat(activationLog.getActivationToken()).isNull();
        assertThat(activationLog.getInstitution()).isNotNull();
        assertThat(activationLog.getIpAddress()).isNull();
        assertThat(activationLog.getUserAgent()).isNull();
        assertThat(activationLog.getErrorMessage()).isNull();
        assertThat(activationLog.getTechnicalData()).isNull();
        assertThat(activationLog.getCreatedBy()).isEqualTo(ANONYMIZED_ACTOR);
        assertThat(activationLog.getEditedBy()).isEqualTo(ANONYMIZED_ACTOR);

        PasswordResetRequest resetRequest = entityManager.find(PasswordResetRequest.class, data.passwordResetRequestId());
        assertThat(resetRequest).isNotNull();
        assertThat(resetRequest.getUser()).isNull();
        assertThat(resetRequest.getEmail()).isEqualTo(ANONYMIZED_EMAIL);
        assertThat(resetRequest.getTokenHash()).isNull();
        assertThat(resetRequest.getIpAddress()).isNull();
        assertThat(resetRequest.getUserAgent()).isNull();
        assertThat(resetRequest.getBrowser()).isNull();
        assertThat(resetRequest.getErrorMessage()).isNull();
        assertThat(resetRequest.getCreatedBy()).isEqualTo(ANONYMIZED_ACTOR);
        assertThat(resetRequest.getEditedBy()).isEqualTo(ANONYMIZED_ACTOR);

        UserDataDeletionRequest deletionRequest = entityManager.find(UserDataDeletionRequest.class, data.userDataDeletionRequestId());
        assertThat(deletionRequest).isNotNull();
        assertThat(deletionRequest.getUser()).isNull();
        assertThat(deletionRequest.getEmail()).isEqualTo(ANONYMIZED_EMAIL);
        assertThat(deletionRequest.getTokenHash()).isNull();
        assertThat(deletionRequest.getIpAddress()).isNull();
        assertThat(deletionRequest.getUserAgent()).isNull();
        assertThat(deletionRequest.getBrowser()).isNull();
        assertThat(deletionRequest.getErrorMessage()).isNull();
        assertThat(deletionRequest.getCreatedBy()).isEqualTo(ANONYMIZED_ACTOR);
        assertThat(deletionRequest.getEditedBy()).isEqualTo(ANONYMIZED_ACTOR);

        EmailMessageLog emailLog = entityManager.find(EmailMessageLog.class, data.emailMessageLogId());
        assertThat(emailLog).isNotNull();
        assertThat(emailLog.getUser()).isNull();
        assertThat(emailLog.getMembership()).isNull();
        assertThat(emailLog.getInstitution()).isNotNull();
        assertThat(emailLog.getRecipient()).isEqualTo(ANONYMIZED_EMAIL);
        assertThat(emailLog.getErrorMessage()).isNull();
        assertThat(emailLog.getContextData()).isNull();
        assertThat(emailLog.getCreatedBy()).isEqualTo(ANONYMIZED_ACTOR);
        assertThat(emailLog.getEditedBy()).isEqualTo(ANONYMIZED_ACTOR);

        LoginSession loginSession = entityManager.find(LoginSession.class, data.loginSessionId());
        assertThat(loginSession).isNotNull();
        assertThat(loginSession.getUser()).isNull();
        assertThat(loginSession.getInstitution()).isNotNull();
        assertThat(loginSession.getUserEmail()).isEqualTo(ANONYMIZED_EMAIL);
        assertThat(loginSession.getIpAddress()).isEqualTo(ANONYMIZED_IP_ADDRESS);
        assertThat(loginSession.getUserAgent()).isNull();
        assertThat(loginSession.getBrowser()).isNull();
        assertThat(loginSession.getCreatedBy()).isEqualTo(ANONYMIZED_ACTOR);
        assertThat(loginSession.getEditedBy()).isEqualTo(ANONYMIZED_ACTOR);

        NavigationAuditEvent navigationEvent = entityManager.find(NavigationAuditEvent.class, data.navigationAuditEventId());
        assertThat(navigationEvent).isNotNull();
        assertThat(navigationEvent.getUser()).isNull();
        assertThat(navigationEvent.getInstitution()).isNotNull();
        assertThat(navigationEvent.getLoginSessionId()).isNull();
        assertThat(navigationEvent.getFrontendSessionId()).isNull();
        assertThat(navigationEvent.getViewId()).isNull();
        assertThat(navigationEvent.getIpAddress()).isEqualTo(ANONYMIZED_IP_ADDRESS);
        assertThat(navigationEvent.getUserAgent()).isNull();
        assertThat(navigationEvent.getCreatedBy()).isEqualTo(ANONYMIZED_ACTOR);
        assertThat(navigationEvent.getEditedBy()).isEqualTo(ANONYMIZED_ACTOR);
    }

    private TestData createTestData() {
        AppUser user = user("jan@example.org");
        entityManager.persist(user);

        Institution institution = institution();
        entityManager.persist(institution);

        InstitutionMembership membership = membership(user, institution);
        entityManager.persist(membership);

        ActivationToken activationToken = activationToken(user, institution, membership);
        entityManager.persist(activationToken);

        AccountActivationLog activationLog = accountActivationLog(user, institution, membership, activationToken);
        entityManager.persist(activationLog);

        PasswordResetRequest passwordResetRequest = passwordResetRequest(user);
        entityManager.persist(passwordResetRequest);

        UserDataDeletionRequest userDataDeletionRequest = userDataDeletionRequest(user, institution);
        entityManager.persist(userDataDeletionRequest);

        EmailMessageLog emailMessageLog = emailMessageLog(user, institution, membership);
        entityManager.persist(emailMessageLog);

        LoginSession loginSession = loginSession(user, institution);
        entityManager.persist(loginSession);

        NavigationAuditEvent navigationAuditEvent = navigationAuditEvent(user, institution, loginSession);
        entityManager.persist(navigationAuditEvent);

        RefreshToken refreshToken = refreshToken(user, institution, membership, loginSession);
        entityManager.persist(refreshToken);

        entityManager.flush();
        TestData data = new TestData(
                user.getId(),
                user.getEmail(),
                activationLog.getId(),
                passwordResetRequest.getId(),
                userDataDeletionRequest.getId(),
                emailMessageLog.getId(),
                loginSession.getId(),
                navigationAuditEvent.getId()
        );
        entityManager.clear();
        return data;
    }

    private AppUser user(String email) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFirstName("Jan");
        user.setLastName("Kowalski");
        user.setPasswordHash("hash");
        user.setRoles("ROLE_USER");
        return user;
    }

    private Institution institution() {
        Institution institution = new Institution();
        institution.setName("Research Unit");
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setAuthMode(AuthMode.AUTH_REQUIRED);
        return institution;
    }

    private InstitutionMembership membership(AppUser user, Institution institution) {
        InstitutionMembership membership = new InstitutionMembership();
        membership.setUser(user);
        membership.setInstitution(institution);
        membership.setRole(InstitutionRole.INSTITUTION_USER);
        return membership;
    }

    private ActivationToken activationToken(AppUser user, Institution institution, InstitutionMembership membership) {
        ActivationToken token = new ActivationToken();
        token.setUser(user);
        token.setInstitution(institution);
        token.setMembership(membership);
        token.setTokenHash("activation-token-hash");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setStatus(ActivationTokenStatus.ACTIVE);
        return token;
    }

    private AccountActivationLog accountActivationLog(
            AppUser user,
            Institution institution,
            InstitutionMembership membership,
            ActivationToken activationToken
    ) {
        AccountActivationLog log = new AccountActivationLog();
        log.setUser(user);
        log.setInstitution(institution);
        log.setMembership(membership);
        log.setActivationToken(activationToken);
        log.setAttemptedAt(Instant.now());
        log.setStatus(AccountActivationStatus.SUCCESS_AUTO_ACTIVATED);
        log.setIpAddress("192.0.2.10");
        log.setUserAgent("Mozilla/5.0");
        log.setErrorMessage("personal error");
        log.setTechnicalData("{\"email\":\"jan@example.org\"}");
        return log;
    }

    private PasswordResetRequest passwordResetRequest(AppUser user) {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setUser(user);
        request.setEmail(user.getEmail());
        request.setTokenHash("password-reset-token-hash");
        request.setStatus(PasswordResetRequestStatus.EMAIL_QUEUED);
        request.setRequestedAt(Instant.now());
        request.setExpiresAt(Instant.now().plusSeconds(3600));
        request.setIpAddress("192.0.2.11");
        request.setUserAgent("Mozilla/5.0");
        request.setBrowser("Firefox");
        request.setErrorMessage("personal reset error");
        return request;
    }

    private UserDataDeletionRequest userDataDeletionRequest(AppUser user, Institution institution) {
        UserDataDeletionRequest request = new UserDataDeletionRequest();
        request.setUser(user);
        request.setInstitution(institution);
        request.setEmail(user.getEmail());
        request.setTokenHash("user-data-deletion-token-hash");
        request.setStatus(UserDataDeletionRequestStatus.EMAIL_QUEUED);
        request.setRequestedAt(Instant.now());
        request.setExpiresAt(Instant.now().plusSeconds(3600));
        request.setIpAddress("192.0.2.13");
        request.setUserAgent("Mozilla/5.0");
        request.setBrowser("Chrome");
        request.setErrorMessage("personal deletion error");
        return request;
    }

    private EmailMessageLog emailMessageLog(AppUser user, Institution institution, InstitutionMembership membership) {
        EmailMessageLog log = new EmailMessageLog();
        log.setEmailType(EmailType.PASSWORD_RESET_LINK);
        log.setRecipient(user.getEmail());
        log.setUser(user);
        log.setInstitution(institution);
        log.setMembership(membership);
        log.setStatus(EmailMessageStatus.SENT);
        log.setAttemptCount(1);
        log.setSentAt(Instant.now());
        log.setErrorMessage("personal mail error");
        log.setTemplateIdentifier("password-reset");
        log.setContextData("{\"recipient\":\"jan@example.org\"}");
        return log;
    }

    private LoginSession loginSession(AppUser user, Institution institution) {
        Instant now = Instant.now();
        LoginSession session = new LoginSession();
        session.setActorType(LoginActorType.USER);
        session.setUser(user);
        session.setInstitution(institution);
        session.setUserEmail(user.getEmail());
        session.setInstitutionName(institution.getName());
        session.setInstitutionType(institution.getInstitutionType());
        session.setInstitutionRole(InstitutionRole.INSTITUTION_USER);
        session.setIpAddress("192.0.2.12");
        session.setUserAgent("Mozilla/5.0");
        session.setBrowser("Chrome");
        session.setStartedAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plusSeconds(3600));
        session.setStatus(LoginSessionStatus.ACTIVE);
        return session;
    }

    private RefreshToken refreshToken(
            AppUser user,
            Institution institution,
            InstitutionMembership membership,
            LoginSession loginSession
    ) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setInstitution(institution);
        token.setInstitutionMembership(membership);
        token.setLoginSession(loginSession);
        token.setTokenHash("refresh-token-hash");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }

    private NavigationAuditEvent navigationAuditEvent(AppUser user, Institution institution, LoginSession loginSession) {
        Instant now = Instant.now();
        NavigationAuditEvent event = new NavigationAuditEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType(NavigationAuditEventType.PAGE_VIEW);
        event.setOccurredAt(now);
        event.setReceivedAt(now);
        event.setActorType(NavigationAuditActorType.USER);
        event.setUser(user);
        event.setInstitution(institution);
        event.setInstitutionType(institution.getInstitutionType());
        event.setInstitutionRole(InstitutionRole.INSTITUTION_USER);
        event.setLoginSessionId(loginSession.getId());
        event.setFrontendSessionId("frontend-session");
        event.setViewId("view-id");
        event.setPageKey("institution.journals.details");
        event.setRouteTemplate("/institution/user/journals/:id");
        event.setRoutePath("/institution/user/journals/309");
        event.setResourceType("journal");
        event.setResourceId("309");
        event.setIpAddress("192.0.2.14");
        event.setUserAgent("Mozilla/5.0");
        return event;
    }

    private long count(String entityName) {
        return entityManager.createQuery("select count(e) from " + entityName + " e", Long.class)
                .getSingleResult();
    }

    private record TestData(
            Long userId,
            String userEmail,
            Long accountActivationLogId,
            Long passwordResetRequestId,
            Long userDataDeletionRequestId,
            Long emailMessageLogId,
            Long loginSessionId,
            Long navigationAuditEventId
    ) {
    }
}
