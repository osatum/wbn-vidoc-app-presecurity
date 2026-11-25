package com.osatum.wbn.application.journal.view;

public enum StudyStatus {
    OK (new String[]{"OK","*", ""}),
    TRANSPARENCY_WARNING(new String[]{"W1", "W2", "W3"}),
    PREDATORY_ALERT(new String[]{"P1", "P2", "P3"});

    StudyStatus(String[] codes) {
        this.codes = codes;
    }

    private String[] codes;

    public String[] getCodes() {
        return codes;
    }
}
