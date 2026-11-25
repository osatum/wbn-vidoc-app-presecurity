package com.osatum.wbn.application.institution.service;


import com.osatum.wbn.application.institution.assembler.simple.InstitutionsDetectionDtoAssembler;
import com.osatum.wbn.application.institution.assembler.simple.InstitutionsDetectionDtoParameters;
import com.osatum.wbn.application.institution.dto.InstitutionAccessDto;
import com.osatum.wbn.application.institution.dto.InstitutionsDetectionDto;
import com.osatum.wbn.application.institution.mapper.InstitutionAccessDtoMapper;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionIpRule;
import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.repository.institution.InstitutionIpRuleRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.service.institution.InstitutionIpMatcherService;
import com.osatum.wbn.domain.service.net.IpAddressRuleParser;
import com.osatum.wbn.domain.service.net.ParsedIpAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InstitutionDetectionService {

    private static final String IPV4_LOOPBACK = "127.0.0.1";
    private static final String IPV6_LOOPBACK = "0:0:0:0:0:0:0:1";

    private final InstitutionIpRuleRepository ipRuleRepository;
    private final InstitutionRepository institutionRepository;
    private final InstitutionIpMatcherService matcher;
    private final InstitutionAccessDtoMapper institutionAccessDtoMapper ;
    private final InstitutionsDetectionDtoAssembler institutionsDetectionDtoAssembler = new InstitutionsDetectionDtoAssembler();

    /**
     * Wykrywa instytucje dostępne dla adresu IP klienta i zwraca wynik używany na stronie startowej.
     */
    @Transactional(readOnly = true)
    public InstitutionsDetectionDto findInstitutionsForIp(String clientIp) {
        // Najprościej: pobierz wszystkie reguły (dla małej/średniej skali ok).
        // Docelowo: optymalizacja przez pre-normalizowane zakresy ipFrom/ipTo.
        List<InstitutionIpRule> rules = ipRuleRepository.findAllEnabledWithInstitution();
        List<String> clientIps = clientIpsForDetection(clientIp);

        // przfiltruj pasujace
        List<Long> filtered = rules.stream()
                .filter(rule -> clientIps.stream().anyMatch(ip -> matcher.matches(ip, rule)))
                .filter(x -> x.getInstitution() != null)
                .map(x -> x.getInstitution().getId())
                .distinct()
                .toList();


        if (filtered.isEmpty()) institutionsDetectionDtoAssembler.toDto(
                new InstitutionsDetectionDtoParameters(new ArrayList<>(), clientIp)
        );

        List<InstitutionAccessDto> dtos = filtered.stream().map(x -> {
                    Optional<Institution> oInstitution = institutionRepository.findById(x);
                    if (oInstitution.isPresent()) {
                        return institutionAccessDtoMapper.toDto(oInstitution.get());
                    } else {
                        return null;
                    }
                }).filter(x -> x != null)
                .sorted(Comparator.comparing(InstitutionAccessDto::getName))
                .toList();

        List<Institution> institutionsWithUsers = institutionRepository.findActiveWithUsersInIds(
                dtos.stream().map(InstitutionAccessDto::getId).toList()
        );

        dtos.forEach(x-> {
            x.setHasUsers(institutionsWithUsers.stream().anyMatch(i-> i.getId().equals(x.getId())));
        });

        InstitutionsDetectionDtoParameters institutionsDetectionDtoParameters = new InstitutionsDetectionDtoParameters(dtos, clientIp);
        return institutionsDetectionDtoAssembler.toDto(institutionsDetectionDtoParameters);
    }

    private List<String> clientIpsForDetection(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return List.of();
        }

        try {
            ParsedIpAddress address = IpAddressRuleParser.parseClientAddress(clientIp);
            if (isIpv4Loopback(address)) {
                return List.of(IPV4_LOOPBACK, IPV6_LOOPBACK);
            }
            if (isIpv6Loopback(address)) {
                return List.of(IPV6_LOOPBACK, IPV4_LOOPBACK);
            }
            return List.of(address.canonicalValue());
        } catch (IllegalArgumentException exception) {
            return List.of(clientIp);
        }
    }

    private boolean isIpv4Loopback(ParsedIpAddress address) {
        return address.addressFamily() == IpAddressFamily.IPV4
                && IPV4_LOOPBACK.equals(address.canonicalValue());
    }

    private boolean isIpv6Loopback(ParsedIpAddress address) {
        return address.addressFamily() == IpAddressFamily.IPV6
                && IPV6_LOOPBACK.equals(address.canonicalValue());
    }


}
