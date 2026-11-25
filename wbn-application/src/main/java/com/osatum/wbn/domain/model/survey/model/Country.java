package com.osatum.wbn.domain.model.survey.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
@Getter
@Setter
/* Klasa kraju */
public class Country implements Serializable {
    private static final long serialVersionUID = 1L;
    /* kod */
    private String code;
    /* kod 3 znakowy */
    private String codeAlpha3;
    /* nazwa PL */
    private String namePl;
    /* nazwa EN */
    private String nameEn;
    /* nazwa RU */
    private String nameRu;
}
