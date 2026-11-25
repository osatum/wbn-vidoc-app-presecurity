package com.osatum.wbn.infrastructure.elasticsearch.repository;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.osatum.wbn.application.journal.query.JournalSearchCriteriaRules;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pobiera dowolną stronę wybranego rozmiaru, także po przekroczeniu okna 10 000 rekordów Elasticsearch.
 * Nie przechowuje migawki ani listy identyfikatorów; kolejne wywołania korzystają z aktualnych danych indeksu.
 */
@Component
@RequiredArgsConstructor
public class JournalSearchPageLoader {
    private static final int RESULT_WINDOW = 10000;
    private static final int SEEK_BATCH_SIZE = 1000;

    private final ElasticsearchOperations operations;

    /**
     * Dla bliskich stron stosuje zwykły offset, a dla dalszych dochodzi do żądanej pozycji przez search_after.
     * Numery stron są zerowe, a wynik zawiera pełny licznik dopasowań, nie rozmiar pobranej partii.
     */
    public Result load(Query filter, List<SortOptions> sort, int page, int size) {
        if (page < 0 || !JournalSearchCriteriaRules.isValidPageSize(size)) {
            throw new IllegalArgumentException("Page must not be negative and page size must be supported");
        }
        long offset = (long) page * size;
        if (offset + size <= RESULT_WINDOW) {
            return result(operations.search(NativeQuery.builder().withQuery(filter).withSort(sort)
                    .withPageable(PageRequest.of(page, size)).withTrackTotalHits(true).build(), JournalDocument.class));
        }

        // Punkt startowy i pomijane partie nie pobierają treści dokumentów, tylko wartości sortowania.
        SearchHits<JournalDocument> anchor = operations.search(NativeQuery.builder().withQuery(filter).withSort(sort)
                .withPageable(PageRequest.of(RESULT_WINDOW - 1, 1)).withTrackTotalHits(true)
                .withSourceFilter(FetchSourceFilter.of(false, null, null)).build(), JournalDocument.class);
        if (offset >= anchor.getTotalHits() || !anchor.hasSearchHits()) {
            return new Result(List.of(), anchor.getTotalHits());
        }
        List<Object> after = cursor(anchor);
        long skipped = RESULT_WINDOW;
        while (skipped < offset) {
            int batchSize = (int) Math.min(SEEK_BATCH_SIZE, offset - skipped);
            SearchHits<JournalDocument> batch = operations.search(NativeQuery.builder().withQuery(filter).withSort(sort)
                    .withPageable(PageRequest.of(0, batchSize)).withSearchAfter(after).withTrackTotalHits(false)
                    .withSourceFilter(FetchSourceFilter.of(false, null, null)).build(), JournalDocument.class);
            if (batch.getSearchHits().size() < batchSize) {
                return new Result(List.of(), anchor.getTotalHits());
            }
            skipped += batchSize;
            after = cursor(batch);
        }
        return result(operations.search(NativeQuery.builder().withQuery(filter).withSort(sort)
                .withPageable(PageRequest.of(0, size)).withSearchAfter(after)
                .withTrackTotalHits(true).build(), JournalDocument.class));
    }

    /**
     * Przekazuje pełny klucz sortowania ostatniego trafienia; końcowy identyfikator rozstrzyga remisy.
     */
    private List<Object> cursor(SearchHits<JournalDocument> hits) {
        List<Object> values = hits.getSearchHits().getLast().getSortValues();
        if (values.isEmpty()) {
            throw new IllegalStateException("Search pagination requires sort values");
        }
        return new ArrayList<>(values);
    }

    private Result result(SearchHits<JournalDocument> hits) {
        return new Result(hits.getSearchHits(), hits.getTotalHits());
    }

    public record Result(List<SearchHit<JournalDocument>> hits, long total) {
    }
}
