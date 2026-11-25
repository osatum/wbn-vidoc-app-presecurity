package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.dictionary.service.DictionaryRepositoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryScientificCategoryService;
import com.osatum.wbn.application.journal.repository.DecisionPopulationRepository;
import com.osatum.wbn.application.journal.repository.DecisionPopulationRepository.Entry;
import com.osatum.wbn.domain.model.survey.model.Category;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.osatum.wbn.application.journal.service.DecisionMetric.*;

class DecisionPopulationReaderTest {
    @Test
    void deduplicatesMembershipsAndIdsKeepsLocalValuesAndReadsOneAnnualPopulationWithoutFx() {
        var repository = mock(DecisionPopulationRepository.class);
        var categories = mock(DictionaryScientificCategoryService.class);
        when(categories.getAll()).thenReturn(DecisionFixture.categories());
        var fees = new DecisionFeeReader();
        var profiles = new DecisionProfileReader(categories, mock(DictionaryRepositoryService.class));
        var reader = new DecisionPopulationReader(repository, categories, profiles, new DecisionMetricReader(fees), fees);
        var first = DecisionFixture.survey(2024, 0);
        first.getSupplementaryData().setScientificCategories(List.of(new Category(1082L, null, null, null, null, true),
                new Category(1008L, null, null, null, null, false), new Category(2000L, null, null, null, null, false)));
        var second = DecisionFixture.survey(2024, 20); second.getStandards().getAvgManuscriptTimes().setReview(-1);
        var fifth = DecisionFixture.survey(2024, 20);
        fifth.getSupplementaryData().setScientificCategories(List.of(new Category(9999L, null, null, null, null, true),
                new Category(1008L, null, null, null, null, false)));
        when(repository.findCandidates(2024)).thenReturn(List.of(new Entry(1L, 0.0, first), new Entry(1L, 0.0, first),
                new Entry(2L, null, second), new Entry(3L, 80.0, DecisionFixture.survey(2025, 999)),
                new Entry(4L, 8.0, first), new Entry(4L, 9.0, first), new Entry(5L, 10.0, fifth)));
        var prepared = reader.read(2024);
        var result = prepared.observations();
        assertThat(result).extracting(DecisionObservation::journalId).containsExactly(1L, 2L, 5L);
        assertThat(result.getFirst().groups()).containsExactlyInAnyOrder(1008L, 2000L);
        assertThat(result.getFirst().values()).containsEntry(ICV, 0.0).containsEntry(INITIAL_ASSESSMENT, 0.0)
                .containsEntry(FOREIGN_AUTHORS, .5).containsEntry(FOREIGN_COUNCIL, 0.0).containsEntry(SUBMISSION_FEE, 0.0);
        assertThat(result.get(1).values()).doesNotContainKeys(REVIEW, ICV).containsKey(FOREIGN_AUTHORS);
        assertThat(result.get(2).groups()).containsExactly(1008L);
        assertThat(prepared.inputs()).hasSize(5);
        assertThat(prepared.inputs().get(2).exclusionReason()).isEqualTo("ASSESSMENT_YEAR_MISMATCH");
        assertThat(prepared.inputs().get(3).exclusionReason()).isEqualTo("AMBIGUOUS_OBSERVATIONS");
        assertThat(prepared.inputs().get(1).metrics().get(REVIEW).status().name()).isEqualTo("INVALID");
        verify(repository, times(1)).findCandidates(2024); verify(categories, times(1)).getAll();
        verify(categories, never()).getById(any());
        when(repository.findCandidates(2025)).thenReturn(List.of(new Entry(1L, 90.0, DecisionFixture.survey(2025, 77))));
        assertThat(reader.read(2025).observations().getFirst().values()).containsEntry(REVIEW, 77.0);
        assertThat(first.getStandards().getAvgManuscriptTimes().getReview()).isZero();
    }
}
