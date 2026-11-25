package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.dictionary.DictionaryAsjcCategory;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import com.osatum.wbn.domain.model.dictionary.DictionaryRepository;
import com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory;
import com.osatum.wbn.domain.model.dictionary.DictionaryTRCategory;
import com.osatum.wbn.domain.model.journal.AsjcCategory;
import com.osatum.wbn.domain.model.journal.IndexedDb;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.Language;
import com.osatum.wbn.domain.model.journal.ScientificCategory;
import com.osatum.wbn.domain.model.journal.TrCategory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PersistenceRegressionTestConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dictionary-cleanup-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Transactional
class DictionaryReferenceCleanupRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DictionaryReferenceCleanupRepository repository;

    @Test
    void deletesJournalDictionaryReferencesBeforeDictionaryItemsAreRemoved() {
        createReferencedDictionaries();

        repository.deleteJournalDictionaryReferences();

        assertThat(count("AsjcCategory")).isZero();
        assertThat(count("ScientificCategory")).isZero();
        assertThat(count("TrCategory")).isZero();
        assertThat(count("IndexedDb")).isZero();
        assertThat(count("Language")).isZero();

        Journal journal = entityManager.find(Journal.class, 501L);
        assertThat(journal.getCountry()).isNull();
        assertThat(journal.getAffiliationCountry()).isNull();

        assertThatCode(this::deleteDictionaries).doesNotThrowAnyException();
    }

    private void createReferencedDictionaries() {
        DictionaryCountry country = new DictionaryCountry();
        country.setCode("PL");
        country.setCodeAlpha3("POL");
        country.setNamePl("Polska");
        entityManager.persist(country);

        DictionaryLanguage dictionaryLanguage = new DictionaryLanguage();
        dictionaryLanguage.setCode("pl");
        dictionaryLanguage.setNamePl("polski");
        entityManager.persist(dictionaryLanguage);

        DictionaryAsjcCategory asjcDictionary = new DictionaryAsjcCategory();
        asjcDictionary.setId(101L);
        asjcDictionary.setNamePl("ASJC");
        entityManager.persist(asjcDictionary);

        DictionaryScientificCategory scientificDictionary = new DictionaryScientificCategory();
        scientificDictionary.setId(201L);
        scientificDictionary.setNamePl("Scientific");
        entityManager.persist(scientificDictionary);

        DictionaryTRCategory trDictionary = new DictionaryTRCategory();
        trDictionary.setId(301L);
        trDictionary.setNamePl("TR");
        entityManager.persist(trDictionary);

        DictionaryRepository repositoryDictionary = new DictionaryRepository();
        repositoryDictionary.setId(401L);
        repositoryDictionary.setName("Repository");
        entityManager.persist(repositoryDictionary);

        Journal journal = new Journal();
        journal.setId(501L);
        journal.setCountry(country);
        journal.setAffiliationCountry(country);
        entityManager.persist(journal);

        AsjcCategory asjcCategory = new AsjcCategory();
        asjcCategory.setJournal(journal);
        asjcCategory.setCategory(asjcDictionary);
        entityManager.persist(asjcCategory);

        ScientificCategory scientificCategory = new ScientificCategory();
        scientificCategory.setJournal(journal);
        scientificCategory.setCategory(scientificDictionary);
        entityManager.persist(scientificCategory);

        TrCategory trCategory = new TrCategory();
        trCategory.setJournal(journal);
        trCategory.setCategory(trDictionary);
        entityManager.persist(trCategory);

        IndexedDb indexedDb = new IndexedDb();
        indexedDb.setJournal(journal);
        indexedDb.setRepository(repositoryDictionary);
        entityManager.persist(indexedDb);

        Language language = new Language();
        language.setJournal(journal);
        language.setLanguage(dictionaryLanguage);
        entityManager.persist(language);

        entityManager.flush();
        entityManager.clear();
    }

    private void deleteDictionaries() {
        entityManager.createQuery("delete from DictionaryAsjcCategory").executeUpdate();
        entityManager.createQuery("delete from DictionaryScientificCategory").executeUpdate();
        entityManager.createQuery("delete from DictionaryTRCategory").executeUpdate();
        entityManager.createQuery("delete from DictionaryRepository").executeUpdate();
        entityManager.createQuery("delete from DictionaryCountry").executeUpdate();
        entityManager.createQuery("delete from DictionaryLanguage").executeUpdate();
        entityManager.flush();
    }

    private long count(String entityName) {
        return entityManager.createQuery("select count(entity) from " + entityName + " entity", Long.class)
                .getSingleResult();
    }
}
