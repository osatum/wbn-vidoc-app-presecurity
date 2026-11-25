package com.osatum.wbn.domain.model.journal;

import java.io.Serializable;

public enum PersonPosition implements Serializable {

    /* Redaktor naczelny */
    EDITOR_IN_CHIEF("c"),

    /* Redaktor */
    ASSISTANCE_EDITOR("e"),

    /* Zastepca redaktora naczelnego */
    DEPUTY_EDITOR_IN_CHIEF("d"),

    /* Redaktor prowadzcy */
    LEAD_EDITOR("l"),

    /* Sekretariat*/
    OFFICE("o"),

    /*Sekretarz redakcji*/
    EDITORIAL_SECRETARY("s");

    private String code;

    PersonPosition(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

}
