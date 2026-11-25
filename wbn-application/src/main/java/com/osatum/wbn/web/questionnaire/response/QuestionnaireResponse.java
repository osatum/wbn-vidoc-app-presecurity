package com.osatum.wbn.web.questionnaire.response;

import com.osatum.wbn.application.questionnaire.view.QuestionnaireView;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireResponse {
    private QuestionnaireView questionnaire;
}
