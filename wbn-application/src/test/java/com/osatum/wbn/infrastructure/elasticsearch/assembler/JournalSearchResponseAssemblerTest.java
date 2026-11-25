package com.osatum.wbn.infrastructure.elasticsearch.assembler;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.application.dictionary.service.DictionaryLanguageService;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.service.journal.JournalRatingPolicyService;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.Rating;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JournalSearchResponseAssemblerTest {
    private final ConfigurationService configuration = new ConfigurationService();
    private final JournalSearchResponseAssembler assembler = new JournalSearchResponseAssembler(
            mock(DictionaryLanguageService.class), mock(DictionaryCountryService.class),
            new JournalRatingPolicyService(), configuration);

    @Test
    void usesTheConfiguredMniswListInsteadOfTheLatestHistoricalRating() {
        var document = new JournalDocument();
        document.setId(123L);
        document.setSurveyYear(2025);
        document.setPublicationDays(0);
        var icv = rating("ICV", 2024, 80.25);
        icv.setRatingStatus(RatingStatus.INDEXED);
        var mnisw = rating("MNW", 2024, 70.0);
        mnisw.setRatingStatus(RatingStatus.NOT_INDEXED);
        document.setRatings(List.of(rating("MNW", 2025, 200.0), icv, mnisw, rating("MNW", 2023, 140.0)));

        var result = assembler.toDto(new JournalSearchResponseParameters(document, 2024, null));

        assertThat(result.getLastMniswPoints()).isEqualTo(70.0);
        assertThat(result.getLastMniswYear()).isEqualTo(2024);
        assertThat(result.getCurrentIcvPoints()).isEqualTo(80.25);
        assertThat(result.getCurrentStatus()).isEqualTo(RatingStatus.INDEXED);
        assertThat(result.getId()).isEqualTo(123L);
        assertThat(result.getPublicationDays()).isZero();
    }

    @Test
    void doesNotSubstituteAnOlderMniswListForAMissingCurrentEntry() {
        var document = new JournalDocument();
        document.setRatings(List.of(rating("MNW", 2023, 140.0)));

        var result = assembler.toDto(new JournalSearchResponseParameters(document, 2024, null));

        assertThat(result.getLastMniswPoints()).isNull();
        assertThat(result.getLastMniswYear()).isNull();
        assertThat(result.getCurrentStatus()).isEqualTo(RatingStatus.NOT_EVALUATED);
        assertThat(result.getPublicationDays()).isNull();
    }

    private Rating rating(String code, int year, double points) {
        var rating = new Rating();
        rating.setRatingCode(code);
        rating.setYear(year);
        rating.setValueNum(points);
        return rating;
    }
}
