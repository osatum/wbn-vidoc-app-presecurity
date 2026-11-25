package com.osatum.wbn.application.institution.service;

import com.osatum.wbn.application.institution.authorization.CanInstitutionManage;
import com.osatum.wbn.application.auth.service.IpAccessSessionRevocationService;
import com.osatum.wbn.application.institution.dto.InstitutionCreateDto;
import com.osatum.wbn.application.institution.dto.InstitutionListViewDto;
import com.osatum.wbn.application.institution.dto.InstitutionMembershipListDto;
import com.osatum.wbn.application.institution.dto.InstitutionMembershipStateChangeResultDto;
import com.osatum.wbn.application.institution.dto.InstitutionMembershipStateChangeStatus;
import com.osatum.wbn.application.institution.dto.InstitutionStateChangeResultDto;
import com.osatum.wbn.application.institution.dto.InstitutionStateChangeStatus;
import com.osatum.wbn.application.institution.dto.InstitutionViewDto;
import com.osatum.wbn.application.institution.mapper.InstitutionMapper;
import com.osatum.wbn.application.institution.mapper.InstitutionMembershipMapper;
import com.osatum.wbn.application.institution.query.InstitutionMembershipListQuery;
import com.osatum.wbn.application.institution.query.InstitutionListQuery;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final InstitutionListQueryHandler queryHandler;
    private final InstitutionMembershipListQueryHandler membershipListQueryHandler;
    private final InstitutionMapper mapper;
    private final InstitutionMembershipMapper membershipMapper;
    private final InstitutionRepository repository;
    private final InstitutionMembershipRepository membershipRepository;
    private final InstitutionUseCaseValidator useCaseValidator;
    private final IpAccessSessionRevocationService ipAccessSessionRevocationService;

    /**
     * Zwraca stronicowaną listę instytucji zgodnie z kryteriami wyszukiwania.
     */
    @CanInstitutionManage
    public PageResult<InstitutionListViewDto> getInstitutions(InstitutionListQuery query) {
        return queryHandler.execute(query);
    }

    @CanInstitutionManage
    public InstitutionListQuery getDefaultInstitutionListQuery() {
        InstitutionListQuery query = new InstitutionListQuery();
        query.setPage(0);
        query.setSize(10);
        query.setFirst(0);
        return query;
    }

    /**
     * Zwraca stronicowaną listę powiązań użytkowników ze wskazaną instytucją.
     */
    @CanInstitutionManage
    public PageResult<InstitutionMembershipListDto> getInstitutionMemberships(Long institutionId, InstitutionMembershipListQuery query) {
        query.setInstitutionId(institutionId);
        return membershipListQueryHandler.execute(query);
    }

    /**
     * Pobiera szczegóły instytucji i mapuje encję domenową do widoku aplikacyjnego.
     */
    @CanInstitutionManage
    @Transactional
    public InstitutionViewDto findInstitution(Long id) {
        return mapper.toViewDto(getInstitution(id));
    }

    /**
     * Tworzy instytucję po walidacji unikalności i spójności danych wejściowych.
     */
    @CanInstitutionManage
    @Transactional
    public InstitutionViewDto createInstitution(InstitutionCreateDto dto) {
        useCaseValidator.validateCreate(dto);

        Institution institution = mapper.toEntity(dto);
        Institution saved = repository.save(institution);

        return mapper.toViewDto(saved);
    }

    /**
     * Aktualizuje dane instytucji po walidacji reguł biznesowych dla istniejącego rekordu.
     */
    @CanInstitutionManage
    @Transactional
    public InstitutionViewDto updateInstitution(Long id, InstitutionCreateDto dto) {
        Institution institution = getInstitution(id);
        useCaseValidator.validateUpdate(id, dto);

        Set<String> previousIpRules = ipRuleKeys(institution);

        mapper.updateEntity(dto, institution);

        Institution saved = repository.save(institution);
        if (!previousIpRules.equals(ipRuleKeys(saved))) {
            ipAccessSessionRevocationService.revokeActiveSessionsForInstitution(saved.getId());
        }
        return mapper.toViewDto(saved);
    }

    /**
     * Przygotowuje dane wymagane do aktywacji instytucji i wyboru powiązań użytkowników.
     */
    @CanInstitutionManage
    @Transactional(readOnly = true)
    public InstitutionStateChangeResultDto prepareActivationForm(Long id) {
        Institution institution = getInstitution(id);
        List<InstitutionMembership> memberships = membershipRepository.findByInstitutionId(institution.getId());

        return stateChangeResult(
                InstitutionStateChangeStatus.MEMBERSHIP_SELECTION_REQUIRED,
                institution,
                memberships
        );
    }

    private Set<String> ipRuleKeys(Institution institution) {
        if (institution.getInstitutionIpRules() == null) {
            return Set.of();
        }
        return institution.getInstitutionIpRules().stream()
                .map(rule -> rule.getRuleType() + ":" + rule.getAddressFamily() + ":" + rule.getRuleRaw())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Zmienia stan instytucji oraz powiązanych użytkowników i członkostw zgodnie z regułami aktywacji lub dezaktywacji.
     */
    @CanInstitutionManage
    @Transactional
    public InstitutionStateChangeResultDto changeState(Long id, EntityState state, List<Long> membershipIds) {
        Institution institution = getInstitution(id);

        if (EntityState.INACTIVE.equals(state)) {
            return deactivateInstitution(institution);
        }

        if (EntityState.ACTIVE.equals(state)) {
            return activateInstitution(institution, membershipIds);
        }

        institution.setState(state);
        return stateChangeResult(InstitutionStateChangeStatus.CHANGED, repository.save(institution), List.of());
    }

    /**
     * Zmienia stan pojedynczego powiązania użytkownika z instytucją i w razie potrzeby aktualizuje stan użytkownika.
     */
    @CanInstitutionManage
    @Transactional
    public InstitutionMembershipStateChangeResultDto changeMembershipState(Long institutionId, Long membershipId, EntityState state) {
        InstitutionMembership membership = getInstitutionMembership(institutionId, membershipId);

        if (EntityState.INACTIVE.equals(state)) {
            return deactivateMembership(membership);
        }

        if (EntityState.ACTIVE.equals(state)) {
            return activateMembership(membership);
        }

        membership.setState(state);
        return membershipStateChangeResult(InstitutionMembershipStateChangeStatus.MEMBERSHIP_DEACTIVATED, membershipRepository.save(membership));
    }

    /**
     * Potwierdza oczekujące powiązanie użytkownika z instytucją, nadając mu rolę użytkownika instytucji.
     */
    @CanInstitutionManage
    @Transactional
    public InstitutionMembershipStateChangeResultDto confirmMembership(Long institutionId, Long membershipId) {
        InstitutionMembership membership = getInstitutionMembership(institutionId, membershipId);
        validateMembershipCanBeConfirmed(membership);

        membership.setRole(InstitutionRole.INSTITUTION_USER);
        return membershipStateChangeResult(
                InstitutionMembershipStateChangeStatus.MEMBERSHIP_CONFIRMED,
                membershipRepository.save(membership)
        );
    }

    private Institution getInstitution(Long id) {
        Optional<Institution> oInstitution = repository.findById(id);
        if (oInstitution.isEmpty()) {
            throw new ResourceNotFoundException("Institution not found: " + id);
        }
        return oInstitution.get();
    }

    private InstitutionMembership getInstitutionMembership(Long institutionId, Long membershipId) {
        InstitutionMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution membership not found: " + membershipId));

        Long membershipInstitutionId = membership.getInstitution().getId();
        if (!institutionId.equals(membershipInstitutionId)) {
            throw new ResourceNotFoundException("Institution membership not found: " + membershipId);
        }

        return membership;
    }

    private void validateMembershipCanBeConfirmed(InstitutionMembership membership) {
        if (!InstitutionRole.INSTITUTION_TO_CONFIRM.equals(membership.getRole())) {
            throw new IllegalArgumentException("Institution membership is not waiting for confirmation.");
        }

        if (!EntityState.ACTIVE.equals(membership.getState())
                || membership.getUser() == null
                || !EntityState.ACTIVE.equals(membership.getUser().getState())
                || membership.getInstitution() == null
                || !EntityState.ACTIVE.equals(membership.getInstitution().getState())) {
            throw new IllegalArgumentException("Institution membership cannot be confirmed.");
        }
    }

    private InstitutionStateChangeResultDto deactivateInstitution(Institution institution) {
        List<InstitutionMembership> memberships = membershipRepository.findByInstitutionId(institution.getId());
        institution.setState(EntityState.INACTIVE);

        memberships.forEach(membership -> membership.setState(EntityState.INACTIVE));
        memberships.stream()
                .map(InstitutionMembership::getUser)
                .distinct()
                .filter(user -> !hasActiveMembershipInActiveInstitution(user))
                .forEach(user -> user.setState(EntityState.INACTIVE));

        return stateChangeResult(InstitutionStateChangeStatus.CHANGED, repository.save(institution), List.of());
    }

    private InstitutionStateChangeResultDto activateInstitution(Institution institution, List<Long> membershipIds) {
        List<InstitutionMembership> memberships = membershipRepository.findByInstitutionId(institution.getId());

        if (!memberships.isEmpty() && (membershipIds == null || membershipIds.isEmpty())) {
            return stateChangeResult(
                    InstitutionStateChangeStatus.MEMBERSHIP_SELECTION_REQUIRED,
                    institution,
                    memberships
            );
        }

        institution.setState(EntityState.ACTIVE);

        if (membershipIds != null && !membershipIds.isEmpty()) {
            Set<Long> selectedIds = new HashSet<>(membershipIds);
            memberships.stream()
                    .filter(membership -> selectedIds.contains(membership.getId()))
                    .forEach(this::activateMembershipAndUser);
        }

        return stateChangeResult(InstitutionStateChangeStatus.CHANGED, repository.save(institution), List.of());
    }

    private void activateMembershipAndUser(InstitutionMembership membership) {
        membership.setState(EntityState.ACTIVE);
        if (membership.getUser() != null && EntityState.INACTIVE.equals(membership.getUser().getState())) {
            membership.getUser().setState(EntityState.ACTIVE);
        }
    }

    private InstitutionMembershipStateChangeResultDto deactivateMembership(InstitutionMembership membership) {
        membership.setState(EntityState.INACTIVE);

        AppUser user = membership.getUser();
        if (user != null && !hasOtherActiveMembershipInActiveInstitution(user, membership.getId())) {
            user.setState(EntityState.INACTIVE);
            return membershipStateChangeResult(
                    InstitutionMembershipStateChangeStatus.MEMBERSHIP_DEACTIVATED_USER_DEACTIVATED,
                    membershipRepository.save(membership)
            );
        }

        return membershipStateChangeResult(
                InstitutionMembershipStateChangeStatus.MEMBERSHIP_DEACTIVATED,
                membershipRepository.save(membership)
        );
    }

    private InstitutionMembershipStateChangeResultDto activateMembership(InstitutionMembership membership) {
        membership.setState(EntityState.ACTIVE);

        AppUser user = membership.getUser();
        if (user != null && EntityState.INACTIVE.equals(user.getState())) {
            user.setState(EntityState.ACTIVE);
            return membershipStateChangeResult(
                    InstitutionMembershipStateChangeStatus.MEMBERSHIP_ACTIVATED_USER_ACTIVATED,
                    membershipRepository.save(membership)
            );
        }

        return membershipStateChangeResult(
                InstitutionMembershipStateChangeStatus.MEMBERSHIP_ACTIVATED,
                membershipRepository.save(membership)
        );
    }

    private boolean hasActiveMembershipInActiveInstitution(AppUser user) {
        if (user == null || user.getId() == null) {
            return false;
        }

        return membershipRepository.findByUserId(user.getId()).stream()
                .anyMatch(membership -> EntityState.ACTIVE.equals(membership.getState())
                        && membership.getInstitution() != null
                        && EntityState.ACTIVE.equals(membership.getInstitution().getState()));
    }

    private boolean hasOtherActiveMembershipInActiveInstitution(AppUser user, Long ignoredMembershipId) {
        if (user == null || user.getId() == null) {
            return false;
        }

        return membershipRepository.findByUserId(user.getId()).stream()
                .anyMatch(membership -> !Objects.equals(membership.getId(), ignoredMembershipId)
                        && EntityState.ACTIVE.equals(membership.getState())
                        && membership.getInstitution() != null
                        && EntityState.ACTIVE.equals(membership.getInstitution().getState()));
    }

    private InstitutionStateChangeResultDto stateChangeResult(
            InstitutionStateChangeStatus status,
            Institution institution,
            List<InstitutionMembership> memberships
    ) {
        InstitutionStateChangeResultDto result = new InstitutionStateChangeResultDto();
        result.setStatus(status);
        result.setInstitution(mapper.toViewDto(institution));
        result.setMemberships(memberships == null ? List.of() : memberships.stream().map(membershipMapper::toListDto).toList());
        return result;
    }

    private InstitutionMembershipStateChangeResultDto membershipStateChangeResult(
            InstitutionMembershipStateChangeStatus status,
            InstitutionMembership membership
    ) {
        InstitutionMembershipStateChangeResultDto result = new InstitutionMembershipStateChangeResultDto();
        result.setStatus(status);
        result.setMembership(membershipMapper.toListDto(membership));
        return result;
    }

}
