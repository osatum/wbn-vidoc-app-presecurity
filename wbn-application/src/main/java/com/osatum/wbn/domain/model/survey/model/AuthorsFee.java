package com.osatum.wbn.domain.model.survey.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Model opłaty pobieranej od autorów.
 * <p>
 * W formularzu sekcji 5 typ opłaty ({@code feeType}) steruje sposobem prezentacji pól:
 * <ul>
 *   <li>{@code ONE_FEE} – pojedyncza kwota,</li>
 *   <li>{@code COUNTRY_SPECIFIC_FEE} – kwoty zależne od kraju (np. Polska/Zagranica),</li>
 *   <li>{@code VARIOUS_FEE} – wiele progów/wariantów opłat.</li>
 * </ul>
 * DTO jest niezależny od enumów modelu źródłowego.
 */
@Data
public class AuthorsFee implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Typ opłaty (np. ONE_FEE / COUNTRY_SPECIFIC_FEE / VARIOUS_FEE). */
    private FeeType feeType;

    /** Szczegóły opłat. */
    private List<FeeDetail> feeDetails = new ArrayList<>();

    private CountrySpecificFeeSelection countrySpecificFeeSelection;
    private FeeSelectionStatus feeSelectionStatus;
}
