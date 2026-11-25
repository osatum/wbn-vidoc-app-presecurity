package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.dto.RegistrationSubmitDto;
import com.osatum.wbn.application.user.dto.RegistrationSubmitResultDto;
import com.osatum.wbn.application.user.dto.RegistrationSubmitStatus;
import com.osatum.wbn.application.shared.exception.ResourceAlreadyExistsException;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionEmailDomain;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.model.registration.ActivationToken;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.repository.user.UserRepository;
import com.osatum.wbn.domain.service.institution.InstitutionIpMatcherService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegistrationSubmitService {
    private static final String EXISTING_ACCOUNT_MESSAGE =
            "Uzytkownik o podanym adresie e-mail ma juz konto. Przejdz do logowania.";

    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;
    private final InstitutionMembershipRepository membershipRepository;
    private final InstitutionIpMatcherService matcher;
    private final PasswordEncoder passwordEncoder;
    private final AccountActivationService accountActivationService;

    /**
     * Rejestruje użytkownika w instytucji bez zapisu user-agenta klienta.
     */
    @Transactional
    public RegistrationSubmitResultDto submit(RegistrationSubmitDto dto, String clientIp) {
        return submit(dto, clientIp, null);
    }

    /**
     * Rejestruje użytkownika w instytucji, tworząc konto, powiązanie oczekujące i token aktywacyjny.
     */
    @Transactional
    public RegistrationSubmitResultDto submit(RegistrationSubmitDto dto, String clientIp, String userAgent) {
        Institution institution = institutionRepository.findById(dto.getInstitutionId())
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found: " + dto.getInstitutionId()));

        validateInstitution(institution);
        validateIp(institution, clientIp);
        validateConsents(dto);

        String normalizedEmail = normalizeEmail(dto.getEmail());
        validateEmailDomain(institution, normalizedEmail);
        validateEmailIsNotRegistered(normalizedEmail);

        AppUser user = userRepository.save(createUser(dto, normalizedEmail));
        InstitutionMembership membership = membershipRepository.save(createPendingMembership(user, institution));
        ActivationToken activationToken = accountActivationService.issueActivationToken(user, institution, membership, EmailType.ACCOUNT_ACTIVATION_LINK);
        accountActivationService.logRegistrationSubmit(
                activationToken,
                dto.isTermsAndPrivacyPolicyAccepted(),
                dto.isPersonalDataProcessingAcknowledged(),
                clientIp,
                userAgent
        );

        return new RegistrationSubmitResultDto(
                user.getId(),
                institution.getId(),
                membership.getId(),
                membership.getRole(),
                RegistrationSubmitStatus.REGISTERED_PENDING_ACTIVATION
        );
    }

    private void validateInstitution(Institution institution) {
        if (!EntityState.ACTIVE.equals(institution.getState())) {
            throw new IllegalArgumentException("Institution is inactive.");
        }

        if (!institution.isAllowRegistration()) {
            throw new IllegalArgumentException("Institution registration is disabled.");
        }

        if (InstitutionType.RESEARCH_UNIT != institution.getInstitutionType()) {
            throw new IllegalArgumentException("Institution registration is not available for this institution type.");
        }
    }

    private void validateIp(Institution institution, String clientIp) {
        if (institution.isAllowOtherIp()) {
            return;
        }

        try {
            if (matcher.matchesInstitution(institution.getId(), clientIp)) {
                return;
            }
        } catch (IllegalArgumentException e) {
            // Invalid or unsupported IP is handled as not matching configured rules.
        }

        throw new IllegalArgumentException("Registration is not allowed from this IP address.");
    }

    private void validateEmailDomain(Institution institution, String normalizedEmail) {
        if (institution.isAllowOtherEmailDomain()) {
            return;
        }

        String domain = emailDomain(normalizedEmail);
        Set<InstitutionEmailDomain> allowedDomains = institution.getEmailDomains();
        boolean allowed = allowedDomains != null && allowedDomains.stream()
                .filter(emailDomain -> EntityState.ACTIVE.equals(emailDomain.getState()))
                .map(InstitutionEmailDomain::getDomain)
                .map(this::normalizeDomain)
                .anyMatch(domain::equals);

        if (!allowed) {
            throw new IllegalArgumentException("Registration is not allowed for this e-mail domain.");
        }
    }

    private void validateEmailIsNotRegistered(String normalizedEmail) {
        userRepository.findByEmail(normalizedEmail)
                .ifPresent(user -> {
                    throw new ResourceAlreadyExistsException("email", EXISTING_ACCOUNT_MESSAGE);
                });
    }

    private void validateConsents(RegistrationSubmitDto dto) {
        if (!dto.isTermsAndPrivacyPolicyAccepted()) {
            throw new IllegalArgumentException("Terms and privacy policy acceptance is required.");
        }

        if (!dto.isPersonalDataProcessingAcknowledged()) {
            throw new IllegalArgumentException("Personal data processing acknowledgement is required.");
        }
    }

    private AppUser createUser(RegistrationSubmitDto dto, String normalizedEmail) {
        AppUser user = new AppUser();
        user.setEmail(normalizedEmail);
        user.setFirstName(normalizeRequired(dto.getFirstName()));
        user.setLastName(normalizeRequired(dto.getLastName()));
        user.setOrcid(normalizeNullable(dto.getOrcid()));
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setRoles("USER");
        user.setState(EntityState.ACTIVE);
        user.setCreatedAt(OffsetDateTime.now());
        return user;
    }

    private InstitutionMembership createPendingMembership(AppUser user, Institution institution) {
        InstitutionMembership membership = new InstitutionMembership();
        membership.setUser(user);
        membership.setInstitution(institution);
        membership.setRole(InstitutionRole.INSTITUTION_PENDING);
        membership.setLoginAllowedFromAnyIp(false);
        membership.setState(EntityState.ACTIVE);
        membership.setCreatedAt(OffsetDateTime.now());
        return membership;
    }

    private String normalizeEmail(String email) {
        return normalizeRequired(email).toLowerCase(Locale.ROOT);
    }

    private String emailDomain(String email) {
        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            throw new IllegalArgumentException("Invalid e-mail address.");
        }

        return normalizeDomain(email.substring(atIndex + 1));
    }

    private String normalizeDomain(String domain) {
        return normalizeRequired(domain).toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value is required.");
        }

        return value.trim();
    }

    private String normalizeNullable(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isBlank() ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
