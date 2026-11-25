package com.osatum.wbn.application.institution.service;

import com.osatum.wbn.application.institution.authorization.CanInstitutionManage;
import com.osatum.wbn.application.institution.dto.InstitutionDeletionConfirmationDto;
import com.osatum.wbn.application.security.Actor;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.InstitutionActor;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.shared.exception.InstitutionDeletionConfirmationException;
import com.osatum.wbn.application.shared.exception.InstitutionDeletionNotAllowedException;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionDeletionConfirmation;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.repository.institution.InstitutionDeletionConfirmationRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionDeletionRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class InstitutionDeletionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final char[] DIGITS = "23456789".toCharArray();
    private static final char[] ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final Pattern CONFIRMATION_CODE_PATTERN = Pattern.compile("[A-Z0-9]{8}");

    private final InstitutionRepository institutionRepository;
    private final InstitutionDeletionConfirmationRepository confirmationRepository;
    private final InstitutionDeletionRepository deletionRepository;
    private final CurrentActorProvider currentActorProvider;
    private final Clock clock;

    @Value("${app.institution-deletion.confirmation-validity-minutes:5}")
    private long confirmationValidityMinutes = 5L;

    @CanInstitutionManage
    @Transactional
    public InstitutionDeletionConfirmationDto createConfirmation(Long institutionId) {
        Actor actor = currentActorProvider.currentActor();
        Institution institution = getInstitutionForUpdate(institutionId);
        validateInstitutionCanBeDeleted(institution, actor);

        String actorKey = actorKey(actor);
        String confirmationCode = generateConfirmationCode();
        Instant now = clock.instant();

        confirmationRepository.deleteActiveForInstitutionAndActor(institutionId, actorKey);

        InstitutionDeletionConfirmation confirmation = new InstitutionDeletionConfirmation();
        confirmation.setId(UUID.randomUUID());
        confirmation.setInstitutionId(institutionId);
        confirmation.setActorKey(actorKey);
        confirmation.setCodeHash(hash(confirmationCode));
        confirmation.setCreatedAt(now);
        confirmation.setExpiresAt(now.plus(Math.max(1L, confirmationValidityMinutes), ChronoUnit.MINUTES));

        InstitutionDeletionConfirmation saved = confirmationRepository.save(confirmation);
        return new InstitutionDeletionConfirmationDto(
                saved.getId(),
                confirmationCode,
                saved.getExpiresAt()
        );
    }

    @CanInstitutionManage
    @Transactional
    public void deleteInstitution(Long institutionId, UUID confirmationId, String confirmationCode) {
        Actor actor = currentActorProvider.currentActor();
        Institution institution = getInstitutionForUpdate(institutionId);
        validateInstitutionCanBeDeleted(institution, actor);

        if (confirmationId == null) {
            throw confirmationException(InstitutionDeletionConfirmationException.Reason.INVALID);
        }

        InstitutionDeletionConfirmation confirmation = confirmationRepository.findByIdForUpdate(confirmationId)
                .orElseThrow(() -> confirmationException(InstitutionDeletionConfirmationException.Reason.INVALID));

        verifyConfirmation(confirmation, institutionId, actorKey(actor), confirmationCode);
        confirmation.setUsedAt(clock.instant());
        confirmationRepository.save(confirmation);

        var usersWithoutOtherInstitution = deletionRepository.findUsersWithoutOtherInstitution(institutionId);
        deletionRepository.deleteDirectDataForInstitution(institutionId);
        usersWithoutOtherInstitution.forEach(deletionRepository::deleteAllDataForUser);
        deletionRepository.deleteMembershipsForInstitution(institutionId);
        confirmationRepository.deleteByInstitutionId(institutionId);
        deletionRepository.deleteInstitution(institutionId);
    }

    private Institution getInstitutionForUpdate(Long institutionId) {
        return institutionRepository.findByIdForUpdate(institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found: " + institutionId));
    }

    private void validateInstitutionCanBeDeleted(Institution institution, Actor actor) {
        if (Objects.equals(actor.institutionId(), institution.getId())) {
            throw new InstitutionDeletionNotAllowedException(
                    "The active institution context cannot be deleted."
            );
        }

        if (InstitutionType.SYSTEM_OPERATOR.equals(institution.getInstitutionType())
                && EntityState.ACTIVE.equals(institution.getState())
                && institutionRepository.countActiveByType(InstitutionType.SYSTEM_OPERATOR) <= 1) {
            throw new InstitutionDeletionNotAllowedException(
                    "The last active system operator institution cannot be deleted."
            );
        }
    }

    private void verifyConfirmation(
            InstitutionDeletionConfirmation confirmation,
            Long institutionId,
            String actorKey,
            String confirmationCode
    ) {
        if (!Objects.equals(confirmation.getInstitutionId(), institutionId)
                || !Objects.equals(confirmation.getActorKey(), actorKey)) {
            throw confirmationException(InstitutionDeletionConfirmationException.Reason.INVALID);
        }

        if (confirmation.getUsedAt() != null) {
            throw confirmationException(InstitutionDeletionConfirmationException.Reason.USED);
        }

        Instant expiresAt = confirmation.getExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            throw confirmationException(InstitutionDeletionConfirmationException.Reason.EXPIRED);
        }

        String normalizedCode = normalizeConfirmationCode(confirmationCode);
        if (!CONFIRMATION_CODE_PATTERN.matcher(normalizedCode).matches()
                || confirmation.getCodeHash() == null
                || !MessageDigest.isEqual(
                        hash(normalizedCode).getBytes(StandardCharsets.UTF_8),
                        confirmation.getCodeHash().getBytes(StandardCharsets.UTF_8)
                )) {
            throw confirmationException(InstitutionDeletionConfirmationException.Reason.INVALID);
        }
    }

    private InstitutionDeletionConfirmationException confirmationException(
            InstitutionDeletionConfirmationException.Reason reason
    ) {
        return new InstitutionDeletionConfirmationException(reason);
    }

    private String actorKey(Actor actor) {
        return switch (actor) {
            case UserActor userActor -> "USER:%s:%s".formatted(userActor.userId(), userActor.loginSessionId());
            case InstitutionActor institutionActor -> "INSTITUTION:%s:%s".formatted(
                    institutionActor.institutionId(),
                    institutionActor.loginSessionId()
            );
        };
    }

    private String generateConfirmationCode() {
        char[] code = new char[8];
        code[0] = LETTERS[SECURE_RANDOM.nextInt(LETTERS.length)];
        code[1] = DIGITS[SECURE_RANDOM.nextInt(DIGITS.length)];

        for (int index = 2; index < code.length; index++) {
            code[index] = ALPHANUMERIC[SECURE_RANDOM.nextInt(ALPHANUMERIC.length)];
        }

        for (int index = code.length - 1; index > 0; index--) {
            int replacementIndex = SECURE_RANDOM.nextInt(index + 1);
            char current = code[index];
            code[index] = code[replacementIndex];
            code[replacementIndex] = current;
        }

        return new String(code);
    }

    private String normalizeConfirmationCode(String confirmationCode) {
        return confirmationCode == null ? "" : confirmationCode.trim().toUpperCase(Locale.ROOT);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash institution deletion confirmation.", exception);
        }
    }
}
