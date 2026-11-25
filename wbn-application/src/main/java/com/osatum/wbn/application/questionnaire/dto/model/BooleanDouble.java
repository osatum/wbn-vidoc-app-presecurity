package com.osatum.wbn.application.questionnaire.dto.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BooleanDouble  implements Serializable {
    private static final long serialVersionUID = 1L;
    private Boolean confirm = Boolean.FALSE;
    private Double value;
}
