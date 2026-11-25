package com.osatum.wbn.infrastructure.elasticsearch.repository.springdata;

import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalDocumentRepository extends ElasticsearchRepository<JournalDocument, String> {
}
