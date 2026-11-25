package com.osatum.wbn.domain.service.journal;

import com.osatum.wbn.domain.model.survey.model.AuthorsFee;
import com.osatum.wbn.domain.model.survey.model.CountrySpecificFeeSelection;
import com.osatum.wbn.domain.model.survey.model.FeeAuthorVariant;
import com.osatum.wbn.domain.model.survey.model.FeeDetail;
import com.osatum.wbn.domain.model.survey.model.FeeSelectionSource;
import com.osatum.wbn.domain.model.survey.model.FeeSelectionStatus;
import com.osatum.wbn.domain.model.survey.model.FeeType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Sprawdza wyeksportowany wybór wariantu opłaty dla autora z Polski i wskazuje odpowiadający mu szczegół.
 * Brak rozstrzygnięcia, dane historyczne i sprzeczności pozostają odrębnymi statusami, bez zgadywania wariantu.
 */
@Service
public class AuthorFeeSelectionPolicy {

    /**
     * Weryfikuje spójność eksportu podczas importu: wyjątek ma pierwszeństwo, a reguła kraju wymaga
     * zgodnego effectiveVariant. Schemat 1.0 bez oznaczeń zachowuje status LEGACY.
     */
    public Resolution validate(AuthorsFee fee, String schemaVersion, String journalCountryCode) {
        if (fee == null || fee.getFeeType() != FeeType.COUNTRY_SPECIFIC_FEE) {
            return new Resolution(null, null, null);
        }
        CountrySpecificFeeSelection selection = fee.getCountrySpecificFeeSelection();
        if (selection == null) {
            return "1.0".equals(schemaVersion)
                    ? new Resolution(FeeSelectionStatus.LEGACY, null, null)
                    : incomplete("Missing countrySpecificFeeSelection");
        }
        if (selection.getAuthorCountryCode() == null || selection.getSource() == null) {
            return incomplete("Missing author country or selection source");
        }
        if (!"PL".equals(selection.getAuthorCountryCode())) {
            return invalid("Unsupported author country");
        }
        String countryCode = journalCountryCode == null ? "" : journalCountryCode.trim();
        if (selection.getSource() == FeeSelectionSource.UNRESOLVED) {
            if (selection.getOverrideVariant() != null || selection.getEffectiveVariant() != null || !countryCode.isEmpty()) {
                return invalid("UNRESOLVED conflicts with country or variants");
            }
            return new Resolution(FeeSelectionStatus.UNRESOLVED, null, null);
        }
        if (selection.getEffectiveVariant() == null) {
            return incomplete("Missing effectiveVariant");
        }
        if (selection.getSource() == FeeSelectionSource.JOURNAL_OVERRIDE) {
            if (selection.getOverrideVariant() == null || selection.getOverrideVariant() != selection.getEffectiveVariant()) {
                return invalid("Override and effective variant do not match");
            }
        } else {
            if (countryCode.isEmpty() || selection.getOverrideVariant() != null) {
                return invalid("JOURNAL_COUNTRY requires a country and no override");
            }
            FeeAuthorVariant expected = "PL".equalsIgnoreCase(countryCode)
                    ? FeeAuthorVariant.INTERNAL_AUTHORS : FeeAuthorVariant.EXTERNAL_AUTHORS;
            if (selection.getEffectiveVariant() != expected) {
                return invalid("Effective variant does not match exported journal country");
            }
        }
        return selectDetail(fee);
    }

    /**
     * Odczytuje wcześniej zweryfikowany effectiveVariant, bez ponownego wyboru według kraju czasopisma.
     * Późniejsza korekta kraju nie może samoczynnie zmienić wariantu użytego w filtrze i wyniku.
     */
    public Resolution resolve(AuthorsFee fee) {
        if (fee == null || fee.getFeeType() != FeeType.COUNTRY_SPECIFIC_FEE) {
            return new Resolution(null, null, null);
        }
        FeeSelectionStatus status = fee.getFeeSelectionStatus();
        if (status == null) {
            status = fee.getCountrySpecificFeeSelection() == null
                    ? FeeSelectionStatus.LEGACY : FeeSelectionStatus.INCOMPLETE;
        }
        if (status != FeeSelectionStatus.RESOLVED) {
            return new Resolution(status, null, null);
        }
        CountrySpecificFeeSelection selection = fee.getCountrySpecificFeeSelection();
        if (selection == null || !"PL".equals(selection.getAuthorCountryCode()) || selection.getEffectiveVariant() == null) {
            return incomplete("Missing validated selection");
        }
        if (selection.getSource() == FeeSelectionSource.JOURNAL_OVERRIDE) {
            if (selection.getOverrideVariant() != selection.getEffectiveVariant()) {
                return invalid("Override and effective variant do not match");
            }
        } else if (selection.getSource() != FeeSelectionSource.JOURNAL_COUNTRY || selection.getOverrideVariant() != null) {
            return invalid("Invalid resolved selection source");
        }
        return selectDetail(fee);
    }

    /**
     * P1-U01: wybiera wariant dla polskiego autora według kraju czasopisma w projekcji P1.
     * Metadane importu i indywidualne wyjątki nie zmieniają tej reguły; brak kraju nie oznacza opłaty zerowej.
     */
    public Resolution resolve(AuthorsFee fee, String journalCountryCode) {
        if (fee == null || fee.getFeeType() != FeeType.COUNTRY_SPECIFIC_FEE) {
            return new Resolution(null, null, null);
        }
        FeeAuthorVariant variant = variantForCountry(journalCountryCode);
        return variant == null
                ? new Resolution(FeeSelectionStatus.UNRESOLVED, null, "Missing or invalid journal country")
                : selectDetail(fee, variant);
    }

    FeeAuthorVariant variantForCountry(String journalCountryCode) {
        String code = journalCountryCode == null ? "" : journalCountryCode.trim();
        if (!code.matches("[A-Za-z]{2}")) return null;
        return "PL".equalsIgnoreCase(code) ? FeeAuthorVariant.INTERNAL_AUTHORS : FeeAuthorVariant.EXTERNAL_AUTHORS;
    }

    /**
     * Wymaga dokładnie jednego szczegółu z kodem wariantu w description; nie interpretuje opisu jako tekstu dowolnego.
     */
    private Resolution selectDetail(AuthorsFee fee) {
        return selectDetail(fee, fee.getCountrySpecificFeeSelection().getEffectiveVariant());
    }

    private Resolution selectDetail(AuthorsFee fee, FeeAuthorVariant variant) {
        List<FeeDetail> matches = fee.getFeeDetails() == null ? List.of() : fee.getFeeDetails().stream()
                .filter(Objects::nonNull)
                .filter(detail -> variant.name().equals(detail.getDescription()))
                .toList();
        if (matches.size() != 1) {
            return invalid("Expected exactly one fee detail matching variant " + variant + ", found " + matches.size());
        }
        return new Resolution(FeeSelectionStatus.RESOLVED, matches.getFirst(), null);
    }

    private Resolution incomplete(String issue) {
        return new Resolution(FeeSelectionStatus.INCOMPLETE, null, issue);
    }

    private Resolution invalid(String issue) {
        return new Resolution(FeeSelectionStatus.INVALID, null, issue);
    }

    public record Resolution(FeeSelectionStatus status, FeeDetail selectedFeeDetail, String issue) {
    }
}
