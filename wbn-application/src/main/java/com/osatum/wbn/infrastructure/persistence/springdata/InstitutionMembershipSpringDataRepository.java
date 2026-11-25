package com.osatum.wbn.infrastructure.persistence.springdata;


import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface InstitutionMembershipSpringDataRepository extends JpaRepository<InstitutionMembership, Long>, JpaSpecificationExecutor<InstitutionMembership> {
    Optional<InstitutionMembership> findByUserIdAndInstitutionId(Long userId, Long institutionId);

    Optional<InstitutionMembership> findByUserIdAndInstitutionIdAndRole(Long userId, Long institutionId, InstitutionRole role);

    List<InstitutionMembership> findByUserId(Long userId);

    List<InstitutionMembership> findByInstitutionId(Long institutionId);

    List<InstitutionMembership> role(InstitutionRole role);

    long countByState(EntityState state);

    @Query("""
        select count(distinct m.user.id)
        from InstitutionMembership m
        where m.state = com.osatum.wbn.domain.model.EntityState.ACTIVE
            and m.user.state = com.osatum.wbn.domain.model.EntityState.ACTIVE
            and m.institution.state = com.osatum.wbn.domain.model.EntityState.ACTIVE
            and (
                select count(distinct m2.institution.id)
                from InstitutionMembership m2
                where m2.state = com.osatum.wbn.domain.model.EntityState.ACTIVE
                    and m2.institution.state = com.osatum.wbn.domain.model.EntityState.ACTIVE
                    and m2.user.id = m.user.id
            ) > 1
    """)
    long countUsersAssignedToMultipleInstitutions();
}
