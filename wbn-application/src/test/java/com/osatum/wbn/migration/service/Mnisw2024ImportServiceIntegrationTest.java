package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryMniswDisciplineService;
import com.osatum.wbn.domain.model.dictionary.DictionaryMniswDiscipline;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.MniswDiscipline;
import com.osatum.wbn.domain.model.journal.Rating;
import com.osatum.wbn.infrastructure.elasticsearch.assembler.simple.JournalDocumentAssembler;
import com.osatum.wbn.infrastructure.elasticsearch.assembler.simple.JournalDocumentParameters;
import com.osatum.wbn.infrastructure.persistence.DictionaryMniswDisciplineRepositoryAdapter;
import com.osatum.wbn.infrastructure.persistence.JournalRepositoryAdapter;
import com.osatum.wbn.infrastructure.persistence.springdata.JournalSpringDataRepository;
import jakarta.persistence.EntityManager;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Mnisw2024ImportServiceIntegrationTest.Configuration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mnisw-import;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Transactional
class Mnisw2024ImportServiceIntegrationTest {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.osatum.wbn.domain.model")
    @EnableJpaRepositories(basePackageClasses = JournalSpringDataRepository.class)
    @Import({Mnisw2024ImportService.class, JournalRepositoryAdapter.class,
            DictionaryMniswDisciplineRepositoryAdapter.class, DictionaryMniswDisciplineService.class})
    static class Configuration {
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Mnisw2024ImportService service;

    @TempDir
    Path tempDir;

    @Test
    void persistsXlsAssignmentsAndReplacesOrphansInTheSameRatingAndIndexProjection() throws Exception {
        Journal journal = new Journal();
        journal.setId(123L);
        entityManager.persist(journal);
        for (String code : Set.of("2021", "2061")) {
            DictionaryMniswDiscipline discipline = new DictionaryMniswDiscipline();
            discipline.setCode(code);
            discipline.setNamePl(code);
            entityManager.persist(discipline);
        }
        flushAndClear();

        importFile("first.xls", true, false);
        Rating initial = storedRating();
        Long ratingId = initial.getId();
        assertThat(initial.getRatingCode()).isEqualTo(Rating.MNW_RATING_CODE);
        assertThat(initial.getYear()).isEqualTo(2024);
        assertThat(initial.getValueNum()).isEqualTo(100.0);
        assertThat(initial.getMniswDisciplines()).extracting(MniswDiscipline::getDisciplineCode)
                .containsExactly("2021");
        assertProjectedCodes("2021");

        importFile("replacement.xls", false, true);
        Rating replaced = storedRating();
        assertThat(replaced.getId()).isEqualTo(ratingId);
        assertThat(replaced.getMniswDisciplines()).singleElement().satisfies(discipline -> {
            assertThat(discipline.getDisciplineCode()).isEqualTo("2061");
            assertThat(discipline.getRating().getId()).isEqualTo(ratingId);
        });
        assertThat(entityManager.createQuery("select discipline.disciplineCode from MniswDiscipline discipline", String.class)
                .getResultList()).containsExactly("2061");
        assertProjectedCodes("2061");

        importFile("cleared.xls", false, false);
        assertThat(storedRating().getId()).isEqualTo(ratingId);
        assertThat(storedRating().getMniswDisciplines()).isEmpty();
        assertThat(entityManager.createQuery("select count(discipline) from MniswDiscipline discipline", Long.class)
                .getSingleResult()).isZero();
        assertThat(new JournalDocumentAssembler().toDto(new JournalDocumentParameters(
                entityManager.find(Journal.class, 123L), null)).getRatings()).singleElement()
                .satisfies(rating -> assertThat(rating.getDisciplines()).isNullOrEmpty());
    }

    private void importFile(String filename, boolean firstDiscipline, boolean secondDiscipline) throws Exception {
        Path path = tempDir.resolve(filename);
        try (HSSFWorkbook workbook = new HSSFWorkbook(); var output = Files.newOutputStream(path)) {
            var sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("mein_value");
            header.createCell(2).setCellValue("2021");
            header.createCell(3).setCellValue("2061");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(123);
            row.createCell(1).setCellValue(100);
            row.createCell(2).setCellValue(firstDiscipline);
            row.createCell(3).setCellValue(secondDiscipline);
            workbook.write(output);
        }
        for (var record : service.readRecords(path.toString())) {
            service.addRating(record);
        }
        flushAndClear();
    }

    private Rating storedRating() {
        Set<Rating> ratings = entityManager.find(Journal.class, 123L).getRatings();
        assertThat(ratings).hasSize(1);
        return ratings.iterator().next();
    }

    private void assertProjectedCodes(String... codes) {
        var document = new JournalDocumentAssembler().toDto(new JournalDocumentParameters(
                entityManager.find(Journal.class, 123L), null));
        assertThat(document.getRatings()).singleElement().satisfies(rating ->
                assertThat(rating.getDisciplines()).extracting(
                        com.osatum.wbn.infrastructure.elasticsearch.document.journal.MniswDiscipline::getCode)
                        .containsExactlyInAnyOrder(codes));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
