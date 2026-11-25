package com.osatum.wbn.domain.model.journal;
/* Typ czasopisma */
public enum JournalKind implements java.io.Serializable{
    /* Czasopismo naukowe */
    SCIENTIFIC("s"),
    /* Czasopismo inne */
    OTHER("o"),
    /* Czasopismo popularno-naukowe */
    POPULAR_SCIENCE("p");

    private static final long serialVersionUID = 1L;
    private String code;

    JournalKind(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
