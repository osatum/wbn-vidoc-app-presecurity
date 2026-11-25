package com.osatum.wbn.application.journal.view;

import com.osatum.wbn.domain.model.journal.PersonPosition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PersonView {

    private String fullName;
    private String title;
    private String firstName;
    private String middleName;
    private String lastName;
    private String countryCode;
    private String email;
    private String department;
    private String statusCode;
    private String recordSource;
    private PersonPosition personPosition;
}
