package com.osatum.wbn.infrastructure.persistence.springdata;


import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionIpRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstitutionIpRuleSpringDataRepository extends JpaRepository<InstitutionIpRule, Long> {
    
    @Query("""
        select r
        from InstitutionIpRule r
        join fetch r.institution i
        where r.state = :state and i.state = :state
    """)
    List<InstitutionIpRule> findActiveByState(@Param("state") EntityState state);

    @Query("""
        select r
        from InstitutionIpRule r
        join fetch r.institution i
        where r.state = :state and i.state = :state and i.id = :institutionId
    """)
    List<InstitutionIpRule>  findByInstitutionIdAndByState(@Param("state") EntityState state, @Param("institutionId") Long institutionId);
}
