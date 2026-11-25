package com.osatum.wbn.infrastructure.elasticsearch.assembler;

import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.dictionary.service.DictionaryLanguageService;
import com.osatum.wbn.application.journal.view.CountryView;
import com.osatum.wbn.application.journal.view.JournalListView;
import com.osatum.wbn.application.journal.view.LanguageView;
import com.osatum.wbn.application.journal.view.StudyStatus;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import com.osatum.wbn.domain.model.journal.IssnType;
import com.osatum.wbn.domain.model.journal.JournalInstitution;
import com.osatum.wbn.domain.model.journal.Rating;
import com.osatum.wbn.domain.model.journal.TitleCode;
import com.osatum.wbn.domain.model.questionnaire.RatingIcvStatus;
import com.osatum.wbn.domain.service.journal.JournalRatingPolicyService;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import com.osatum.wbn.shared.assembler.AssemblerDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JournalSearchResponseAssembler implements AssemblerDto<JournalDocument, JournalListView, JournalSearchResponseParameters> {

    @Value("${app.cover-path}")
    private String coverPath;
    private final DictionaryLanguageService dictionaryLanguageService;
    private final DictionaryCountryService dictionaryCountryService;
    private final JournalRatingPolicyService journalRatingPolicyService;
    private final ConfigurationService configurationService;
    @Override
    public JournalListView toDto(JournalSearchResponseParameters parameters) {
        JournalListView response = parameters.getDto() == null ? new JournalListView() : parameters.getDto();

        JournalDocument journalDocument = parameters.getEntity();
        response.setId(journalDocument.getId());
        response.setPublicationDays(journalDocument.getPublicationDays());
        response.setCountry(mapCountryView(journalDocument.getCountryCode()));
        response.setDoi(journalDocument.getDoi());

        if (journalDocument.getCover() != null) {
            response.setCover(coverPath + "/" + journalDocument.getCover());
        }

        response.setNumberOfPublications(journalDocument.getNumberOfPublications());
        response.setPercentFullText(journalDocument.getPercentFullText());
        response.setPercentAbstracts(journalDocument.getPercentAbstracts());
        response.setPercentKeywords(journalDocument.getPercentKeywords());
        response.setPercentReferences(journalDocument.getPercentReferences());
        applyTitle(response, journalDocument);
        applyTitles(response, journalDocument);
        applyIssns(response, journalDocument);
        applyPublisher(response, journalDocument);
        applyInstitutions(response, journalDocument);
        applyRatings(response, journalDocument, parameters.getYear());
        applyLanguages(response, journalDocument);
        applyStudyStatus(response, journalDocument);
        return response;
    }


    private void applyTitle(JournalListView response, JournalDocument journalDocument) {
        if (journalDocument.getTitles() == null) {
            return;
        }
        Optional<com.osatum.wbn.infrastructure.elasticsearch.document.journal.Title> oTitle = journalDocument.getTitles().stream()
                .filter(x -> TitleCode.ORIGINAL.equals(x.getCode()))
                .findFirst();
        if (oTitle.isPresent()) {
            response.setTitle(oTitle.get().getContent());
            return;
        }
        oTitle = journalDocument.getTitles().stream()
                .filter(x -> TitleCode.ENGLISH.equals(x.getCode()))
                .findFirst();
        if (oTitle.isPresent()) {
            response.setTitle(oTitle.get().getContent());
            return;
        }
        oTitle = journalDocument.getTitles().stream()
                .filter(x -> TitleCode.SHORT.equals(x.getCode()))
                .findFirst();
        if (oTitle.isPresent()) {
            response.setTitle(oTitle.get().getContent());
            return;
        }
        oTitle = journalDocument.getTitles().stream()
                .filter(x -> TitleCode.SHORT.equals(x.getCode()))
                .findFirst();
        if (oTitle.isPresent()) {
            response.setTitle(oTitle.get().getContent());
        }

    }

    private CountryView mapCountryView(String countryCode) {
        if (countryCode == null) {
            return null;
        }
        DictionaryCountry country = dictionaryCountryService.getByCode(countryCode);
        if (country == null) {
            return null;
        }

        CountryView countryView = new CountryView();
        countryView.setNameEn(country.getNameEn());
        countryView.setNamePl(country.getNamePl());
        countryView.setNameRu(country.getNameRu());
        countryView.setCode(countryCode);

        return countryView;

    }

    private void applyStudyStatus(JournalListView response, JournalDocument journalDocument) {
        for (StudyStatus value : StudyStatus.values()) {
            if (ArrayUtils.contains(value.getCodes(), journalDocument.getWarningCode())) {
                response.setStudyStatus(value);
                return;
            }
        }
    }

    private void applyRatings(JournalListView response, JournalDocument journalDocument, Integer year) {
        if (year == null || journalDocument.getRatings() == null) {
            return;
        }

        Optional<com.osatum.wbn.infrastructure.elasticsearch.document.journal.Rating> oRatingCurrent = journalDocument.getRatings().stream()
                .filter(x -> Rating.ICV_RATING_CODE.equals(x.getRatingCode()))
                .filter(x -> year.equals(x.getYear()))
                .findFirst();

        oRatingCurrent.ifPresent(rating -> {
            response.setCurrentIcvYear(year);
            response.setCurrentIcvPoints(rating.getValueNum());
            if (rating.getRatingStatus() != null){
                response.setCurrentStatus(rating.getRatingStatus());
            }
        });
        if (oRatingCurrent.isEmpty()){
            response.setCurrentStatus(RatingStatus.NOT_EVALUATED);
        }

        Optional<com.osatum.wbn.infrastructure.elasticsearch.document.journal.Rating> oRatingPreviously = journalDocument.getRatings().stream()
                .filter(x -> Rating.ICV_RATING_CODE.equals(x.getRatingCode()))
                .filter(x -> Integer.valueOf(year - 1).equals(x.getYear()))
                .findFirst();

        oRatingPreviously.ifPresent(rating -> {
            response.setPreviousIcvYear(year - 1);
            response.setPreviousIcvPoints(rating.getValueNum());
            if (rating.getRatingStatus() != null){
                response.setPreviousStatus(rating.getRatingStatus());
            }
        });
        if (oRatingPreviously.isEmpty()){
            response.setPreviousStatus(RatingStatus.NOT_EVALUATED);
        }


        Optional<com.osatum.wbn.infrastructure.elasticsearch.document.journal.Rating> oMniswLast = journalDocument.getRatings().stream()
                .filter(x -> Rating.MNW_RATING_CODE.equals(x.getRatingCode()))
                .filter(x -> configurationService.getCurrentMniswYear().equals(x.getYear()))
                .findFirst();

        oMniswLast.ifPresent(rating -> {
            response.setLastMniswList(rating.getValueStr());
            response.setLastMniswPoints(rating.getValueNum());
            response.setLastMniswYear(rating.getYear());

        });


    }

    private void applyTitles(JournalListView response, JournalDocument journalDocument) {
        if (journalDocument.getTitles() == null) {
            return;
        }
        journalDocument.getTitles().forEach(title -> {
            TitleCode code = title.getCode();
            if (TitleCode.ENGLISH.equals(code)) {
                response.setTitleEnglish(title.getContent());
                return;
            }
            if (TitleCode.SHORT.equals(code)) {
                response.setTitleShort(title.getContent());
            }
            if (TitleCode.ORIGINAL.equals(code)) {
                response.setTitleOriginal(title.getContent());
            }
        });
    }

    private void applyIssns(JournalListView response, JournalDocument journalDocument) {
        if (journalDocument.getIssns() == null) {
            return;
        }
        journalDocument.getIssns().forEach(issn -> {
            IssnType type = issn.getType();
            if (IssnType.P.equals(type)) {
                response.setPissn(issn.getContent());
                return;
            }
            if (IssnType.E.equals(type)) {
                response.setEissn(issn.getContent());
            }
        });
    }

    private void applyPublisher(JournalListView response, JournalDocument journalDocument) {
        if (journalDocument.getInstitutions() == null) {
            return;
        }
        response.setPublisher(
                journalDocument.getInstitutions().stream()
                        .filter(x -> JournalInstitution.ROLE_PUBLISHER.equals(x.getRoleCode()))
                        .map(x -> x.getName())
                        .collect(Collectors.joining(", "))
        );
    }

    private void applyInstitutions(JournalListView response, JournalDocument journalDocument) {
        if (journalDocument.getInstitutions() == null) {
            return;
        }
        journalDocument.getInstitutions().stream()
                .filter(x -> JournalInstitution.ROLE_PUBLISHER.equals(x.getRoleCode()))
                .forEach(x -> {
                    if (response.getPublishers() == null) {
                        response.setPublishers(new ArrayList<>());
                    }
                    response.getPublishers().add(x.getName());
                });

        journalDocument.getInstitutions().stream()
                .filter(x -> JournalInstitution.ROLE_EDITORIAL_OFFICE.equals(x.getRoleCode()))
                .forEach(x -> {
                    if (response.getEditorialOffices() == null) {
                        response.setEditorialOffices(new ArrayList<>());
                    }
                    response.getEditorialOffices().add(x.getName());
                });
    }

    private void applyLanguages(JournalListView response, JournalDocument journalDocument) {
        if (journalDocument.getLanguages() == null) {
            return;
        }
        response.setLanguages(new ArrayList<>());
        journalDocument.getLanguages().forEach(x -> {
            DictionaryLanguage dictionaryLanguage = dictionaryLanguageService.getByCode(x.getCode());
            if (dictionaryLanguage != null) {
                LanguageView languageView = new LanguageView();
                languageView.setNameEn(dictionaryLanguage.getNameEn());
                languageView.setNamePl(dictionaryLanguage.getNamePl());
                languageView.setNameRu(dictionaryLanguage.getNameRu());
                languageView.setCode(dictionaryLanguage.getCode());
                response.getLanguages().add(languageView);
            }
        });
    }
}
