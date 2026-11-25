package com.osatum.wbn.domain.model.journal;

import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.domain.model.questionnaire.ReasonNotIndexing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "journal_rating")
@Setter
@Getter
@NoArgsConstructor
/* tabela przechowujaca informacje o ocenach czasopisma */
public class Rating extends GeneratedIdEntity {
    /* ocena icv */
    public static final String ICV_RATING_CODE = "ICV";
    /* ocena MNW */
    public static final String MNW_RATING_CODE = "MNW";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private Journal journal;

    /* wartosc tekstowa oceny */
    @Column(name = "value_str")
    private String valueStr;

    /* wartosc liczbowo oceny */
    @Column(name = "value_num")
    private Double valueNum;

    @Column(length = 20, name = "rating_code")
    /* kod oceny */
    private String ratingCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, name = "rating_status")
    /* kod oceny */
    private RatingStatus ratingStatus;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private ReasonNotIndexing reasonNotIndexing;

    /* rok oceny */
    @Column(name = "rating_year")
    private Integer year;

    /* issn */
    @Column(name = "rating_issn")
    private String issn;

    /* dyscypliny mnisw */
    @OneToMany(mappedBy = "rating", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MniswDiscipline> mniswDisciplines;
}
