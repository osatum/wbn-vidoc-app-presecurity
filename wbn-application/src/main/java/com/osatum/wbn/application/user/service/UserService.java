package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.institution.authorization.CanInstitutionManage;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import com.osatum.wbn.application.user.authorization.CanUserManage;
import com.osatum.wbn.application.user.dto.UserBasicDataDto;
import com.osatum.wbn.application.user.dto.UserCreateDto;
import com.osatum.wbn.application.user.dto.UserCreateResultDto;
import com.osatum.wbn.application.user.dto.UserCreateStatus;
import com.osatum.wbn.application.user.dto.UserListViewDto;
import com.osatum.wbn.application.user.dto.UserPasswordDto;
import com.osatum.wbn.application.user.dto.UserStateChangeResultDto;
import com.osatum.wbn.application.user.dto.UserStateChangeStatus;
import com.osatum.wbn.application.user.dto.UserViewDto;
import com.osatum.wbn.application.user.mapper.UserMapper;
import com.osatum.wbn.application.user.query.UserListQuery;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.repository.user.UserPersonalDataDeletionRepository;
import com.osatum.wbn.domain.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserListQueryHandler queryHandler;
    private final UserRepository repository;
    private final InstitutionRepository institutionRepository;
    private final InstitutionMembershipRepository membershipRepository;
    private final UserMapper mapper;
    private final UserUseCaseValidator useCaseValidator;
    private final PasswordEncoder passwordEncoder;
    private final UserPersonalDataDeletionRepository personalDataDeletionRepository;

    /**
     * Zwraca stronicowaną listę użytkowników zgodnie z kryteriami wyszukiwania.
     */
    @CanUserManage
    public PageResult<UserListViewDto> findUsers(UserListQuery query) {
        return queryHandler.execute(query);
    }

    @CanUserManage
    public UserListQuery getDefaultUserListQuery() {
        UserListQuery query = new UserListQuery();
        query.setPage(0);
        query.setSize(10);
        query.setFirst(0);
        return query;
    }

    /**
     * Zwraca stronicowaną listę użytkowników przypisanych do wskazanej instytucji.
     */
    @CanUserManage
    public PageResult<UserListViewDto> findUsersByInstitution(Long institutionId, UserListQuery query) {
        query.setInstitutionId(institutionId);
        return queryHandler.execute(query);
    }

    /**
     * Pobiera szczegółowe dane użytkownika i mapuje je do widoku aplikacyjnego.
     */
    @CanUserManage
    @Transactional
    public UserViewDto findUser(Long id) {
        Optional<AppUser> oUser = repository.findById(id);
        if (oUser.isEmpty()) {
            throw new IllegalArgumentException("Invalid user id = " + id);
        }
        return mapper.viewDto(oUser.get());
    }

    /**
     * Aktualizuje podstawowe dane użytkownika w trybie administracyjnym.
     */
    @CanUserManage
    @Transactional
    public UserViewDto updateBasicData(Long id, UserBasicDataDto dto) {
        AppUser user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        mapper.updateBasicData(dto, user);
        return mapper.viewDto(repository.save(user));
    }

    /**
     * Ustawia nowe hasło użytkownika w trybie administracyjnym po sprawdzeniu powtórzenia hasła.
     */
    @CanInstitutionManage
    @Transactional
    public UserViewDto updatePassword(Long id, UserPasswordDto dto) {
        AppUser user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        if (!Objects.equals(dto.getPassword(), dto.getPasswordRepeat())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        return mapper.viewDto(repository.save(user));
    }

    /**
     * Przygotowuje domyślne wartości i listę ról dla formularza tworzenia użytkownika w instytucji.
     */
    @CanUserManage
    @Transactional(readOnly = true)
    public UserCreateDto prepareCreateUserForm(Long institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found: " + institutionId));
        List<InstitutionRole> roleOptions = allowedRoles(institution);

        UserCreateDto dto = new UserCreateDto();
        dto.setInstitutionId(institutionId);
        dto.setRole(defaultRole(roleOptions));
        dto.setRoleOptions(roleOptions);
        return dto;
    }

    /**
     * Tworzy nowego użytkownika albo dołącza istniejące konto do instytucji zgodnie z decyzją formularza.
     */
    @CanUserManage
    @Transactional
    public UserCreateResultDto createUser(UserCreateDto dto) {
        Institution institution = institutionRepository.findById(dto.getInstitutionId())
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found: " + dto.getInstitutionId()));

        Optional<AppUser> existingUser = useCaseValidator.findExistingUser(dto.getEmail());

        InstitutionRole role = resolveRole(institution, dto.getRole());

        if (existingUser.isPresent()) {
            AppUser user = existingUser.get();

            UserCreateStatus status = resolveExistingUserStatus(user, institution.getId(), role);
            if (UserCreateStatus.USER_ALREADY_ASSIGNED_SELECTED_ROLE.equals(status)) {
                return mapper.createResult(status, ensureApplicationRoles(user, institution), null);
            }

            if (!dto.isAttachExistingUser()) {
                return mapper.createResult(status, user, null);
            }

            user = ensureApplicationRoles(user, institution);
            InstitutionMembership membership = createMembership(user, institution, role);
            return mapper.createResult(UserCreateStatus.EXISTING_USER_ATTACHED, user, membership);
        }

        AppUser user = createNewUser(dto, institution);
        InstitutionMembership membership = createMembership(user, institution, role);

        return mapper.createResult(UserCreateStatus.CREATED, user, membership);
    }

    /**
     * Przygotowuje dane potrzebne do aktywacji użytkownika i wyboru aktywowanych powiązań.
     */
    @CanUserManage
    @Transactional(readOnly = true)
    public UserStateChangeResultDto prepareActivationForm(Long id) {
        AppUser user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        List<InstitutionMembership> memberships = membershipRepository.findByUserId(user.getId());
        List<InstitutionMembership> activeInstitutionMemberships = memberships.stream()
                .filter(this::isActiveInstitutionMembership)
                .toList();

        if (activeInstitutionMemberships.isEmpty()) {
            return mapper.stateChangeResult(UserStateChangeStatus.INACTIVE_INSTITUTION, user, List.of());
        }

        return mapper.stateChangeResult(
                UserStateChangeStatus.MEMBERSHIP_SELECTION_REQUIRED,
                user,
                activeInstitutionMemberships
        );
    }

    /**
     * Zmienia stan użytkownika oraz powiązanych członkostw zgodnie z regułami aktywacji i dezaktywacji.
     */
    @CanUserManage
    @Transactional
    public UserStateChangeResultDto changeState(Long id, EntityState state, List<Long> membershipIds) {
        AppUser user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        if (EntityState.INACTIVE.equals(state)) {
            user.setState(EntityState.INACTIVE);
            membershipRepository.findByUserId(user.getId())
                    .forEach(membership -> membership.setState(EntityState.INACTIVE));

            return mapper.stateChangeResult(UserStateChangeStatus.CHANGED, repository.save(user), List.of());
        }

        if (EntityState.ACTIVE.equals(state)) {
            return activateUser(user, membershipIds);
        }

        user.setState(state);

        return mapper.stateChangeResult(UserStateChangeStatus.CHANGED, repository.save(user), List.of());
    }

    /**
     * Uruchamia pełne usunięcie danych użytkownika zgodnie z procesem RODO dla operatora systemu.
     */
    @CanUserManage
    @Transactional
    public void deleteForGdpr(Long id) {
        deleteForGdprAfterVerification(id);
    }

    /**
     * Usuwa konto użytkownika po potwierdzeniu procesu RODO oraz anonimizuje powiązane logi i zgłoszenia.
     */
    @Transactional
    public void deleteForGdprAfterVerification(Long id) {
        AppUser user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        personalDataDeletionRepository.deleteRefreshTokensForUser(user.getId());
        personalDataDeletionRepository.anonymizeAccountActivationLogsForUser(user.getId());
        personalDataDeletionRepository.anonymizePasswordResetRequestsForUser(user.getId(), user.getEmail());
        personalDataDeletionRepository.anonymizeUserDataDeletionRequestsForUser(user.getId(), user.getEmail());
        personalDataDeletionRepository.anonymizeEmailMessageLogsForUser(user.getId(), user.getEmail());
        personalDataDeletionRepository.anonymizeNavigationAuditVisitsForUser(user.getId());
        personalDataDeletionRepository.anonymizeLoginSessionsForUser(user.getId(), user.getEmail());
        personalDataDeletionRepository.anonymizeNavigationAuditEventsForUser(user.getId());
        personalDataDeletionRepository.deleteActivationTokensForUser(user.getId());
        personalDataDeletionRepository.clearMembershipActivatedByUser(user.getId());
        personalDataDeletionRepository.deleteMembershipsForUser(user.getId());
        repository.delete(user);
    }

    private AppUser createNewUser(UserCreateDto dto, Institution institution) {
        AppUser user = mapper.createEntity(
                dto,
                useCaseValidator.normalizeEmail(dto.getEmail()),
                passwordEncoder.encode(dto.getPassword()),
                applicationRoles(institution)
        );
        return repository.save(user);
    }

    private InstitutionRole resolveRole(Institution institution, InstitutionRole requestedRole) {
        List<InstitutionRole> allowedRoles = allowedRoles(institution);
        InstitutionRole role = requestedRole == null ? defaultRole(allowedRoles) : requestedRole;
        if (!allowedRoles.contains(role)) {
            throw new IllegalArgumentException("Role is not allowed for institution type: " + institution.getInstitutionType());
        }
        return role;
    }

    private InstitutionRole defaultRole(List<InstitutionRole> allowedRoles) {
        if (allowedRoles.isEmpty()) {
            throw new IllegalArgumentException("No roles available for institution type.");
        }
        return allowedRoles.getFirst();
    }

    private List<InstitutionRole> allowedRoles(Institution institution) {
        if (InstitutionType.SYSTEM_OPERATOR.equals(institution.getInstitutionType())) {
            return List.of(InstitutionRole.INSTITUTION_ADMIN);
        }
        if (InstitutionType.RESEARCH_UNIT.equals(institution.getInstitutionType())) {
            return List.of(InstitutionRole.INSTITUTION_USER);
        }
        return List.of();
    }

    private String applicationRoles(Institution institution) {
        if (InstitutionType.SYSTEM_OPERATOR.equals(institution.getInstitutionType())) {
            return "ADMIN";
        }
        return "USER";
    }

    private AppUser ensureApplicationRoles(AppUser user, Institution institution) {
        String requiredRole = applicationRoles(institution);
        if (hasApplicationRole(user, requiredRole)) {
            return user;
        }

        String roles = user.getRoles();
        user.setRoles(roles == null || roles.isBlank() ? requiredRole : roles + "," + requiredRole);
        return repository.save(user);
    }

    private boolean hasApplicationRole(AppUser user, String role) {
        String roles = user.getRoles();
        if (roles == null || roles.isBlank()) {
            return false;
        }
        return List.of(roles.split(",")).stream()
                .map(String::trim)
                .anyMatch(role::equals);
    }

    private InstitutionMembership createMembership(AppUser user, Institution institution, InstitutionRole role) {
        return membershipRepository.save(mapper.createMembership(user, institution, role));
    }

    private UserStateChangeResultDto activateUser(AppUser user, List<Long> membershipIds) {
        List<InstitutionMembership> memberships = membershipRepository.findByUserId(user.getId());
        List<InstitutionMembership> activeInstitutionMemberships = memberships.stream()
                .filter(this::isActiveInstitutionMembership)
                .toList();

        if (memberships.size() == 1) {
            InstitutionMembership membership = memberships.getFirst();
            if (!isActiveInstitutionMembership(membership)) {
                return mapper.stateChangeResult(UserStateChangeStatus.INACTIVE_INSTITUTION, user, List.of());
            }

            user.setState(EntityState.ACTIVE);
            membership.setState(EntityState.ACTIVE);
            return mapper.stateChangeResult(UserStateChangeStatus.CHANGED, repository.save(user), List.of());
        }

        if (activeInstitutionMemberships.isEmpty()) {
            return mapper.stateChangeResult(UserStateChangeStatus.INACTIVE_INSTITUTION, user, List.of());
        }

        if (membershipIds == null || membershipIds.isEmpty()) {
            return mapper.stateChangeResult(
                    UserStateChangeStatus.MEMBERSHIP_SELECTION_REQUIRED,
                    user,
                    activeInstitutionMemberships
            );
        }

        Set<Long> selectedIds = new HashSet<>(membershipIds);
        List<InstitutionMembership> selectedMemberships = activeInstitutionMemberships.stream()
                .filter(membership -> selectedIds.contains(membership.getId()))
                .toList();

        if (selectedMemberships.isEmpty()) {
            return mapper.stateChangeResult(
                    UserStateChangeStatus.MEMBERSHIP_SELECTION_REQUIRED,
                    user,
                    activeInstitutionMemberships
            );
        }

        user.setState(EntityState.ACTIVE);
        selectedMemberships.forEach(membership -> membership.setState(EntityState.ACTIVE));
        return mapper.stateChangeResult(UserStateChangeStatus.CHANGED, repository.save(user), List.of());
    }

    private boolean isActiveInstitutionMembership(InstitutionMembership membership) {
        return membership.getInstitution() != null
                && EntityState.ACTIVE.equals(membership.getInstitution().getState());
    }

    private UserCreateStatus resolveExistingUserStatus(AppUser user, Long institutionId, InstitutionRole role) {
        Set<InstitutionMembership> memberships = user.getMemberships();
        if (memberships == null || memberships.isEmpty()) {
            return UserCreateStatus.USER_EXISTS;
        }

        boolean assignedToSelectedInstitutionInSelectedRole = memberships.stream()
                .anyMatch(membership -> isActiveMembership(membership)
                        && isSelectedInstitution(membership, institutionId)
                        && role.equals(membership.getRole()));
        if (assignedToSelectedInstitutionInSelectedRole) {
            return UserCreateStatus.USER_ALREADY_ASSIGNED_SELECTED_ROLE;
        }

        boolean assignedToSelectedInstitution = memberships.stream()
                .anyMatch(membership -> isActiveMembership(membership)
                        && isSelectedInstitution(membership, institutionId));
        if (assignedToSelectedInstitution) {
            return UserCreateStatus.USER_ASSIGNED_SELECTED_INSTITUTION_OTHER_ROLE;
        }

        boolean assignedToOtherInstitution = memberships.stream()
                .anyMatch(membership -> isActiveMembership(membership)
                        && !isSelectedInstitution(membership, institutionId));
        if (assignedToOtherInstitution) {
            return UserCreateStatus.USER_ASSIGNED_OTHER_INSTITUTION;
        }

        return UserCreateStatus.USER_EXISTS;
    }

    private boolean isActiveMembership(InstitutionMembership membership) {
        return EntityState.ACTIVE.equals(membership.getState());
    }

    private boolean isSelectedInstitution(InstitutionMembership membership, Long institutionId) {
        return membership.getInstitution() != null
                && Objects.equals(membership.getInstitution().getId(), institutionId);
    }
}
