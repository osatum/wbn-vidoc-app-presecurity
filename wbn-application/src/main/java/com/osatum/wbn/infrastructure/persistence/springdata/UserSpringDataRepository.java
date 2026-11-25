package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSpringDataRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {
    Optional<AppUser> findByEmail(String email);

    long countByState(EntityState state);
}
