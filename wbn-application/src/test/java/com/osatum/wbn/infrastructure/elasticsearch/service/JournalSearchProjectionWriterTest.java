package com.osatum.wbn.infrastructure.elasticsearch.service;

import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JournalSearchProjectionWriterTest {
    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final JournalSearchProjectionWriter writer = new JournalSearchProjectionWriter(operations);

    @Test
    void savesThePreparedDocumentWithoutChangingItOrRefreshingTheIndex() {
        var document = new JournalDocument();
        document.setId(123L);
        document.setDocumentId("123:2024");
        document.setSurveyYear(2024);

        writer.save(document);

        verify(operations).save(same(document));
        verifyNoMoreInteractions(operations);
        assertThat(document.getId()).isEqualTo(123L);
        assertThat(document.getDocumentId()).isEqualTo("123:2024");
        assertThat(document.getSurveyYear()).isEqualTo(2024);
    }

    @Test
    void deletesOnlyDocumentsOfTheChangedJournalAcrossAllSurveyYears() {
        writer.deleteJournalDocuments(123L);

        var captured = ArgumentCaptor.forClass(DeleteQuery.class);
        verify(operations).delete(captured.capture(), eq(JournalDocument.class));
        var query = ((NativeQuery) captured.getValue().getQuery()).getQuery();
        assertThat(query.isTerm()).isTrue();
        assertThat(query.term().field()).isEqualTo("id");
        assertThat(query.term().value().longValue()).isEqualTo(123);
        verifyNoMoreInteractions(operations);
    }

    @Test
    void refreshesOnlyTheJournalIndexWhenExplicitlyRequested() {
        var index = mock(IndexOperations.class);
        when(operations.indexOps(JournalDocument.class)).thenReturn(index);

        writer.refresh();

        verify(operations).indexOps(JournalDocument.class);
        verify(index).refresh();
        verifyNoMoreInteractions(operations, index);
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "delete", "refresh"})
    void propagatesInfrastructureFailuresToTheCaller(String operation) {
        var failure = new IllegalStateException("test index failure");
        var document = new JournalDocument();
        Runnable action = switch (operation) {
            case "save" -> {
                doThrow(failure).when(operations).save(document);
                yield () -> writer.save(document);
            }
            case "delete" -> {
                doThrow(failure).when(operations).delete(any(DeleteQuery.class), eq(JournalDocument.class));
                yield () -> writer.deleteJournalDocuments(123L);
            }
            case "refresh" -> {
                var index = mock(IndexOperations.class);
                when(operations.indexOps(JournalDocument.class)).thenReturn(index);
                doThrow(failure).when(index).refresh();
                yield writer::refresh;
            }
            default -> throw new IllegalArgumentException(operation);
        };

        assertThatThrownBy(action::run).isSameAs(failure);
    }
}
