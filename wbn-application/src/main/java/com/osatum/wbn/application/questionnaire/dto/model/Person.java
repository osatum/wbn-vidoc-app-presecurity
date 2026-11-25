package com.osatum.wbn.application.questionnaire.dto.model;

import com.osatum.wbn.application.journal.view.CountryView;
import com.osatum.wbn.domain.model.journal.PersonPosition;
import lombok.Data;

import java.io.Serializable;

/**
 * Osoba w ankiecie (np. redaktor) – uproszczony DTO niezależny od słowników i enumów modelu źródłowego.
 */
@Data
public class Person implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Identyfikator GICID (jeśli występuje). */
    private String gicid;

    /** Tytuł naukowy/stanowisko (np. dr, prof.). */
    private String title;

    /** Imię. */
    private String firstName;

    /** Drugie imię. */
    private String middleName;

    /** Nazwisko. */
    private String lastName;

    /** Kod kraju. */
    private CountryView country;

    /** E-mail. */
    private String email;

    /** Jednostka/department. */
    private String department;

    /** Pozycja/rola osoby w redakcji (w źródle: enum {@code personPosition}). */
    private PersonPosition personPosition;
}
