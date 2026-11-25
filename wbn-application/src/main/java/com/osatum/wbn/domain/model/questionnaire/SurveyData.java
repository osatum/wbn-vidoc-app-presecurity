package com.osatum.wbn.domain.model.questionnaire;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.domain.model.survey.Survey;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "questionnaire_survey")
@Setter
@Getter
@NoArgsConstructor
/* Klasa przechowujaca dane ankiety */
public class SurveyData extends GeneratedIdEntity {
    /* dane ankiety w formacie JSON */
    @Column(columnDefinition = "TEXT", length = 65535)
    @Convert(converter = SurveyJsonConverter.class)
    private Survey survey;
}
