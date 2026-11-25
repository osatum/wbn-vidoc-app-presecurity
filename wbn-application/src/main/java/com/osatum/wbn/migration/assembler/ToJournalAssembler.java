package com.osatum.wbn.migration.assembler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osatum.jml.migration.dto.journal.Description;
import com.osatum.jml.migration.dto.journal.JournalPayload;
import com.osatum.jml.migration.dto.journal.ScientificCategory;
import com.osatum.jml.migration.dto.journal.Title;
import com.osatum.wbn.application.dictionary.service.*;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import com.osatum.wbn.domain.model.journal.*;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.RatingIcv;
import com.osatum.wbn.domain.model.questionnaire.RatingIcvStatus;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.AuthorsFee;
import com.osatum.wbn.domain.service.journal.AuthorFeeSelectionPolicy;
import com.osatum.wbn.migration.converter.DateTimeConverter;
import com.osatum.wbn.shared.assembler.AssemblerEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToJournalAssembler implements AssemblerEntity<Journal, JournalPayload, ToJournalParameters> {
    // Obsługa załączników ankiet została odłożona; pomijamy tylko te trzy właściwości.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .addMixIn(com.osatum.wbn.domain.model.survey.Description.class, SurveyDescriptionImportMixin.class)
            .addMixIn(com.osatum.wbn.domain.model.survey.Cooperation.class, SurveyCooperationImportMixin.class);

    @JsonIgnoreProperties({"cover", "logo"})
    private abstract static class SurveyDescriptionImportMixin {
    }

    @JsonIgnoreProperties("reviewersFile")
    private abstract static class SurveyCooperationImportMixin {
    }

    @Value("${app.cover-source}")
    private String coverSource;

    private final IssnMapper issnMapper;
    private final JournalInstitutionMapper institutionMapper;
    private final PersonMapper personMapper;
    private final CardinalityMapper cardinalityMapper;
    private final RatingMapper ratingMapper;
    private final AuthorFeeSelectionPolicy authorFeeSelectionPolicy;
    private final DictionaryScientificCategoryService scientificCategoryService;
    private final DictionaryTRCategoryService trCategoryService;
    private final DictionaryAsjcCategoryService asjcCategoryService;
    private final DictionaryRepositoryService dictionaryRepositoryService;
    private final DictionaryCountryService dictionaryCountryService;
    private final DictionaryLanguageService dictionaryLanguageService;

    @Override
    public Journal toEntity(ToJournalParameters paramater) {
        Journal journal = new Journal();

        JournalPayload dto = paramater.getDto();

        journal.setId(dto.getId());
        journal.setDoi(dto.getDoi());

        assemblyStatusCode(dto, journal);
        /*
         * - Tytuł oryginalny
         * - Tytuł w języku angielskim
         * - Tytuł skrócony
         * - Alternatywne tytuły
         */
        assemblyTitle(dto, journal);
        /*
         * - Opis czasopisma
         * - Opis w języku angielskim
         */
        assemblyDescription(dto, journal);
        /*
         * - Charakter czasopisma
         */
        assemblyKindCode(dto, journal);
        /*
         * - Główny profil naukowy
         * - Profile naukowe
         */
        assemblyScientificCategory(dto, journal);
        /*
         * - Kategoria Web of Science (główna)
         * - Kategoria Web of Science
         */
        assemblyTrCategory(dto, journal);
        /*
         * - Kategoria Scimago (główna)
         * - Kategorie Scimago
         */
        assemblyAjscCategory(dto, journal);
        /*
         * - Kraj
         */
        assemblyCountryCode(dto, journal);
        /*
         * -  Język publikacji
         */
        assemblyLanguages(dto, journal);
        /*
         * - ISSN
         */
        assemblyIssns(dto, journal);
        /*
         * - Doi
         */
        assemblyOaLicenceCode(dto, journal);
        /*
         * - Licencja Creative Commons publikacji czasopisma
         */
        assemblyInstitutions(dto, journal);
        /*
        - Bazy indeksacyjne
         */
        assemblyIndexedDb(dto, journal);
        /*
         - Strona internetowa
         */
        assemblyWebsite(dto, journal);
        /*
         * - Wersja drukowana
         */
        assemblyPrintedVersion(dto, journal);
        /*
         * - Wersja elektroniczna
         */
        assemblyElectronicVersion(dto, journal);

        /*
         * - Wydawane od
         */
        assemblyFirstIssueYear(dto, journal);
        /*
         * - Częstotliwość wydawania
         */
        assemblyFrequencyCode(dto, journal);
        /*
         * Nakład
         */
        assemblyCirculation(dto, journal);

        /*
         * Skład zespołu redakcyjnego
         */
        assemblyPersons(dto, journal);
        /*
         * Instytucje
         */
        assemblyInstitutions(dto, journal);

        /**
         * Licznosci
         */
        assemblyCardinality(dto, journal);

        /**
         * Ocena
         */
        //assemblyRating(dto, journal);

        /**
         * Cover
         */
        assemblyCover(dto, journal);

        assemblyWarning(dto, journal);

        assemblyQuestionarie(dto, journal, paramater.getSchemaVersion());
        assemblyDoi(dto, journal);

        return journal;
    }

    private void assemblyQuestionarie(JournalPayload dto, Journal journal, String schemaVersion) {
        if (dto.getQuestionnaires() == null) {
            return;
        }

        journal.setQuestionnaires(new HashSet<>());
        dto.getQuestionnaires().forEach(source -> {

            Questionnaire questionnaire = new Questionnaire();
            questionnaire.setYear(source.getYear());
            questionnaire.setCompleteness(source.getCompleteness());
            questionnaire.setJournal(journal);
            questionnaire.setSendDate(DateTimeConverter.toLocalDateTime(source.getSendDate()));
            questionnaire.setCreationDate(DateTimeConverter.toLocalDateTime(source.getCreationDate()));
            questionnaire.setRating(buildIcvRating(source));
            try {
                SurveyData surveyData = new SurveyData();
                Survey survey = objectMapper.convertValue(source.getSurvey(), Survey.class);
                if (survey == null) {
                    throw new IllegalArgumentException("Missing survey");
                }
                validateFees(survey, schemaVersion, dto.getCountryCode(), dto.getId(), source.getYear());

                if (survey.getSupplementaryData() != null){
                    if (PaidArticleType.FREE_ARTICLES.equals(survey.getSupplementaryData().getPaidArticle())){
                        survey.getSupplementaryData().setArticleCost(null);
                    }
                }

                surveyData.setSurvey(survey);

                questionnaire.setSurveyData(surveyData);
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot import survey for journal " + dto.getId()
                        + ", year " + source.getYear(), e);
            }

            journal.getQuestionnaires().add(questionnaire);
        });

    }

    private void validateFees(Survey survey, String schemaVersion, String countryCode, Long journalId, Integer year) {
        if (survey.getStandards() == null) {
            return;
        }
        Map<String, AuthorsFee> fees = new LinkedHashMap<>();
        fees.put("submissionFee", survey.getStandards().getSubmissionFee());
        fees.put("publicationFee", survey.getStandards().getPublicationFee());
        fees.put("openAccessFee", survey.getStandards().getOpenAccessFee());
        fees.put("rapidReview", survey.getStandards().getRapidReview());
        fees.put("apc", survey.getStandards().getApc());
        fees.forEach((name, fee) -> {
            AuthorFeeSelectionPolicy.Resolution resolution = authorFeeSelectionPolicy.validate(fee, schemaVersion, countryCode);
            if (fee != null) {
                fee.setFeeSelectionStatus(resolution.status());
            }
            if (resolution.issue() != null) {
                log.warn("Journal {}, survey {}, fee {}: {}", journalId, year, name, resolution.issue());
            }
        });
    }

    private RatingIcv buildIcvRating(com.osatum.jml.migration.dto.questionnaire.Questionnaire source) {
        if (source.getRating() == null) {
            return null;
        }
        RatingIcv ratingIcv = new RatingIcv();
        ratingIcv.setIcv(source.getRating().getIcv());
        ratingIcv.setRatingStatus(RatingIcvStatus.ERROR.valueOf(source.getRating().getStatus().name()));
        return ratingIcv;
    }


    private void assemblyWarning(JournalPayload dto, Journal journal) {
        if (dto.getWarningCode() == null) {
            return;
        }
        journal.setWarningCode(dto.getWarningCode());
    }

    private void assemblyCover(JournalPayload dto, Journal journal) {
        if (dto.getCover() == null) {
            return;
        }

        byte[] contents = dto.getCover().getContents();
        if (contents == null || contents.length == 0) {
            log.warn("Journal {}: skipping cover without contents", dto.getId());
            return;
        }

        String path = dto.getId() + "." + extractExtension(dto.getCover().getFileName());
        try {
            writeFile(contents, Path.of(coverSource), path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        FileData file = new FileData();
        file.setFileName(dto.getCover().getFileName());
        file.setFileType(dto.getCover().getFileType());
        file.setPath(path);
        file.setSize(Long.valueOf(contents.length));

        journal.setCover(file);
    }

    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int lastSeparator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String name = fileName.substring(lastSeparator + 1);
        int lastDot = name.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == name.length() - 1) {
            return "";
        }
        return name.substring(lastDot + 1);
    }

    private Path writeFile(byte[] data, Path directory, String fileName) throws IOException {
        if (directory == null || fileName == null) {
            throw new IllegalArgumentException("directory and fileName must not be null");
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(fileName);
        return Files.write(target, data);
    }

    private void assemblyStatusCode(JournalPayload dto, Journal journal) {
        if (dto.getState() == null) {
            return;
        }
        journal.setState(EntityState.valueOf(dto.getState().name()));
    }


    private void assemblyRating(JournalPayload dto, Journal journal) {
        if (dto.getRatings() == null) {
            return;
        }
        journal.setRatings(new HashSet<>());
        dto.getRatings().forEach(source -> {
            Rating rating = ratingMapper.toEntity(source);
            rating.setJournal(journal);

            if (source.getMniswDisciplineCode() != null) {
                rating.setMniswDisciplines(new HashSet<>());
                for (String code : source.getMniswDisciplineCode()) {

                    MniswDiscipline discipline = new MniswDiscipline();
                    discipline.setRating(rating);
                    discipline.setDisciplineCode(code);

                    rating.getMniswDisciplines().add(discipline);
                }
            }
            journal.getRatings().add(rating);
        });
    }

    private void assemblyCardinality(JournalPayload dto, Journal journal) {
        if (dto.getCardinality() == null) {
            return;
        }
        journal.setCardinality(cardinalityMapper.toEntity(dto.getCardinality()));
    }

    private void assemblyPersons(JournalPayload dto, Journal journal) {
        if (dto.getPersons() == null) {
            return;
        }
        journal.setPersons(new HashSet<>());
        dto.getPersons().forEach(source -> {
            Person person = personMapper.toEntity(source);
            person.setJournal(journal);
            journal.getPersons().add(person);
        });
    }

    private void assemblyCirculation(JournalPayload dto, Journal journal) {
        if (dto.getCirculation() == null) {
            return;
        }
        journal.setCirculation(dto.getCirculation());
    }

    private void assemblyFrequencyCode(JournalPayload dto, Journal journal) {
        if (dto.getFrequency() == null) {
            return;
        }
        journal.setFrequency(JournalFrequency.valueOf(dto.getFrequency().name()));
    }

    private void assemblyFirstIssueYear(JournalPayload dto, Journal journal) {
        if (dto.getFirstIssueYear() == null) {
            return;
        }
        journal.setFirstIssueYear(dto.getFirstIssueYear());
    }

    private void assemblyElectronicVersion(JournalPayload dto, Journal journal) {
        if (dto.getElectronicVersion() == null) {
            return;
        }
        journal.setElectronicVersion(dto.getElectronicVersion());
    }

    private void assemblyPrintedVersion(JournalPayload dto, Journal journal) {
        if (dto.getPrintedVersion() == null) {
            return;
        }
        journal.setPrintedVersion(dto.getPrintedVersion());
    }

    private void assemblyWebsite(JournalPayload dto, Journal journal) {
        if (dto.getWebsite() == null) {
            return;
        }
        journal.setWebsite(dto.getWebsite());
    }

    private void assemblyIndexedDb(JournalPayload dto, Journal journal) {
        if (dto.getIndexedDbs() == null) {
            return;
        }
        journal.setIndexedDbs(new HashSet<>());
        for (com.osatum.jml.migration.dto.journal.IndexedDb source : dto.getIndexedDbs()) {
            IndexedDb indexedDb = new IndexedDb();
            indexedDb.setJournal(journal);
            indexedDb.setYearFrom(source.getYearFrom());
            indexedDb.setRepository(dictionaryRepositoryService.getById(source.getId()));
            journal.getIndexedDbs().add(indexedDb);

        }
    }

    private void assemblyOaLicenceCode(JournalPayload dto, Journal journal) {
        if (dto.getOaLicenceCode() == null) {
            return;
        }
        journal.setOaLicenceCode(dto.getOaLicenceCode());
    }

    private void assemblyDoi(JournalPayload dto, Journal journal) {
        if (dto.getDoi() != null) {
            journal.setDoi(dto.getDoi());
        }
    }

    private void assemblyCountryCode(JournalPayload dto, Journal journal) {
        if (dto.getCountryCode() == null) {
            return;
        }

        DictionaryCountry dictionaryCountry = dictionaryCountryService.getByCode(dto.getCountryCode());
        journal.setCountry(dictionaryCountry);
    }

    private void assemblyAjscCategory(JournalPayload dto, Journal journal) {
        if (dto.getAsjcCategories() == null) {
            return;
        }
        journal.setAjscCategories(new HashSet<>());
        for (com.osatum.jml.migration.dto.journal.AsjcCategory source : dto.getAsjcCategories()) {
            AsjcCategory to = new AsjcCategory();
            to.setJournal(journal);
            to.setCategoryType(CategoryType.valueOf(source.getType().name()));
            to.setCategory(asjcCategoryService.getById(source.getCategoryId()));
            journal.getAjscCategories().add(to);
        }
    }

    private void assemblyTrCategory(JournalPayload dto, Journal journal) {
        if (dto.getTrCategories() == null) {
            return;
        }
        journal.setTrCategories(new HashSet<>());
        for (com.osatum.jml.migration.dto.journal.TrCategory source : dto.getTrCategories()) {
            TrCategory to = new TrCategory();
            to.setJournal(journal);
            to.setCategoryType(CategoryType.valueOf(source.getType().name()));
            to.setCategory(trCategoryService.getById(source.getCategoryId()));
            journal.getTrCategories().add(to);
        }
    }

    private void assemblyScientificCategory(JournalPayload dto, Journal journal) {
        if (dto.getScientificCategories() == null) {
            return;
        }
        journal.setScientificCategories(new HashSet<>());
        for (ScientificCategory source : dto.getScientificCategories()) {
            com.osatum.wbn.domain.model.journal.ScientificCategory to = new com.osatum.wbn.domain.model.journal.ScientificCategory();
            to.setJournal(journal);
            to.setCategoryType(CategoryType.valueOf(source.getType().name()));
            to.setCategory(scientificCategoryService.getById(source.getCategoryId()));
            journal.getScientificCategories().add(to);
        }
    }

    private void assemblyKindCode(JournalPayload dto, Journal journal) {
        if (dto.getKind() == null) {
            return;
        }
        JournalKind kind = JournalKind.valueOf(dto.getKind().name());
        journal.setKind(kind);
    }

    private void assemblyLanguages(JournalPayload dto, Journal journal) {
        if (dto.getLanguages() == null) {
            return;
        }
        journal.setLanguages(new HashSet<>());
        for (com.osatum.jml.migration.dto.journal.Language from : dto.getLanguages()) {

            Language language = new Language();
            language.setJournal(journal);
            language.setOrderNo(from.getOrderNo() != null ? from.getOrderNo() : 0);
            DictionaryLanguage dictionaryLanguage = dictionaryLanguageService.getByCode(from.getCode());
            language.setLanguage(dictionaryLanguage);
            journal.getLanguages().add(language);
        }
    }

    private void assemblyInstitutions(JournalPayload dto, Journal journal) {
        if (dto.getInstitutions() == null) {
            return;
        }
        journal.setInstitutions(new HashSet<>());
        for (com.osatum.jml.migration.dto.journal.Institution from : dto.getInstitutions()) {
            JournalInstitution institution = institutionMapper.toEntity(from);
            institution.setJournal(journal);
            journal.getInstitutions().add(institution);
        }
    }

    private void assemblyIssns(JournalPayload dto, Journal journal) {
        if (dto.getIssns() == null) {
            return;
        }
        journal.setIssns(new HashSet<>());
        for (com.osatum.jml.migration.dto.journal.Issn from : dto.getIssns()) {
            Issn issn = issnMapper.toEntity(from);
            issn.setJournal(journal);
            journal.getIssns().add(issn);
        }
    }

    private void assemblyTitle(JournalPayload dto, Journal journal) {
        if (dto.getTitles() == null) {
            return;
        }
        journal.setTitles(new HashSet<>());
        for (Title from : dto.getTitles()) {
            com.osatum.wbn.domain.model.journal.Title title = new com.osatum.wbn.domain.model.journal.Title();
            title.setJournal(journal);
            title.setContent(from.getContent());
            if (from.getCode() != null) {
                title.setCode(TitleCode.valueOf(from.getCode().name()));
            }

            journal.getTitles().add(title);
        }
    }

    private void assemblyDescription(JournalPayload dto, Journal journal) {
        if (dto.getDescriptions() == null) {
            return;
        }
        journal.setDescriptions(new HashSet<>());

        for (Description from : dto.getDescriptions()) {
            com.osatum.wbn.domain.model.journal.Description to = new com.osatum.wbn.domain.model.journal.Description();
            to.setJournal(journal);
            to.setContent(from.getContent());
            if (from.getType() != null) {
                to.setType(DescriptionType.valueOf(from.getType().name()));
            }
            journal.getDescriptions().add(to);
        }
    }
}
