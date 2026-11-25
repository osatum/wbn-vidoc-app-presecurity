package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.application.dictionary.service.*;
import com.osatum.wbn.domain.model.dictionary.DictionaryCategory;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import com.osatum.wbn.domain.model.journal.*;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.Language;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.RatingIcv;
import com.osatum.wbn.domain.model.survey.*;
import com.osatum.wbn.domain.model.survey.Description;
import com.osatum.wbn.domain.model.survey.model.*;
import com.osatum.wbn.domain.model.survey.model.Category;
import com.osatum.wbn.domain.model.survey.model.Issn;
import com.osatum.wbn.domain.model.survey.model.JournalInstitution;
import com.osatum.wbn.domain.model.survey.model.Person;
import com.osatum.wbn.domain.service.journal.JournalRatingPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class SurveyToJournalUpdater {

    private final DictionaryCountryService dictionaryCountryService;
    private final DictionaryScientificCategoryService dictionaryScientificCategoryService;
    private final DictionaryAsjcCategoryService dictionaryAsjcCategoryService;
    private final DictionaryTRCategoryService dictionaryTRCategoryService;
    private final DictionaryLanguageService dictionaryLanguageService;
    private final JournalRatingPolicyService journalRatingPolicyService;
    private final SurveyJournalEntityMapper entityMapper;

    public void update(Journal journal, Survey survey) {
        update(journal, survey, null);
    }

    public void update(Journal journal, Survey survey, Integer surveyYear) {
        if (journal == null || survey == null) {
            return;
        }

        updateTitles(journal, survey);
        updateIssns(journal, survey);
        updateCountry(journal, survey);
        updateAffiliationCountry(journal, survey);
        updateAffiliationName(journal, survey);
        updateScientificCategories(journal, survey);
        updateAsjcCategories(journal, survey);
        updateTrCategories(journal, survey);
        updateDoi(journal, survey);
        updateWebsite(journal, survey);
        updateEditorialInstitutions(journal, survey);
        updatePublisherInstitutions(journal, survey);
        updatePersons(journal, survey);
        updatePublishingVersions(journal, survey);
        updateJournalKind(journal, survey);
        updateFrequency(journal, survey, surveyYear);
        updateCirculation(journal, survey);
        updateFirstYear(journal, survey);
        updatePaidArticle(journal, survey);
        updateDescriptions(journal, survey);
        updateLanguages(journal, survey, surveyYear);

    }

    public void updateRatings(Journal journal, List<Questionnaire> ratingIcvs) {
        if (journal.getRatings() == null) {
            journal.setRatings(new HashSet<>());
        }
        for (Rating rating : journal.getRatings()) {
            if (Rating.ICV_RATING_CODE.equals(rating.getRatingCode())) {
                rating.setJournal(null);
            }
        }
        journal.getRatings().removeIf(rating -> Rating.ICV_RATING_CODE.equals(rating.getRatingCode()));

        for (Questionnaire questionnaire : ratingIcvs) {
            if (questionnaire.getRating() == null) {
                continue;
            }
            RatingIcv ratingIcv = questionnaire.getRating();
            Rating rating = new Rating();
            rating.setJournal(journal);
            rating.setYear(questionnaire.getYear());
            rating.setRatingCode(Rating.ICV_RATING_CODE);
            rating.setValueNum(ratingIcv.getIcv());
            rating.setReasonNotIndexing(ratingIcv.getReasonNotIndexing());
            if (ratingIcv.getRatingStatus() != null) {
                rating.setRatingStatus(
                        journalRatingPolicyService.getRatingStatus(ratingIcv.getRatingStatus().name())
                );
            }
            journal.getRatings().add(rating);
        }
    }


    private void updateTitles(Journal journal, Survey survey) {
        com.osatum.wbn.domain.model.survey.Journal surveyJournal = survey.getJournal();

        if (journal.getTitles() == null) {
            journal.setTitles(new HashSet<>());
        } else {
            journal.getTitles().forEach(x -> x.setJournal(null));
            journal.getTitles().clear();
        }


        if (surveyJournal == null || isEmpty(surveyJournal.getTitles())) {
            return;
        }
        for (JournalTitle source : surveyJournal.getTitles()) {
            Title title = entityMapper.toJournalTitle(journal, source);
            if (title != null) {
                journal.getTitles().add(title);
            }
        }
    }

    private void updateIssns(Journal journal, Survey survey) {
        com.osatum.wbn.domain.model.survey.Journal surveyJournal = survey.getJournal();
        if (journal.getIssns() == null) {
            journal.setIssns(new HashSet<>());
        } else {
            journal.getIssns().forEach(x -> x.setJournal(null));
            journal.getIssns().clear();
        }
        if (surveyJournal == null || isEmpty(surveyJournal.getIssns())) {
            return;
        }
        for (Issn source : surveyJournal.getIssns()) {
            com.osatum.wbn.domain.model.journal.Issn issn = entityMapper.toJournalIssn(journal, source);
            if (issn != null) {
                journal.getIssns().add(issn);
            }
        }
    }


    private void updateAffiliationName(Journal journal, Survey survey) {
        SupplementaryData data = survey.getSupplementaryData();
        journal.setAffiliationName(null);
        if (data == null || data.getAffiliationName() == null || !hasText(data.getAffiliationName())) {
            return;
        }
        journal.setAffiliationName(data.getAffiliationName());
    }

    private void updateAffiliationCountry(Journal journal, Survey survey) {
        SupplementaryData data = survey.getSupplementaryData();
        journal.setAffiliationCountry(null);

        if (data == null || data.getAffiliationCountry() == null || !hasText(data.getAffiliationCountry().getCode())) {
            return;
        }
        DictionaryCountry dictionaryCountry = dictionaryCountryService.getByCode(data.getCountry().getCode());
        if (dictionaryCountry != null) {
            journal.setAffiliationCountry(dictionaryCountry);
        }
    }

    private void updateCountry(Journal journal, Survey survey) {
        SupplementaryData data = survey.getSupplementaryData();
        journal.setCountry(null);
        if (data == null || data.getCountry() == null || !hasText(data.getCountry().getCode())) {
            return;
        }

        DictionaryCountry dictionaryCountry = dictionaryCountryService.getByCode(data.getCountry().getCode());
        if (dictionaryCountry != null) {
            journal.setCountry(dictionaryCountry);
        }
    }

    private void updateScientificCategories(Journal journal, Survey survey) {
        SupplementaryData data = survey.getSupplementaryData();

        if (journal.getScientificCategories() == null) {
            journal.setScientificCategories(new HashSet<>());
        } else {
            journal.getScientificCategories().forEach(x -> x.setJournal(null));
            journal.getScientificCategories().clear();
        }


        if (data == null || data.getScientificCategories() == null) {
            return;
        }
        addCategories(
                journal,
                data.getScientificCategories(),
                journal.getScientificCategories(),
                dictionaryScientificCategoryService::getById,
                ScientificCategory::new,
                ScientificCategory::setCategory
        );
    }

    private void updateAsjcCategories(Journal journal, Survey survey) {
        SupplementaryData data = survey.getSupplementaryData();
        if (journal.getAjscCategories() == null) {
            journal.setAjscCategories(new HashSet<>());
        } else {
            journal.getAjscCategories().forEach(x -> x.setJournal(null));
            journal.getAjscCategories().clear();
        }
        if (data == null || data.getAsjcCategories() == null) {
            return;
        }
        addCategories(
                journal,
                data.getAsjcCategories(),
                journal.getAjscCategories(),
                dictionaryAsjcCategoryService::getById,
                AsjcCategory::new,
                AsjcCategory::setCategory
        );
    }

    private void updateTrCategories(Journal journal, Survey survey) {
        SupplementaryData data = survey.getSupplementaryData();
        if (journal.getTrCategories() == null) {
            journal.setTrCategories(new HashSet<>());
        } else {
            journal.getTrCategories().forEach(x -> x.setJournal(null));
            journal.getTrCategories().clear();
        }
        if (data == null || data.getTrCategories() == null) {
            return;
        }
        addCategories(
                journal,
                data.getTrCategories(),
                journal.getTrCategories(),
                dictionaryTRCategoryService::getById,
                TrCategory::new,
                TrCategory::setCategory
        );
    }

    private void updateDoi(Journal journal, Survey survey) {
        Digitalization digitalization = survey.getDigitalization();
        journal.setDoi(null);
        if (digitalization == null || digitalization.getDoiPrefix() == null) {
            return;
        }

        journal.setDoi(digitalization.getDoiPrefix());
    }

    private void updateWebsite(Journal journal, Survey survey) {
        Digitalization digitalization = survey.getDigitalization();
        journal.setWebsite(null);
        if (digitalization == null || digitalization.getWebsite() == null) {
            return;
        }
        journal.setWebsite(digitalization.getWebsite());
    }

    private void updatePublisherInstitutions(Journal journal, Survey survey) {
        Publisher publisher = survey.getPublisher();
        if (publisher == null) {
            return;
        }

        if (journal.getInstitutions() == null) {
            journal.setInstitutions(new HashSet<>());
        } else {
            journal.getInstitutions().stream()
                    .filter(x -> com.osatum.wbn.domain.model.journal.JournalInstitution.ROLE_PUBLISHER.equals(x.getRoleCode()))
                    .forEach(
                            x -> {
                                x.setJournal(null);
                            }
                    );
            journal.getInstitutions().removeIf(
                    x -> com.osatum.wbn.domain.model.journal.JournalInstitution.ROLE_PUBLISHER.equals(x.getRoleCode())
            );
        }


        if (isEmpty(publisher.getPublisherInstitutions())) {
            return;
        }
        for (JournalInstitution source : publisher.getPublisherInstitutions()) {
            if (!Character.valueOf('P').equals(source.getInstitutionRole())) {
                continue;
            }
            com.osatum.wbn.domain.model.journal.JournalInstitution institution = entityMapper.toJournalInstitution(journal, source);
            if (institution != null) {
                journal.getInstitutions().add(institution);
            }
        }
    }

    private void updateEditorialInstitutions(Journal journal, Survey survey) {
        Editorial editorial = survey.getEditorial();
        if (editorial == null) {
            return;
        }

        if (journal.getInstitutions() == null) {
            journal.setInstitutions(new HashSet<>());
        } else {
            journal.getInstitutions().stream()
                    .filter(x -> com.osatum.wbn.domain.model.journal.JournalInstitution.ROLE_EDITORIAL_OFFICE.equals(x.getRoleCode()))
                    .forEach(
                            x -> {
                                x.setJournal(null);
                            }

                    );
            journal.getInstitutions().removeIf(
                    x -> com.osatum.wbn.domain.model.journal.JournalInstitution.ROLE_EDITORIAL_OFFICE.equals(x.getRoleCode())
            );
        }

        if (isEmpty(editorial.getRedactionInstitutions())) {
            return;
        }
        for (JournalInstitution source : editorial.getRedactionInstitutions()) {
            if (!Character.valueOf('E').equals(source.getInstitutionRole())) {
                continue;
            }
            com.osatum.wbn.domain.model.journal.JournalInstitution institution = entityMapper.toJournalInstitution(journal, source);
            if (institution != null) {
                journal.getInstitutions().add(institution);
            }
        }
    }

    private void updatePersons(Journal journal, Survey survey) {
        Editorial editorial = survey.getEditorial();
        if (journal.getPersons() == null) {
            journal.setPersons(new HashSet<>());
        } else {
            journal.getPersons().forEach(
                    x -> {
                        x.setJournal(null);
                    }

            );
            journal.getPersons().clear();
        }

        if (editorial == null || isEmpty(editorial.getEditors())) {
            return;
        }
        for (Person source : editorial.getEditors()) {
            com.osatum.wbn.domain.model.journal.Person person = entityMapper.toJournalPerson(journal, source);
            if (person != null) {
                journal.getPersons().add(person);
            }
        }
    }

    private void updatePublishingVersions(Journal journal, Survey survey) {
        PublishingVersions versions = survey.getPublishingVersions();
        journal.setElectronicVersion(null);
        journal.setPrintedVersion(null);
        if (versions == null) {
            return;
        }

        journal.setElectronicVersion(versions.getElectronicVersion());
        journal.setPrintedVersion(versions.getPrintedVersion());
    }

    private void updateJournalKind(Journal journal, Survey survey) {
        SupplementaryData data = survey.getSupplementaryData();
        journal.setKind(null);
        if (data == null) {
            return;
        }
        journal.setKind(data.getKind());
    }

    private void updateFrequency(Journal journal, Survey survey, Integer surveyYear) {
        Stability stability = survey.getStability();
        journal.setFrequency(null);
        Integer year = surveyYear == null ? survey.getEvaluationYear() : surveyYear;
        if (stability == null || year == null || stability.getArticlePublished() == null) {
            return;
        }

        for (ArticlePublished articlePublished : stability.getArticlePublished()) {
            if (articlePublished == null || !year.equals(articlePublished.getYear())) {
                continue;
            }
            journal.setFrequency(articlePublished.getFrequencyCode());
            return;
        }
    }

    private void updateCirculation(Journal journal, Survey survey) {
        PublishingVersions versions = survey.getPublishingVersions();
        journal.setCirculation(null);
        if (versions == null) {
            return;
        }
        journal.setCirculation(versions.getCirculation());
    }

    private void updateFirstYear(Journal journal, Survey survey) {
        Stability stability = survey.getStability();
        journal.setFirstIssueYear(null);
        if (stability == null) {
            return;
        }

        journal.setFirstIssueYear(stability.getFirstIssueYear());
    }

    private void updatePaidArticle(Journal journal, Survey survey) {
        SupplementaryData data = survey.getSupplementaryData();
        journal.setPaidArticle(null);
        if (data == null) {
            return;
        }
        if (data.getPaidArticle() == null) {
            return;
        }

        PaidArticle paidArticle = new PaidArticle();
        paidArticle.setPaidType(data.getPaidArticle());
        if (data.getArticleCost() != null) {
            paidArticle.setPaidType(data.getPaidArticle());
            if (!PaidArticleType.FREE_ARTICLES.equals(data.getPaidArticle())) {
                paidArticle.setValue(data.getArticleCost().getValue());
                paidArticle.setCurrencyCode(data.getArticleCost().getCurrencyCode());

            }
        }
        journal.setPaidArticle(paidArticle);
    }

    private void updateDescriptions(Journal journal, Survey survey) {
        Description source = survey.getDescription();
        if (journal.getDescriptions() == null) {
            journal.setDescriptions(new HashSet<>());
        } else {
            journal.getDescriptions().forEach(
                    x -> {
                        x.setJournal(null);
                    }
            );
            journal.getDescriptions().clear();
        }

        if (source == null) {
            return;
        }


        addDescription(journal, journal.getDescriptions(), source.getDescriptionPL(), DescriptionType.PL);
        addDescription(journal, journal.getDescriptions(), source.getDescriptionEN(), DescriptionType.EN);
    }

    private void updateLanguages(Journal journal, Survey survey, Integer surveyYear) {
        Stability stability = survey.getStability();

        if (journal.getLanguages() == null) {
            journal.setLanguages(new HashSet<>());
        } else {
            journal.getLanguages().forEach(
                    x -> {
                        x.setJournal(null);
                    }
            );
            journal.getLanguages().clear();
        }

        if (stability == null || isEmpty(stability.getArticleLanguages())) {
            return;
        }

        List<String> languageCodes = stability.getArticleLanguages().stream()
                .filter(item -> item != null && item.getLanguage() != null && hasText(item.getLanguage().getCode()))
                .filter(item -> surveyYear == null
                        || (surveyYear.equals(item.getCurrentYear()) && item.getCurrentValue() != null && item.getCurrentValue() > 0)
                        || (surveyYear.equals(item.getPreviousYear()) && item.getPreviousValue() != null && item.getPreviousValue() > 0))
                .map(item -> item.getLanguage().getCode())
                .distinct()
                .toList();

        if (languageCodes.isEmpty()) {
            return;
        }


        for (int i = 0; i < languageCodes.size(); i++) {
            DictionaryLanguage dictionaryLanguage = dictionaryLanguageService.getByCode(languageCodes.get(i));
            if (dictionaryLanguage == null) {
                continue;
            }

            Language language = new Language();
            language.setJournal(journal);
            language.setOrderNo(i);
            language.setLanguage(dictionaryLanguage);
            journal.getLanguages().add(language);
        }
    }

    private <D extends DictionaryCategory, C extends com.osatum.wbn.domain.model.journal.Category> void addCategories(
            Journal journal,
            List<Category> sourceCategories,
            Set<C> targetCategories,
            Function<Long, D> dictionaryResolver,
            Supplier<C> targetFactory,
            BiConsumer<C, D> dictionarySetter
    ) {
        for (Category source : sourceCategories) {
            if (source == null || source.getId() == null) {
                continue;
            }

            D dictionaryCategory = dictionaryResolver.apply(source.getId());
            if (dictionaryCategory == null) {
                continue;
            }

            C category = targetFactory.get();
            category.setJournal(journal);
            category.setCategoryType(entityMapper.toCategoryType(source));
            dictionarySetter.accept(category, dictionaryCategory);
            targetCategories.add(category);
        }
    }

    private void addDescription(
            Journal journal,
            Set<com.osatum.wbn.domain.model.journal.Description> descriptions,
            String content,
            DescriptionType type
    ) {
        if (content == null) {
            return;
        }

        com.osatum.wbn.domain.model.journal.Description description = new com.osatum.wbn.domain.model.journal.Description();
        description.setJournal(journal);
        description.setContent(content);
        description.setType(type);
        descriptions.add(description);
    }

    private boolean isEmpty(List<?> items) {
        return items == null || items.isEmpty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
