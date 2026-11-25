package com.osatum.wbn.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DictionaryReferenceCleanupRepository {
    private final EntityManager entityManager;

    public void deleteJournalDictionaryReferences() {
        entityManager.flush();

        entityManager.createQuery("delete from AsjcCategory").executeUpdate();
        entityManager.createQuery("delete from ScientificCategory").executeUpdate();
        entityManager.createQuery("delete from TrCategory").executeUpdate();
        entityManager.createQuery("delete from IndexedDb").executeUpdate();
        entityManager.createQuery("delete from Language journalLanguage").executeUpdate();
        entityManager.createQuery("""
                update Journal journal
                set journal.country = null,
                    journal.affiliationCountry = null
                where journal.country is not null
                    or journal.affiliationCountry is not null
                """).executeUpdate();

        entityManager.clear();
    }
}
