package com.osatum.wbn.domain.repository.user;

import com.osatum.wbn.domain.model.user.AppUser;

import java.util.Optional;

public interface UserRepository {
    Optional<AppUser> findById(Long id);
    Optional<AppUser> findByEmail(String email);
    AppUser save(AppUser user);
    void delete(AppUser user);
    long countActive();
}
