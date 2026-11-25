package com.osatum.wbn.infrastructure.elasticsearch.repository;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JournalSearchPageLoaderTest {
    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final JournalSearchPageLoader loader = new JournalSearchPageLoader(operations);
    private final Query filter = Query.of(query -> query.term(value -> value.field("surveyYear").value(2024)));
    private final List<SortOptions> sort = List.of(SortOptions.of(value -> value.field(field -> field.field("id"))));

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 999})
    void usesTheStandardWindowAndExactCountForShallowPages(int page) {
        respond(hits(12037, 10, List.of(10)));
        assertThat(loader.load(filter, sort, page, 10).total()).isEqualTo(12037);
        NativeQuery query = queries(1).getFirst();
        assertThat(query.getPageable().getOffset()).isEqualTo((long) page * 10);
        assertThat(query.getPageable().getPageSize()).isEqualTo(10);
        assertThat(query.getTrackTotalHits()).isTrue();
        assertThat(query.getSearchAfter()).isNullOrEmpty();
    }

    @Test
    void startsTheFirstDeepPageAfterTheWindowAnchorAndFetchesOnlyTenFullDocuments() {
        respond(hits(12037, 1, List.of(10000)), hits(12037, 10, List.of(10010)));
        assertThat(loader.load(filter, sort, 1000, 10).hits()).hasSize(10);
        List<NativeQuery> queries = queries(2);
        assertThat(queries.getFirst().getPageable().getOffset()).isEqualTo(9999);
        assertThat(queries.getFirst().getSourceFilter().fetchSource()).isFalse();
        assertThat(queries.getLast().getPageable().getOffset()).isZero();
        assertThat(queries.getLast().getSearchAfter()).containsExactly(10000);
        assertThat(queries.getLast().getSourceFilter()).isNull();
    }

    @Test
    void seeksInBoundedBatchesWithoutChangingFiltersSortOrPersistingIds() {
        respond(
                hits(20000, 1, List.of(10000)), hits(0, 1000, List.of(11000)), hits(0, 1000, List.of(12000)),
                hits(0, 510, List.of(12510)), hits(20000, 10, List.of(12520)));
        assertThat(loader.load(filter, sort, 1251, 10).total()).isEqualTo(20000);
        List<NativeQuery> queries = queries(5);
        assertThat(queries).allSatisfy(query -> {
            assertThat(query.getQuery()).isSameAs(filter);
            assertThat(query.getSortOptions()).isEqualTo(sort);
            assertThat(query.getPointInTime()).isNull();
        });
        assertThat(queries).extracting(query -> query.getPageable().getPageSize()).containsExactly(1, 1000, 1000, 510, 10);
        assertThat(queries).extracting(NativeQuery::getTrackTotalHits).containsExactly(true, false, false, false, true);
        assertThat(queries.get(3).getSearchAfter()).containsExactly(12000);
        assertThat(queries.getLast().getSearchAfter()).containsExactly(12510);
    }

    @ParameterizedTest
    @ValueSource(ints = {1001, Integer.MAX_VALUE})
    void returnsAnEmptyOutOfRangePageWithoutOverflowOrScanningTheWholeIndex(int page) {
        respond(hits(10001, 1, List.of(10000)));
        var result = loader.load(filter, sort, page, 10);
        assertThat(result.hits()).isEmpty();
        assertThat(result.total()).isEqualTo(10001);
        queries(1);
    }

    @Test
    void preservesNullSortValuesAndRebuildsTheCursorForEveryRequest() {
        respond(
                hits(12000, 1, Arrays.asList(null, 10000)), hits(12000, 10, List.of(10010)),
                hits(12001, 1, Arrays.asList(null, 9999)), hits(12001, 10, List.of(10009)));
        assertThat(loader.load(filter, sort, 1000, 10).total()).isEqualTo(12000);
        assertThat(loader.load(filter, sort, 1000, 10).total()).isEqualTo(12001);
        List<NativeQuery> queries = queries(4);
        assertThat(queries.get(1).getSearchAfter()).containsExactly(null, 10000);
        assertThat(queries.get(3).getSearchAfter()).containsExactly(null, 9999);
    }

    @Test
    void handlesAnEmptyOrShrinkingSetWithoutAnUnboundedCursorLoop() {
        respond(
                hits(0, 0, List.of()), hits(15000, 1, List.of(10000)), hits(0, 5, List.of(10005)));
        assertThat(loader.load(filter, sort, 1000, 10).hits()).isEmpty();
        assertThat(loader.load(filter, sort, 1200, 10).hits()).isEmpty();
        queries(3);
    }

    @Test
    void rejectsMissingSortValuesAndNegativePagesInsteadOfReturningAnotherPage() {
        assertThatIllegalArgumentException().isThrownBy(() -> loader.load(filter, sort, -1, 10));
        verifyNoInteractions(operations);
        respond(hits(12000, 1, List.of()));
        assertThatIllegalStateException().isThrownBy(() -> loader.load(filter, sort, 1000, 10));
    }

    @SafeVarargs
    private void respond(SearchHits<JournalDocument>... responses) {
        when(operations.search(any(NativeQuery.class), eq(JournalDocument.class)))
                .thenReturn(responses[0], Arrays.copyOfRange(responses, 1, responses.length));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 25, 50, 100})
    void usesTheRequestedPageSizeAndOffsetWithinTheWindow(int size) {
        respond(hits(1199, size, List.of(size * 3)));
        assertThat(loader.load(filter, sort, 2, size).hits()).hasSize(size);
        var query = queries(1).getFirst();
        assertThat(query.getPageable().getOffset()).isEqualTo(2L * size);
        assertThat(query.getPageable().getPageSize()).isEqualTo(size);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 25, 50, 100})
    void keepsTheRequestedPageSizeWhenCrossingTheElasticsearchWindow(int size) {
        respond(hits(12037, 1, List.of(10000)), hits(12037, size, List.of(10000 + size)));
        var result = loader.load(filter, sort, 10000 / size, size);
        assertThat(result.hits()).hasSize(size);
        var queries = queries(2);
        assertThat(queries.getFirst().getPageable().getOffset()).isEqualTo(9999);
        assertThat(queries.getLast().getSearchAfter()).containsExactly(10000);
        assertThat(queries.getLast().getPageable().getPageSize()).isEqualTo(size);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 11, 15, 99, 101})
    void rejectsUnsupportedPageSizesBeforeAccessingElasticsearch(int size) {
        assertThatIllegalArgumentException().isThrownBy(() -> loader.load(filter, sort, 0, size));
        verifyNoInteractions(operations);
    }

    private List<NativeQuery> queries(int expected) {
        ArgumentCaptor<NativeQuery> captured = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations, times(expected)).search(captured.capture(), eq(JournalDocument.class));
        return captured.getAllValues();
    }

    private SearchHits<JournalDocument> hits(long total, int count, List<Object> cursor) {
        SearchHits<JournalDocument> hits = mock(SearchHits.class);
        SearchHit<JournalDocument> hit = mock(SearchHit.class);
        when(hit.getSortValues()).thenReturn(cursor);
        when(hits.getSearchHits()).thenReturn(IntStream.range(0, count).mapToObj(position -> hit).toList());
        when(hits.hasSearchHits()).thenReturn(count > 0);
        when(hits.getTotalHits()).thenReturn(total);
        return hits;
    }
}
