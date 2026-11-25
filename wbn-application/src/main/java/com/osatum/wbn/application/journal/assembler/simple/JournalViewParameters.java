package com.osatum.wbn.application.journal.assembler.simple;

import com.osatum.wbn.application.journal.view.JournalView;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.shared.assembler.DefaultParameters;

public class JournalViewParameters extends DefaultParameters<Journal, JournalView> {
    public JournalViewParameters(Journal entity, JournalView dto) {
        super(entity, dto);
    }
}
