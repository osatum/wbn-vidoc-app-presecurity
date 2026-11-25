package com.osatum.wbn.domain.model.questionnaire;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Setter
@Getter
@NoArgsConstructor
/* Dane uzytkownika */
public class UserData {
    private Long sourceId;
    /* login */
    private String login;
    /* email */
    private String email;
    /* imie */
    private String name;
    /* nazwisko */
    private String surname;
}
