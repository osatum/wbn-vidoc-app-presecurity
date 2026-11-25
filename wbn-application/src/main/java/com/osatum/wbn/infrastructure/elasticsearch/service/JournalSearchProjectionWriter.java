package com.osatum.wbn.infrastructure.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.stereotype.Component;

/**
 * Oddziela aktualizację projekcji czasopism od obsługi zapytań wyszukiwarki.
 * Otrzymuje dokumenty przygotowane i zweryfikowane przez mechanizm indeksowania.
 */
@Component
@RequiredArgsConstructor
public class JournalSearchProjectionWriter {
    private final ElasticsearchOperations operations;

    /**
     * Zapisuje pojedynczą projekcję przekazaną przez mechanizm indeksowania czasopisma.
     */
    public void save(JournalDocument document) {
        operations.save(document);
    }

    /**
     * Usuwa wszystkie roczne projekcje wskazanego czasopisma, aby po zmianie nie pozostały nieaktualne lata.
     */
    public void deleteJournalDocuments(Long journalId) {
        operations.delete(DeleteQuery.builder(NativeQuery.builder()
                .withQuery(Query.of(query -> query.term(value -> value.field("id").value(journalId))))
                .build()).build(), JournalDocument.class);
    }

    /**
     * Uwidacznia zapisane zmiany w kolejnych wyszukiwaniach bez przebudowy całego indeksu.
     */
    public void refresh() {
        operations.indexOps(JournalDocument.class).refresh();
    }
}
