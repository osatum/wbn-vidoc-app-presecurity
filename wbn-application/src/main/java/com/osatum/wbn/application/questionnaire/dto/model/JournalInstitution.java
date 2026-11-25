package com.osatum.wbn.application.questionnaire.dto.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Powiązanie instytucji z rolą (wydawca, współwydawca, itp.).
 */
@Data
public class JournalInstitution implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Rola instytucji (w źródle: {@code institutionRole} jako {@code Character}). */
    private Character institutionRole;

    /** Dane instytucji. */
    private Institution institution;
}
