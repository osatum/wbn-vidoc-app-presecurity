package com.osatum.wbn.application.questionnaire.view;

import com.osatum.wbn.application.questionnaire.dto.SurveyDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireView {
    private Integer year;
    private Integer completeness;
    private RatingIcvView rating;
    private SurveyDto survey;
    private boolean detailedSectionEnabled;
}
