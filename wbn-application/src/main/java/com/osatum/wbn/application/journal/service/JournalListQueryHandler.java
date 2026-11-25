package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.query.JournalSearchCriteria;
import com.osatum.wbn.application.journal.view.JournalListView;
import com.osatum.wbn.application.journal.view.JournalSearchMetadata;
import com.osatum.wbn.application.journal.view.JournalSearchCounts;
import com.osatum.wbn.application.shared.list.PageResult;

/**
 * Kontrakt odczytu rocznego indeksu P1: strona wyników i metadane filtrów korzystają z konfiguracji backendu.
 */
public interface JournalListQueryHandler {
    PageResult<JournalListView> execute(JournalSearchCriteria criteria);

    /**
     * Zwraca rzeczywiste zakresy ocen oraz globalne liczniki wartości, bez stosowania filtrów formularza.
     */
    JournalSearchMetadata getSearchMetadata();

    JournalSearchMetadata getSearchMetadata(Integer surveyYear);

    JournalSearchCounts getSearchCounts(JournalSearchCriteria criteria);
}
