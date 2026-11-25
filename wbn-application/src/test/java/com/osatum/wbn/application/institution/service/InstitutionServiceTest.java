package com.osatum.wbn.application.institution.service;

import com.osatum.wbn.application.institution.dto.InstitutionMembershipListDto;
import com.osatum.wbn.application.institution.dto.InstitutionMembershipStateChangeStatus;
import com.osatum.wbn.application.institution.mapper.InstitutionMapper;
import com.osatum.wbn.application.institution.mapper.InstitutionMembershipMapper;
import com.osatum.wbn.application.institution.query.InstitutionListQuery;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstitutionServiceTest {
    private InstitutionMembershipRepository membershipRepository;
    private InstitutionMembershipMapper membershipMapper;
    private InstitutionService service;

    @BeforeEach
    void setUp() {
        membershipRepository = mock(InstitutionMembershipRepository.class);
        membershipMapper = mock(InstitutionMembershipMapper.class);
        service = new InstitutionService(
                mock(InstitutionListQueryHandler.class),
                mock(InstitutionMembershipListQueryHandler.class),
                mock(InstitutionMapper.class),
                membershipMapper,
                mock(InstitutionRepository.class),
                membershipRepository,
                mock(InstitutionUseCaseValidator.class),
                mock(com.osatum.wbn.application.auth.service.IpAccessSessionRevocationService.class)
        );
    }

    @Test
    void returnsDefaultInstitutionListQuery() {
        InstitutionListQuery query = service.getDefaultInstitutionListQuery();

        assertThat(query.getPage()).isZero();
        assertThat(query.getSize()).isEqualTo(10);
        assertThat(query.getFirst()).isZero();
        assertThat(query.getSortBy()).isEqualTo(InstitutionListQuery.SortBy.SORT_BY_NAME_ASC);
    }

    @Test
    void confirmsMembershipWaitingForAdministrator() {
        InstitutionMembership membership = membership(20L, 1L, InstitutionRole.INSTITUTION_TO_CONFIRM);
        when(membershipRepository.findById(20L)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any(InstitutionMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipMapper.toListDto(any(InstitutionMembership.class))).thenAnswer(invocation -> {
            InstitutionMembership saved = invocation.getArgument(0);
            InstitutionMembershipListDto dto = new InstitutionMembershipListDto();
            dto.setId(saved.getId());
            dto.setRole(saved.getRole());
            return dto;
        });

        var result = service.confirmMembership(1L, 20L);

        assertThat(result.getStatus()).isEqualTo(InstitutionMembershipStateChangeStatus.MEMBERSHIP_CONFIRMED);
        assertThat(result.getMembership().getRole()).isEqualTo(InstitutionRole.INSTITUTION_USER);

        ArgumentCaptor<InstitutionMembership> membershipCaptor = ArgumentCaptor.forClass(InstitutionMembership.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getRole()).isEqualTo(InstitutionRole.INSTITUTION_USER);
    }

    @Test
    void rejectsMembershipThatIsNotWaitingForAdministrator() {
        InstitutionMembership membership = membership(20L, 1L, InstitutionRole.INSTITUTION_USER);
        when(membershipRepository.findById(20L)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> service.confirmMembership(1L, 20L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not waiting for confirmation");

        verify(membershipRepository, never()).save(any(InstitutionMembership.class));
    }

    @Test
    void rejectsMembershipFromOtherInstitution() {
        InstitutionMembership membership = membership(20L, 2L, InstitutionRole.INSTITUTION_TO_CONFIRM);
        when(membershipRepository.findById(20L)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> service.confirmMembership(1L, 20L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(membershipRepository, never()).save(any(InstitutionMembership.class));
    }

    private InstitutionMembership membership(Long membershipId, Long institutionId, InstitutionRole role) {
        Institution institution = new Institution();
        institution.setId(institutionId);
        institution.setState(EntityState.ACTIVE);

        AppUser user = new AppUser();
        user.setId(10L);
        user.setState(EntityState.ACTIVE);

        InstitutionMembership membership = new InstitutionMembership();
        membership.setId(membershipId);
        membership.setInstitution(institution);
        membership.setUser(user);
        membership.setRole(role);
        membership.setState(EntityState.ACTIVE);
        return membership;
    }
}
