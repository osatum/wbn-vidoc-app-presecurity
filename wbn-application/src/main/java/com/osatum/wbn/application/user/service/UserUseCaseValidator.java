package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.shared.exception.ResourceAlreadyExistsException;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import com.osatum.wbn.domain.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class UserUseCaseValidator {
    private final UserRepository userRepository;
    private final InstitutionMembershipRepository membershipRepository;

    Optional<AppUser> findExistingUser(String email) {
        return userRepository.findByEmail(normalizeEmail(email));
    }

    void validateUserCanBeAssigned(AppUser user, Long institutionId, InstitutionRole role) {
        Optional<InstitutionMembership> membership = membershipRepository.findByUserIdAndInstitutionIdAndRole(user.getId(), institutionId, role);
        if (membership.isPresent() && EntityState.ACTIVE.equals(membership.get().getState())) {
            throw new ResourceAlreadyExistsException("email", "Uzytkownik ma juz taka role w tej instytucji");
        }
    }
    String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
