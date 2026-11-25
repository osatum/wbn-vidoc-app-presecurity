package com.osatum.wbn.application.journal.view;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InstitutionView {
    private String name;
    private String department;
    private String street;
    private String postCode;
    private String city;
    private String countryCode;
    private CountryView country;
    private String email;
    private String phone;
    private String www;
    private String roleCode;
}
