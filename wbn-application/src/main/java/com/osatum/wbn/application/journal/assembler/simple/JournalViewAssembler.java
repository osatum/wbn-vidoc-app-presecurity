package com.osatum.wbn.application.journal.assembler.simple;

import com.osatum.wbn.application.journal.view.JournalView;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.shared.assembler.AssemblerDto;

public class JournalViewAssembler implements AssemblerDto<Journal, JournalView, JournalViewParameters> {
    @Override
    public JournalView toDto(JournalViewParameters parameters) {
        JournalView response = parameters.getDto() == null ? new JournalView() : parameters.getDto();
        Journal journal = parameters.getEntity();
        return response;
    }
}
