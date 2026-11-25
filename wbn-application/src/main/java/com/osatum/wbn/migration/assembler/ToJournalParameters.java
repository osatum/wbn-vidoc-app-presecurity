package com.osatum.wbn.migration.assembler;

import com.osatum.jml.migration.dto.journal.JournalPayload;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.shared.assembler.DefaultParameters;

public class ToJournalParameters extends DefaultParameters<Journal, JournalPayload> {
    private final String schemaVersion;

    public ToJournalParameters(Journal entity, JournalPayload dto) {
        this(entity, dto, "1.0");
    }

    public ToJournalParameters(Journal entity, JournalPayload dto, String schemaVersion) {
        super(entity, dto);
        this.schemaVersion = schemaVersion;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }
}
