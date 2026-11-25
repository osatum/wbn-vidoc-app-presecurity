package com.osatum.wbn.infrastructure.elasticsearch.assembler;

import com.osatum.wbn.application.journal.view.JournalListView;

import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import com.osatum.wbn.shared.assembler.DefaultParameters;

public class JournalSearchResponseParameters extends DefaultParameters<JournalDocument, JournalListView> {
    public JournalSearchResponseParameters(JournalDocument entity, Integer year, String coverPath) {
        super(entity, null);
        this.year = year;
        this.coverPath = coverPath;
    }



    private final Integer year;
    private final String coverPath;

    public Integer getYear(){
        return this.year;
    }

    public String getCoverPath() {
        return coverPath;
    }
}
