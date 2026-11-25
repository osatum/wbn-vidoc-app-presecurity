package com.osatum.wbn.domain.model.survey.model;

import lombok.Data;

@Data
public class JournalIndicator implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private Double previous;
    private Double current;
}
