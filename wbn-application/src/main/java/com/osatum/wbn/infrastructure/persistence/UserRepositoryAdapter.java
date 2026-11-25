package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.repository.user.UserRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.UserSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserSpringDataRepository springData;

    @Override
    public Optional<AppUser> findById(Long id) {
        return springData.findById(id);
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        return springData.findByEmail(email);
    }

    @Override
    public AppUser save(AppUser user) {
        return springData.save(user);
    }

    @Override
    public void delete(AppUser user) {
        springData.delete(user);
    }

    @Override
    public long countActive() {
        return springData.countByState(EntityState.ACTIVE);
    }
}
