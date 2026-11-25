package com.osatum.wbn.application.journal.view;

import com.osatum.wbn.domain.entity.RatingStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RatingView {
    private Integer year;
    private Double currentMethodPoint;
    private Double previousMethodPoint;
    private String ratingKey;
    private RatingStatus ratingStatus;
}
