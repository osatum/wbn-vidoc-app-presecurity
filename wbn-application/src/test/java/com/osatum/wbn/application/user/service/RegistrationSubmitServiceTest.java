package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.dto.RegistrationSubmitDto;
import com.osatum.wbn.application.user.dto.RegistrationSubmitStatus;
import com.osatum.wbn.application.shared.exception.ResourceAlreadyExistsException;
import com.osatum.wbn.application.user.service.AccountActivationService;
import com.osatum.wbn.application.user.service.RegistrationSubmitService;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.AuthMode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationSubmitServiceTest {
    private InstitutionRepository institutionRepository;
    private UserRepository userRepository;
    private InstitutionMembershipRepository membershipRepository;
    private InstitutionIpMatcherService matcher;
    private PasswordEncoder passwordEncoder;
    private AccountActivationService accountActivationService;
    private RegistrationSubmitService service;

    @BeforeEach
    void setUp() {
        institutionRepository = mock(InstitutionRepository.class);
        userRepository = mock(UserRepository.class);
        membershipRepository = mock(InstitutionMembershipRepository.class);
        matcher = mock(InstitutionIpMatcherService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        accountActivationService = mock(AccountActivationService.class);
        service = new RegistrationSubmitService(
                institutionRepository,
                userRepository,
                membershipRepository,
                matcher,
                passwordEncoder,
                accountActivationService
        );
    }

    @Test
    void createsPendingUserRegistration() {
        Institution institution = registrationInstitution();
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));
        when(matcher.matchesInstitution(1L, "192.0.2.10")).thenReturn(true);
        when(userRepository.findByEmail("jan@example.edu")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });
        when(membershipRepository.save(any(InstitutionMembership.class))).thenAnswer(invocation -> {
            InstitutionMembership membership = invocation.getArgument(0);
            membership.setId(20L);
            return membership;
        });
        ActivationToken activationToken = new ActivationToken();
        activationToken.setId(30L);
        when(accountActivationService.issueActivationToken(
                any(AppUser.class),
                same(institution),
                any(InstitutionMembership.class),
                eq(EmailType.ACCOUNT_ACTIVATION_LINK)
        )).thenReturn(activationToken);

        var result = service.submit(registrationDto("JAN@EXAMPLE.EDU"), "192.0.2.10", "JUnit");

        assertThat(result.getStatus()).isEqualTo(RegistrationSubmitStatus.REGISTERED_PENDING_ACTIVATION);
        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getInstitutionId()).isEqualTo(1L);
        assertThat(result.getMembershipId()).isEqualTo(20L);
        assertThat(result.getRole()).isEqualTo(InstitutionRole.INSTITUTION_PENDING);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("jan@example.edu");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("encoded");
        assertThat(userCaptor.getValue().getRoles()).isEqualTo("USER");
        assertThat(userCaptor.getValue().getOrcid()).isEqualTo("0000-0000-0000-000X");

        ArgumentCaptor<InstitutionMembership> membershipCaptor = ArgumentCaptor.forClass(InstitutionMembership.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getRole()).isEqualTo(InstitutionRole.INSTITUTION_PENDING);
        assertThat(membershipCaptor.getValue().isLoginAllowedFromAnyIp()).isFalse();
        assertThat(membershipCaptor.getValue().getState()).isEqualTo(EntityState.ACTIVE);
        verify(accountActivationService).issueActivationToken(
                userCaptor.getValue(),
                institution,
                membershipCaptor.getValue(),
                EmailType.ACCOUNT_ACTIVATION_LINK
        );
        verify(accountActivationService).logRegistrationSubmit(
                activationToken,
                true,
                true,
                "192.0.2.10",
                "JUnit"
        );
    }

    @Test
    void blocksRegistrationWhenUserAlreadyExists() {
        Institution institution = registrationInstitution();
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));
        when(matcher.matchesInstitution(1L, "192.0.2.10")).thenReturn(true);
        when(userRepository.findByEmail("jan@example.edu")).thenReturn(Optional.of(new AppUser()));

        assertThatThrownBy(() -> service.submit(registrationDto("jan@example.edu"), "192.0.2.10"))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("ma juz konto");

        verify(userRepository, never()).save(any(AppUser.class));
        verify(membershipRepository, never()).save(any(InstitutionMembership.class));
    }

    @Test
    void blocksRegistrationFromNotAllowedIp() {
        Institution institution = registrationInstitution();
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));
        when(matcher.matchesInstitution(1L, "203.0.113.10")).thenReturn(false);

        assertThatThrownBy(() -> service.submit(registrationDto("jan@example.edu"), "203.0.113.10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IP");

        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void blocksRegistrationForSystemOperator() {
        Institution institution = registrationInstitution();
        institution.setInstitutionType(InstitutionType.SYSTEM_OPERATOR);
        institution.setAllowOtherIp(true);
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));

        assertThatThrownBy(() -> service.submit(registrationDto("jan@example.edu"), "192.0.2.10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("institution type");

        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void allowsRegistrationFromOtherIpWhenInstitutionAllowsIt() {
        Institution institution = registrationInstitution();
        institution.setAllowOtherIp(true);
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));
        when(userRepository.findByEmail("jan@example.edu")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipRepository.save(any(InstitutionMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.submit(registrationDto("jan@example.edu"), "203.0.113.10");

        assertThat(result.getRole()).isEqualTo(InstitutionRole.INSTITUTION_PENDING);
        verify(matcher, never()).matchesInstitution(1L, "203.0.113.10");
    }

    @Test
    void blocksRegistrationForNotAllowedEmailDomain() {
        Institution institution = registrationInstitution();
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));
        when(matcher.matchesInstitution(1L, "192.0.2.10")).thenReturn(true);

        assertThatThrownBy(() -> service.submit(registrationDto("jan@other.edu"), "192.0.2.10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("e-mail domain");

        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void blocksRegistrationWithoutRequiredConsents() {
        Institution institution = registrationInstitution();
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));
        when(matcher.matchesInstitution(1L, "192.0.2.10")).thenReturn(true);
        RegistrationSubmitDto dto = registrationDto("jan@example.edu");
        dto.setTermsAndPrivacyPolicyAccepted(false);

        assertThatThrownBy(() -> service.submit(dto, "192.0.2.10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Terms");

        verify(userRepository, never()).save(any(AppUser.class));
        verify(membershipRepository, never()).save(any(InstitutionMembership.class));
    }

    @Test
    void allowsRegistrationFromOtherEmailDomainWhenInstitutionAllowsIt() {
        Institution institution = registrationInstitution();
        institution.setAllowOtherEmailDomain(true);
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));
        when(matcher.matchesInstitution(1L, "192.0.2.10")).thenReturn(true);
        when(userRepository.findByEmail("jan@other.edu")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipRepository.save(any(InstitutionMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.submit(registrationDto("jan@other.edu"), "192.0.2.10");

        assertThat(result.getRole()).isEqualTo(InstitutionRole.INSTITUTION_PENDING);
    }

    private RegistrationSubmitDto registrationDto(String email) {
        RegistrationSubmitDto dto = new RegistrationSubmitDto();
        dto.setInstitutionId(1L);
        dto.setEmail(email);
        dto.setFirstName("Jan");
        dto.setLastName("Kowalski");
        dto.setOrcid("0000-0000-0000-000x");
        dto.setPassword("secret123");
        dto.setTermsAndPrivacyPolicyAccepted(true);
        dto.setPersonalDataProcessingAcknowledged(true);
        return dto;
    }

    private Institution registrationInstitution() {
        Institution institution = new Institution();
        institution.setId(1L);
        institution.setName("Research Unit");
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setAuthMode(AuthMode.AUTH_REQUIRED);
        institution.setAllowRegistration(true);
        institution.setAllowOtherIp(false);
        institution.setAllowOtherEmailDomain(false);
        institution.setEmailDomains(Set.of(emailDomain(institution, "example.edu")));
        return institution;
    }

    private InstitutionEmailDomain emailDomain(Institution institution, String domain) {
        InstitutionEmailDomain emailDomain = new InstitutionEmailDomain();
        emailDomain.setInstitution(institution);
        emailDomain.setDomain(domain);
        return emailDomain;
    }
}
