package com.osatum.wbn.domain.model.questionnaire;

import java.util.Arrays;
import java.util.Optional;

/* statusy oceny ankiety */
public enum RatingIcvStatus {
    /* blad*/
    ERROR,
    /* ocena oczekuje na zatwierdzenie */
    FOR_APPROVAL,
    /* ocena zatwierdzona */
    PUBLISHED,
    /* nie indeksowana */
    NOT_INDEXED,
    /* predator */
    PREDATOR;

    public static Optional<RatingIcvStatus> fromValue(String value) {
        return Arrays.stream(RatingIcvStatus.values()).filter(x-> x.name().equals(value)).findFirst();
    }

}
