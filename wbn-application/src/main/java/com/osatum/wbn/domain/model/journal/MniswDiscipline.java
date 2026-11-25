package com.osatum.wbn.domain.model.journal;


import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_mnisw_discipline")
@Setter
@Getter
@NoArgsConstructor
/* Dyscypliny MNiSW czasopisma */
public class MniswDiscipline  extends GeneratedIdEntity {

    /* wzkaznik na ocene */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rating_id")
    private Rating rating;

    /* kod dyscypliny */
    @Column(length = 20)
    private String disciplineCode;
}
