package com.osatum.wbn.domain.model.survey.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class Language implements Serializable {
    private static final long serialVersionUID = 1L;
    private String code;
    private String nameEn;
    private String namePl;
    private String nameRu;
}
