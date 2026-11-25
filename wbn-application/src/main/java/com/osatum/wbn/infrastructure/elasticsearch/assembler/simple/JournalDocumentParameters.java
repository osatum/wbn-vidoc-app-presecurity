package com.osatum.wbn.infrastructure.elasticsearch.assembler.simple;


import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import com.osatum.wbn.shared.assembler.DefaultParameters;

public class JournalDocumentParameters extends DefaultParameters<Journal, JournalDocument> {
    public JournalDocumentParameters(Journal entity, JournalDocument dto) {
        super(entity, dto);
    }
}
