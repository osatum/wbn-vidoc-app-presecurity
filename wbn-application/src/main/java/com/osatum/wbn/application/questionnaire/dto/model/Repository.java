package com.osatum.wbn.application.questionnaire.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Repository implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;

}
