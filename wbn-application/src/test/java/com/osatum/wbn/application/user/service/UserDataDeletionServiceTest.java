package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.port.UserNotificationPort;
import com.osatum.wbn.application.user.dto.UserDataDeletionRequestDto;
import com.osatum.wbn.application.user.dto.UserDataDeletionTokenStatus;
import com.osatum.wbn.application.user.mapper.UserDataDeletionRequestMapper;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequest;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequestStatus;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.repository.user.UserDataDeletionRequestRepository;
import com.osatum.wbn.domain.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDataDeletionServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Mock
    private UserDataDeletionRequestRepository requestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private UserNotificationPort emailNotificationService;

    @Mock
    private UserService userService;

    private UserDataDeletionService service;

    @BeforeEach
    void setUp() {
        service = new UserDataDeletionService(
                requestRepository,
                userRepository,
                institutionRepository,
                emailNotificationService,
                userService,
                new UserDataDeletionRequestMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(service, "tokenValidityMinutes", 60L);
        ReflectionTestUtils.setField(service, "rateLimitWindowMinutes", 15L);
        ReflectionTestUtils.setField(service, "rateLimitMaxRequests", 3L);

        AtomicLong sequence = new AtomicLong(1);
        when(requestRepository.save(any(UserDataDeletionRequest.class))).thenAnswer(invocation -> {
            UserDataDeletionRequest request = invocation.getArgument(0);
            if (request.getId() == null) {
                request.setId(sequence.getAndIncrement());
            }
            return request;
        });
    }

    @Test
    void queuesDeletionForCurrentResearchUnitUserAndInvalidatesPreviousRequest() {
        AppUser user = activeUser();
        Institution institution = researchInstitution();
        UserDataDeletionRequest previous = deletionRequest(user, institution, UserDataDeletionRequestStatus.EMAIL_QUEUED, NOW.plus(30, ChronoUnit.MINUTES));
        EmailMessageLog emailLog = new EmailMessageLog();
        emailLog.setId(77L);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(institutionRepository.findById(institution.getId())).thenReturn(Optional.of(institution));
        when(userRepository.findByEmail("jan@example.edu")).thenReturn(Optional.of(user));
        when(requestRepository.countRecentByUserIdOrIpAddress(user.getId(), "127.0.0.1", NOW.minus(15, ChronoUnit.MINUTES)))
                .thenReturn(0L);
        when(requestRepository.findByUserIdAndStatusIn(eq(user.getId()), any())).thenReturn(List.of(previous));
        when(emailNotificationService.queueUserDataDeletionConfirmationMessage(eq(user), eq(institution), any(UserDataDeletionRequest.class), any()))
                .thenReturn(emailLog);

        var result = service.requestDeletion(requestDto(" Jan@Example.Edu ", user, institution), "127.0.0.1", "Mozilla/5.0 Chrome/125");

        assertThat(result.accepted()).isTrue();
        assertThat(previous.getStatus()).isEqualTo(UserDataDeletionRequestStatus.INVALIDATED);
        assertThat(previous.getInvalidatedAt()).isEqualTo(NOW);

        ArgumentCaptor<UserDataDeletionRequest> requestCaptor = ArgumentCaptor.forClass(UserDataDeletionRequest.class);
        verify(emailNotificationService).queueUserDataDeletionConfirmationMessage(eq(user), eq(institution), requestCaptor.capture(), any());
        UserDataDeletionRequest saved = requestCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("jan@example.edu");
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getInstitution()).isSameAs(institution);
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(60, ChronoUnit.MINUTES));
        assertThat(saved.getStatus()).isEqualTo(UserDataDeletionRequestStatus.EMAIL_QUEUED);
        assertThat(saved.getBrowser()).isEqualTo("Chrome");
    }

    @Test
    void rejectsWhenEmailDoesNotBelongToCurrentUser() {
        AppUser user = activeUser();
        Institution institution = researchInstitution();
        AppUser otherUser = activeUser();
        otherUser.setId(22L);
        otherUser.setEmail("other@example.edu");

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(institutionRepository.findById(institution.getId())).thenReturn(Optional.of(institution));
        when(userRepository.findByEmail("other@example.edu")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> service.requestDeletion(requestDto("other@example.edu", user, institution), "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nieprawidlowe konto");

        ArgumentCaptor<UserDataDeletionRequest> requestCaptor = ArgumentCaptor.forClass(UserDataDeletionRequest.class);
        verify(requestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(UserDataDeletionRequestStatus.REJECTED);
        verify(emailNotificationService, never()).queueUserDataDeletionConfirmationMessage(any(), any(), any(), any());
    }

    @Test
    void confirmDeletionCompletesRequestAnonymizesRequestsAndDeletesVerifiedUser() {
        AppUser user = activeUser();
        Institution institution = researchInstitution();
        String rawToken = "raw-delete-token";
        UserDataDeletionRequest request = deletionRequest(user, institution, UserDataDeletionRequestStatus.EMAIL_QUEUED, NOW.plus(30, ChronoUnit.MINUTES));
        request.setId(99L);
        request.setTokenHash(tokenHash(rawToken));

        when(requestRepository.findByTokenHash(tokenHash(rawToken))).thenReturn(Optional.of(request));
        when(requestRepository.findByUserIdAndStatusIn(eq(user.getId()), any())).thenReturn(List.of(request));

        var result = service.confirmDeletion(rawToken, "127.0.0.1", "Mozilla/5.0 Firefox/125");

        assertThat(result.status()).isEqualTo(UserDataDeletionTokenStatus.COMPLETED);
        assertThat(request.getStatus()).isEqualTo(UserDataDeletionRequestStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isEqualTo(NOW);
        assertThat(request.getUser()).isNull();
        assertThat(request.getEmail()).isEqualTo("anonymous@deleted.invalid");
        assertThat(request.getTokenHash()).isNull();
        verify(requestRepository).anonymizeRequestsForUser(user.getId(), "jan@example.edu");
        verify(userService).deleteForGdprAfterVerification(user.getId());
    }

    private UserDataDeletionRequestDto requestDto(String email, AppUser user, Institution institution) {
        UserDataDeletionRequestDto dto = new UserDataDeletionRequestDto();
        dto.setEmail(email);
        dto.setUserId(user.getId());
        dto.setInstitutionId(institution.getId());
        dto.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        return dto;
    }

    private AppUser activeUser() {
        AppUser user = new AppUser();
        user.setId(10L);
        user.setEmail("jan@example.edu");
        user.setFirstName("Jan");
        user.setLastName("Kowalski");
        user.setState(EntityState.ACTIVE);
        return user;
    }

    private Institution researchInstitution() {
        Institution institution = new Institution();
        institution.setId(20L);
        institution.setName("Research Unit");
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setState(EntityState.ACTIVE);
        return institution;
    }

    private UserDataDeletionRequest deletionRequest(
            AppUser user,
            Institution institution,
            UserDataDeletionRequestStatus status,
            Instant expiresAt
    ) {
        UserDataDeletionRequest request = new UserDataDeletionRequest();
        request.setEmail(user.getEmail());
        request.setUser(user);
        request.setInstitution(institution);
        request.setStatus(status);
        request.setRequestedAt(NOW.minus(5, ChronoUnit.MINUTES));
        request.setExpiresAt(expiresAt);
        request.setState(EntityState.ACTIVE);
        return request;
    }

    private static String tokenHash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
