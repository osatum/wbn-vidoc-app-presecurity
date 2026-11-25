package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.dto.RegistrationAvailabilityDto;
import com.osatum.wbn.application.user.dto.RegistrationAvailabilityReason;
import com.osatum.wbn.application.user.dto.RegistrationInstitutionDto;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionIpRule;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.repository.institution.InstitutionIpRuleRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.service.institution.InstitutionIpMatcherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegistrationInstitutionService {
    private final InstitutionRepository institutionRepository;
    private final InstitutionIpRuleRepository ipRuleRepository;
    private final InstitutionIpMatcherService matcher;

    /**
     * Zwraca instytucje dostępne w rejestracji ogólnej dla adresu IP klienta.
     */
    @Transactional(readOnly = true)
    public List<RegistrationInstitutionDto> findGeneralRegistrationInstitutions(String clientIp) {
        return institutionRepository.findActiveRegistrationEnabled().stream()
                .filter(this::isAvailableForRegistration)
                .filter(institution -> institution.isAllowOtherIp() || matchesInstitutionIp(institution.getId(), clientIp))
                .map(this::toDto)
                .toList();
    }

    /**
     * Zwraca instytucje dostępne do rejestracji wyłącznie na podstawie dopasowania reguł IP.
     */
    @Transactional(readOnly = true)
    public List<RegistrationInstitutionDto> findIpRegistrationInstitutions(String clientIp) {
        return ipRuleRepository.findAllEnabledWithInstitution().stream()
                .filter(rule -> matcher.matches(clientIp, rule))
                .map(InstitutionIpRule::getInstitution)
                .filter(Objects::nonNull)
                .filter(this::isAvailableForRegistration)
                .sorted(Comparator.comparing(Institution::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toMap(
                        Institution::getId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Sprawdza, czy użytkownik może rozpocząć rejestrację w wybranej instytucji z bieżącego adresu IP.
     */
    @Transactional(readOnly = true)
    public RegistrationAvailabilityDto validateRegistrationStart(Long institutionId, String clientIp) {
        return institutionRepository.findById(institutionId)
                .map(institution -> validateRegistrationStart(institution, clientIp))
                .orElseGet(() -> blocked(institutionId, RegistrationAvailabilityReason.INSTITUTION_NOT_FOUND, null));
    }

    private RegistrationAvailabilityDto validateRegistrationStart(Institution institution, String clientIp) {
        if (institution.getState() != EntityState.ACTIVE) {
            return blocked(institution.getId(), RegistrationAvailabilityReason.INSTITUTION_INACTIVE, null);
        }

        if (!institution.isAllowRegistration()) {
            return blocked(institution.getId(), RegistrationAvailabilityReason.REGISTRATION_DISABLED, null);
        }

        if (!isRegistrationInstitutionType(institution)) {
            return blocked(institution.getId(), RegistrationAvailabilityReason.REGISTRATION_DISABLED, null);
        }

        if (institution.isAllowOtherIp() || matchesInstitutionIp(institution.getId(), clientIp)) {
            return new RegistrationAvailabilityDto(
                    institution.getId(),
                    true,
                    RegistrationAvailabilityReason.AVAILABLE,
                    toDto(institution)
            );
        }

        return blocked(institution.getId(), RegistrationAvailabilityReason.IP_NOT_ALLOWED, toDto(institution));
    }

    private boolean matchesInstitutionIp(Long institutionId, String clientIp) {
        try {
            return matcher.matchesInstitution(institutionId, clientIp);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isAvailableForRegistration(Institution institution) {
        return institution.getState() == EntityState.ACTIVE
                && institution.isAllowRegistration()
                && isRegistrationInstitutionType(institution);
    }

    private boolean isRegistrationInstitutionType(Institution institution) {
        return InstitutionType.RESEARCH_UNIT == institution.getInstitutionType();
    }

    private RegistrationAvailabilityDto blocked(
            Long institutionId,
            RegistrationAvailabilityReason reason,
            RegistrationInstitutionDto institution
    ) {
        return new RegistrationAvailabilityDto(institutionId, false, reason, institution);
    }

    private RegistrationInstitutionDto toDto(Institution institution) {
        return new RegistrationInstitutionDto(
                institution.getId(),
                institution.getName(),
                institution.getDescription(),
                institution.getInstitutionType(),
                institution.isAllowOtherIp(),
                institution.isAllowOtherEmailDomain()
        );
    }
}
