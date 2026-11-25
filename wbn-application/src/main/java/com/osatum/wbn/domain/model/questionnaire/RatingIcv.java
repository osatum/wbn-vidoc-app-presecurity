package com.osatum.wbn.domain.model.questionnaire;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "questionnaire_rating_icv")
@Setter
@Getter
@NoArgsConstructor
/* Klasa przechowujaca informacje o ocenach icv */
public class RatingIcv extends GeneratedIdEntity {
    /* ocena icv */
    private Double icv;
    /* status oceny */
    @Enumerated(EnumType.STRING)
    private RatingIcvStatus ratingStatus;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private ReasonNotIndexing reasonNotIndexing;
}
