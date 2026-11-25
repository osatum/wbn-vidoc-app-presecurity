package com.osatum.wbn.domain.model.survey.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Uniwersalna reprezentacja pozycji słownikowej/kategorii wybranej w ankiecie.
 * <p>
 * W {@code SurveyData} występują różne typy kategorii (np. scientific, TR, ASJC).
 * DTO upraszcza je do wspólnych pól – wystarczających do migracji poprzez JSON.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Id kategorii (jeśli istnieje w modelu źródłowym). */
    private Long id;

    /** Kod kategorii (np. kod ASJC/TR) – jeżeli dostępny. */
    private String code;

    /** Nazwa/etykieta kategorii widoczna dla użytkownika w jezyku polskim  */
    private String namePl;
    /** Nazwa/etykieta kategorii widoczna dla użytkownika w jezyku angielskim  */
    private String nameEn;
    /** Nazwa/etykieta kategorii widoczna dla użytkownika w jezyku polskim  */
    private String nameRu;

    /** Czy jest to kategoria główna. */
    private Boolean main;
}
