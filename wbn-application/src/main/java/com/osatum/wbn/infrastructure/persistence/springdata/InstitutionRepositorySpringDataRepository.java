package com.osatum.wbn.infrastructure.persistence.springdata;


import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstitutionRepositorySpringDataRepository extends JpaRepository<Institution, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select institution
            from Institution institution
            where institution.id = :id
            """)
    Optional<Institution> findByIdForUpdate(@Param("id") Long id);

    boolean existsByStateAndNameIgnoreCase(EntityState state, String name);

    boolean existsByStateAndNameIgnoreCaseAndIdNot(EntityState state, String name, Long id);

    long countByState(EntityState state);

    long countByStateAndInstitutionType(EntityState state, InstitutionType institutionType);

    List<Institution> findByStateOrderByNameAsc(EntityState state, Pageable pageable);

    List<Institution> findByStateAndAllowRegistrationTrueOrderByNameAsc(EntityState state);

    @Query("""
        select r.id
        from Institution r
        where r.state = :state and r.institutionType = :institutionType
    """)
    List<Long> findIdsActiveByType(@Param("state") EntityState entityState, @Param("institutionType") InstitutionType institutionType);
}
