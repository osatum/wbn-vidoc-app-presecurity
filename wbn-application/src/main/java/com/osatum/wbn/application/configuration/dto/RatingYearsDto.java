package com.osatum.wbn.application.configuration.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RatingYearsDto {
    private Integer currentIcvYear;
    private Integer previousIcvYear;
}
