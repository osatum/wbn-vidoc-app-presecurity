package com.osatum.wbn.domain.model.survey.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FirstNumberMonth {


    /* Grudzie roku poprzedniego */
    DEC_YEAR_BEFORE("DEC_YEAR_BEFORE"),
    /* Styczeń */
    JANUARY("JANUARY"),
    /* Luty */
    FEBRUARY("FEBRUARY"),
    /* Marzec */
    MARCH("MARCH"),
    /* Kwiecień */
    APRIL("APRIL"),
    /* Maj */
    MAY("MAY"),
    /* Czerwiec */
    JUNE("JUNE"),
    /* Lipiec */
    JULY("JULY"),
    /* Sierpień */
    AUGUST("AUGUST"),
    /* Wrzesień */
    SEPTEMBER("SEPTEMBER"),
    /* Październik */
    OCTOBER("OCTOBER"),
    /* Listopad */
    NOVEMBER("NOVEMBER"),
    /* Grudzień */
    DECEMBER("DECEMBER"),
    /* Styczeń roku kolejnego */
    JAN_NEXT_YEAR("JAN_NEXT_YEAR"),
    /* Inny */
    OTHER("OTHER");

    private String value;

    FirstNumberMonth(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static FirstNumberMonth fromValue(String text) {
        for (FirstNumberMonth b : FirstNumberMonth.values()) {
            if (String.valueOf(b.value).equals(text)) {
                return b;
            }
        }
        return null;
    }
}
