package com.osatum.wbn.domain.model.survey.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
/* Kontener porzechowywania wartosci boolean i double */
public class BooleanDouble  implements Serializable {
    private static final long serialVersionUID = 1L;
    private Boolean confirm = Boolean.FALSE;
    private Double value;
}
