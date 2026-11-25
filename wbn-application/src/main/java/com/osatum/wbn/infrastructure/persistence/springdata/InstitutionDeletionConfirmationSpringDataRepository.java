package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.institution.InstitutionDeletionConfirmation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstitutionDeletionConfirmationSpringDataRepository
        extends JpaRepository<InstitutionDeletionConfirmation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select confirmation
            from InstitutionDeletionConfirmation confirmation
            where confirmation.id = :confirmationId
            """)
    Optional<InstitutionDeletionConfirmation> findByIdForUpdate(@Param("confirmationId") UUID confirmationId);

    @Modifying
    @Query("""
            delete from InstitutionDeletionConfirmation confirmation
            where confirmation.institutionId = :institutionId
                and confirmation.actorKey = :actorKey
                and confirmation.usedAt is null
            """)
    void deleteActiveForInstitutionAndActor(
            @Param("institutionId") Long institutionId,
            @Param("actorKey") String actorKey
    );

    @Modifying
    @Query("""
            delete from InstitutionDeletionConfirmation confirmation
            where confirmation.institutionId = :institutionId
            """)
    void deleteByInstitutionId(@Param("institutionId") Long institutionId);
}
