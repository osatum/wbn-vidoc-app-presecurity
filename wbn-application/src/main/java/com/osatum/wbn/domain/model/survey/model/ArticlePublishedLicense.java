package com.osatum.wbn.domain.model.survey.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
/* licencja publikowanych artykułow */
public enum ArticlePublishedLicense {

    NONE("NONE"),
    CC_BY("CC_BY"),
    CC_BY_SA("CC_BY_SA"),
    CC_BY_ND("CC_BY_ND"),
    CC_BY_NC("CC_BY_NC"),
    CC_BY_NC_SA("CC_BY_NC_SA"),
    CC_BY_NC_ND("CC_BY_NC_ND"),
    AUTHOR_PREFERENCE("AUTHOR_PREFERENCE");

    private String value;

    ArticlePublishedLicense(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static ArticlePublishedLicense fromValue(String text) {
        for (ArticlePublishedLicense b : ArticlePublishedLicense.values()) {
            if (String.valueOf(b.value).equals(text)) {
                return b;
            }
        }
        return null;
    }
}
