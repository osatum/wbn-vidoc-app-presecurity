package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.journal.Journal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalSpringDataRepository extends JpaRepository<Journal, Long> {
    @Query("select journal.id from Journal journal")
    List<Long> findAllIds();

    @Query("select journal.id from Journal journal where journal.id > :lastId order by journal.id")
    List<Long> findIdsAfter(@Param("lastId") Long lastId, Pageable pageable);
}
