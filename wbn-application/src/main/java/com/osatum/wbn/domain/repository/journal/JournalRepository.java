package com.osatum.wbn.domain.repository.journal;


import com.osatum.wbn.domain.model.journal.Journal;

import java.util.List;
import java.util.Optional;

public interface JournalRepository {
    Journal save(Journal journal);

    void deleteById(Long id);

    Optional<Journal> findById(Long aLong);

    List<Long> findAllIds();

    List<Journal> findAll();
}
