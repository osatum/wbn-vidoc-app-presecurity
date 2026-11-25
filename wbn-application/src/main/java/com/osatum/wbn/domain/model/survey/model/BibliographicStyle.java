package com.osatum.wbn.domain.model.survey.model;

import com.fasterxml.jackson.annotation.JsonCreator;
/* Style bibliograficzne */
public enum BibliographicStyle {
    Cell,
    Science,
    Nature,
    MLA,
    Bibtex,
    Harvard,
    APA,
    Vancouver,
    ISO690,
    Chicago,
    IEEE,
    Other,
    Oxford,
    ACM,
    AMA;

    @JsonCreator
    public static BibliographicStyle fromValue(String text) {
        for (BibliographicStyle b : BibliographicStyle.values()) {
            if (String.valueOf(b.name()).equals(text)) {
                return b;
            }
        }
        return null;
    }
}
