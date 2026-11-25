package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.dictionary.service.DictionaryScientificCategoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryRepositoryService;
import com.osatum.wbn.application.journal.repository.DecisionPopulationRepository;
import com.osatum.wbn.application.journal.view.JournalDecisionAnnualView;
import java.util.stream.IntStream;
import static org.mockito.Mockito.*;

/** Buduje syntetyczne roczniki do testów zapisu i odczytu benchmarków, bez korzystania z bazy użytkownika. */
public final class DecisionPreparedTestSupport {
    private DecisionPreparedTestSupport() { }
    public static DecisionPreparedData.Population population(int year, int count, int days) {
        var repository = mock(DecisionPopulationRepository.class);
        when(repository.findCandidates(year)).thenReturn(IntStream.rangeClosed(1, count).mapToObj(id ->
                new DecisionPopulationRepository.Entry((long) id, 81.125, DecisionFixture.survey(year, days),
                        (long) (year * 10000 + id), "PUBLISHED", 81.125, null)).toList());
        var dictionary = dictionary();
        var fees = new DecisionFeeReader();
        return new DecisionPopulationReader(repository, dictionary,
                new DecisionProfileReader(dictionary, mock(DictionaryRepositoryService.class)), new DecisionMetricReader(fees), fees).read(year);
    }
    public static JournalDecisionAnnualView annual(int year, int days) {
        var fees = new DecisionFeeReader();
        return new DecisionAnnualReader(new DecisionProfileReader(dictionary(), mock(DictionaryRepositoryService.class)), fees)
                .read(DecisionFixture.survey(year, days), year);
    }
    private static DictionaryScientificCategoryService dictionary() {
        var dictionary = mock(DictionaryScientificCategoryService.class);
        when(dictionary.getAll()).thenReturn(DecisionFixture.categories());
        for (var category : DecisionFixture.categories()) when(dictionary.getById(category.getId())).thenReturn(category);
        return dictionary;
    }
}
