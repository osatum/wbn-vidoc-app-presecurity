package com.osatum.wbn.application.questionnaire.view;

import com.osatum.wbn.domain.model.questionnaire.RatingIcvStatus;
import com.osatum.wbn.domain.model.questionnaire.ReasonNotIndexing;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RatingIcvView {
    private Double icv;
    private RatingIcvStatus ratingStatus;
    private ReasonNotIndexing reasonNotIndexing;
}
