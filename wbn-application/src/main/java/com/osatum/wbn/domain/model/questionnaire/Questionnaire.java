package com.osatum.wbn.domain.model.questionnaire;


import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.domain.model.journal.Journal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "questionnaire")
@Setter
@Getter
@NoArgsConstructor
/* Klasa przechowujaca informacje o ankiecie */
public class Questionnaire extends GeneratedIdEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private Journal journal;

    /* rok ankiety */
    @Column(name = "questionnaire_year")
    private Integer year;
    /* data utworzenia */
    private LocalDateTime creationDate;
    /* data wyslania */
    private LocalDateTime sendDate;

    /* procentowe uzupelnienie ankiety */
    private Integer completeness;

    /* ocena ankiety */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "rating_id")
    private RatingIcv rating;

    /* dane uzytkownika powiazanego z ankieta */
    @Embedded
    private UserData userData;
    /* dane ankiety */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "survey_data_id")
    private SurveyData surveyData;
}
