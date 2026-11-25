package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.JournalSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JournalRepositoryAdapter implements JournalRepository {
    private final JournalSpringDataRepository repository;


    @Override
    public Journal save(Journal journal){
        return repository.save(journal);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Override
    public Optional<Journal> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<Long> findAllIds() {
        return repository.findAllIds();
    }

    public long countJournals() {
        return repository.count();
    }

    public List<Long> findIdsAfter(Long lastId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Journal ID page limit must be positive.");
        }
        return repository.findIdsAfter(lastId, PageRequest.of(0, limit));
    }

    @Override
    public List<Journal> findAll() {
        return repository.findAll();
    }

}
