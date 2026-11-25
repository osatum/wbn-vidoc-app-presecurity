package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.dictionary.service.DictionaryMniswDisciplineService;
import com.osatum.wbn.application.journal.view.DecisionValue.State;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.journal.*;
import com.osatum.wbn.domain.model.dictionary.DictionaryMniswDiscipline;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionRatingReaderTest {
    private final DictionaryMniswDisciplineService dictionary = mock();
    private final DecisionRatingReader reader = new DecisionRatingReader(new ConfigurationService(), dictionary);
    @Test
    void mniswUsesConfiguredYearAndSameRatingForPointsAndDisciplineNames() {
        var current = rating("MNW", 2024, 70.0, null); var newer = rating("MNW", 2025, 100.0, null);
        var assignment = new MniswDiscipline(); assignment.setDisciplineCode("2021");
        var second = new MniswDiscipline(); second.setDisciplineCode("2022");
        current.setMniswDisciplines(Set.of(second, assignment));
        var newerAssignment = new MniswDiscipline(); newerAssignment.setDisciplineCode("OTHER_YEAR");
        newer.setMniswDisciplines(Set.of(newerAssignment));
        var entry = new DictionaryMniswDiscipline(); entry.setCode("2021"); entry.setNamePl("Nauka"); entry.setNameEn("Science");
        when(dictionary.getByCode("2021")).thenReturn(entry);
        var secondEntry = new DictionaryMniswDiscipline(); secondEntry.setCode("2022"); secondEntry.setNameEn("Medicine");
        when(dictionary.getByCode("2022")).thenReturn(secondEntry);
        var result = reader.mnisw(List.of(current, newer));
        assertThat(result.year()).isEqualTo(2024); assertThat(result.points()).isEqualTo(70.0); assertThat(result.listed()).isTrue();
        assertThat(result.disciplines()).extracting(value -> value.code()).containsExactly("2021", "2022");
        assertThat(result.disciplines()).extracting(value -> value.nameEn()).containsExactly("Science", "Medicine");
        verify(dictionary, never()).getByCode("OTHER_YEAR");
        current.setMniswDisciplines(Set.of()); assertThat(reader.mnisw(List.of(current)).listed()).isTrue();
        assertThat(reader.mnisw(List.of(newer)).listed()).isFalse();
        assertThat(reader.mnisw(List.of(current, rating("MNW", 2024, 40.0, null))).status()).isEqualTo(State.INVALID);
    }
    @Test
    void historyOnlyContainsActualEvaluationsAndNeverAddsMissingOrAmbiguousYears() {
        var result = reader.history(List.of(rating("ICV", 2025, 81.125, RatingStatus.INDEXED),
                rating("ICV", 2024, null, RatingStatus.NOT_EVALUATED), rating("ICV", 2022, null, RatingStatus.NOT_INDEXED),
                rating("ICV", 2021, 0.0, RatingStatus.INDEXED), rating("ICV", 2020, 1.0, RatingStatus.INDEXED),
                rating("ICV", 2020, 2.0, RatingStatus.INDEXED)));
        assertThat(result).extracting(value -> value.year()).containsExactly(2025, 2022, 2021);
        assertThat(result.getFirst().value()).isEqualTo(81.125); assertThat(result.get(2).value()).isZero();
    }
    private Rating rating(String code, int year, Double points, RatingStatus status) {
        var result = new Rating(); result.setRatingCode(code); result.setYear(year); result.setValueNum(points); result.setRatingStatus(status); return result;
    }
}
