package com.osatum.wbn.domain.model.survey.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Dane instytucji (wydawcy / jednostki) – {@code InstitutionSurvey} w modelu źródłowym.
 */
@Data
public class Institution implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String department;
    private String gicid;
    private String street;
    private String postCode;
    private String city;
    private String state;
    private Country country;
    private String email;
    private String phone;
    private String www;
    private String type;
}
