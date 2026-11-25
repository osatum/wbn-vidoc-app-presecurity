package com.osatum.wbn.application.institution.service;

import com.osatum.wbn.application.institution.dto.InstitutionDeletionConfirmationDto;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.shared.exception.InstitutionDeletionConfirmationException;
import com.osatum.wbn.application.shared.exception.InstitutionDeletionNotAllowedException;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionDeletionConfirmation;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.repository.institution.InstitutionDeletionConfirmationRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionDeletionRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionDeletionUser;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstitutionDeletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T20:00:00Z");
    private static final Long INSTITUTION_ID = 20L;

    private InstitutionRepository institutionRepository;
    private InstitutionDeletionConfirmationRepository confirmationRepository;
    private InstitutionDeletionRepository deletionRepository;
    private CurrentActorProvider currentActorProvider;
    private InstitutionDeletionService service;

    @BeforeEach
    void setUp() {
        institutionRepository = mock(InstitutionRepository.class);
        confirmationRepository = mock(InstitutionDeletionConfirmationRepository.class);
        deletionRepository = mock(InstitutionDeletionRepository.class);
        currentActorProvider = mock(CurrentActorProvider.class);
        service = new InstitutionDeletionService(
                institutionRepository,
                confirmationRepository,
                deletionRepository,
                currentActorProvider,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        when(currentActorProvider.currentActor()).thenReturn(operatorActor());
        when(confirmationRepository.save(any(InstitutionDeletionConfirmation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAnEightCharacterConfirmationBoundToCurrentOperator() {
        when(institutionRepository.findByIdForUpdate(INSTITUTION_ID)).thenReturn(Optional.of(researchInstitution()));

        InstitutionDeletionConfirmationDto result = service.createConfirmation(INSTITUTION_ID);

        assertThat(result.confirmationId()).isNotNull();
        assertThat(result.confirmationCode()).matches("(?=.*[A-Z])(?=.*[0-9])[A-Z0-9]{8}");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(300));

        ArgumentCaptor<InstitutionDeletionConfirmation> confirmationCaptor =
                ArgumentCaptor.forClass(InstitutionDeletionConfirmation.class);
        verify(confirmationRepository).save(confirmationCaptor.capture());
        InstitutionDeletionConfirmation confirmation = confirmationCaptor.getValue();
        assertThat(confirmation.getInstitutionId()).isEqualTo(INSTITUTION_ID);
        assertThat(confirmation.getActorKey()).isEqualTo("USER:100:500");
        assertThat(confirmation.getCodeHash()).isNotEqualTo(result.confirmationCode());
        verify(confirmationRepository).deleteActiveForInstitutionAndActor(INSTITUTION_ID, "USER:100:500");
    }

    @Test
    void deletesDirectDataAndAllDataOfUsersWithoutAnotherInstitution() {
        InstitutionDeletionConfirmation confirmation = confirmation("AB34CD56", NOW.plusSeconds(300));
        InstitutionDeletionUser user = new InstitutionDeletionUser(10L, "jan@example.edu");

        when(institutionRepository.findByIdForUpdate(INSTITUTION_ID)).thenReturn(Optional.of(researchInstitution()));
        when(confirmationRepository.findByIdForUpdate(confirmation.getId())).thenReturn(Optional.of(confirmation));
        when(deletionRepository.findUsersWithoutOtherInstitution(INSTITUTION_ID)).thenReturn(List.of(user));

        service.deleteInstitution(INSTITUTION_ID, confirmation.getId(), "AB34CD56");

        assertThat(confirmation.getUsedAt()).isEqualTo(NOW);
        InOrder order = inOrder(deletionRepository, confirmationRepository);
        order.verify(deletionRepository).findUsersWithoutOtherInstitution(INSTITUTION_ID);
        order.verify(deletionRepository).deleteDirectDataForInstitution(INSTITUTION_ID);
        order.verify(deletionRepository).deleteAllDataForUser(user);
        order.verify(deletionRepository).deleteMembershipsForInstitution(INSTITUTION_ID);
        order.verify(confirmationRepository).deleteByInstitutionId(INSTITUTION_ID);
        order.verify(deletionRepository).deleteInstitution(INSTITUTION_ID);
    }

    @Test
    void keepsAUserAccountWhenTheUserHasAnotherInstitution() {
        InstitutionDeletionConfirmation confirmation = confirmation("AB34CD56", NOW.plusSeconds(300));

        when(institutionRepository.findByIdForUpdate(INSTITUTION_ID)).thenReturn(Optional.of(researchInstitution()));
        when(confirmationRepository.findByIdForUpdate(confirmation.getId())).thenReturn(Optional.of(confirmation));
        when(deletionRepository.findUsersWithoutOtherInstitution(INSTITUTION_ID)).thenReturn(List.of());

        service.deleteInstitution(INSTITUTION_ID, confirmation.getId(), "AB34CD56");

        verify(deletionRepository, never()).deleteAllDataForUser(any(InstitutionDeletionUser.class));
        verify(deletionRepository).deleteDirectDataForInstitution(INSTITUTION_ID);
        verify(deletionRepository).deleteMembershipsForInstitution(INSTITUTION_ID);
        verify(deletionRepository).deleteInstitution(INSTITUTION_ID);
    }

    @Test
    void rejectsAnInvalidConfirmationCodeWithoutDeletingData() {
        InstitutionDeletionConfirmation confirmation = confirmation("AB34CD56", NOW.plusSeconds(300));

        when(institutionRepository.findByIdForUpdate(INSTITUTION_ID)).thenReturn(Optional.of(researchInstitution()));
        when(confirmationRepository.findByIdForUpdate(confirmation.getId())).thenReturn(Optional.of(confirmation));

        assertThatThrownBy(() -> service.deleteInstitution(INSTITUTION_ID, confirmation.getId(), "ZZ99ZZ99"))
                .isInstanceOf(InstitutionDeletionConfirmationException.class)
                .extracting(exception -> ((InstitutionDeletionConfirmationException) exception).getReason())
                .isEqualTo(InstitutionDeletionConfirmationException.Reason.INVALID);

        verify(deletionRepository, never()).deleteDirectDataForInstitution(any());
        verify(confirmationRepository, never()).deleteByInstitutionId(any());
    }

    @Test
    void rejectsMissingConfirmationIdentifierWithoutDeletingData() {
        when(institutionRepository.findByIdForUpdate(INSTITUTION_ID)).thenReturn(Optional.of(researchInstitution()));

        assertThatThrownBy(() -> service.deleteInstitution(INSTITUTION_ID, null, "AB34CD56"))
                .isInstanceOf(InstitutionDeletionConfirmationException.class);

        verify(deletionRepository, never()).deleteDirectDataForInstitution(any());
        verify(confirmationRepository, never()).deleteByInstitutionId(any());
    }

    @Test
    void rejectsDeletionOfTheActiveInstitutionContext() {
        Institution institution = researchInstitution();
        institution.setId(99L);
        when(institutionRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(institution));

        assertThatThrownBy(() -> service.createConfirmation(99L))
                .isInstanceOf(InstitutionDeletionNotAllowedException.class);

        verify(confirmationRepository, never()).save(any(InstitutionDeletionConfirmation.class));
    }

    private InstitutionDeletionConfirmation confirmation(String code, Instant expiresAt) {
        InstitutionDeletionConfirmation confirmation = new InstitutionDeletionConfirmation();
        confirmation.setId(UUID.randomUUID());
        confirmation.setInstitutionId(INSTITUTION_ID);
        confirmation.setActorKey("USER:100:500");
        confirmation.setCodeHash(hash(code));
        confirmation.setCreatedAt(NOW.minusSeconds(30));
        confirmation.setExpiresAt(expiresAt);
        return confirmation;
    }

    private Institution researchInstitution() {
        Institution institution = new Institution();
        institution.setId(INSTITUTION_ID);
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setState(EntityState.ACTIVE);
        return institution;
    }

    private UserActor operatorActor() {
        return new UserActor(
                100L,
                "operator@example.edu",
                99L,
                InstitutionType.SYSTEM_OPERATOR,
                InstitutionRole.INSTITUTION_ADMIN,
                500L
        );
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
