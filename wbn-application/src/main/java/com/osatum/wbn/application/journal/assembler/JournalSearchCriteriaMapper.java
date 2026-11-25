package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.application.journal.query.JournalListQuery;
import com.osatum.wbn.application.journal.query.JournalSearchCriteria;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Oddziela zgodność historycznego kontraktu REST od kanonicznych kryteriów aplikacji.
 * Jawna pusta lista usuwa ograniczenie i ma pierwszeństwo przed starszym pojedynczym kodem.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface JournalSearchCriteriaMapper {
    @Mapping(target = "countryCodes", expression = "java(selectedCodes(query.getCountryCodes(), query.getCountryCode()))")
    @Mapping(target = "languageCodes", expression = "java(selectedCodes(query.getLanguageCodes(), query.getLanguageCode()))")
    @Mapping(target = "mniswDisciplineCodes", expression = "java(query.isMniswOnly() ? selectedCodes(query.getMniswDisciplineCodes(), null) : java.util.List.of())")
    JournalSearchCriteria toCriteria(JournalListQuery query);

    default List<String> selectedCodes(List<String> codes, String legacyCode) {
        List<String> values = codes;
        if (values == null) {
            values = legacyCode != null && !legacyCode.isBlank() ? List.of(legacyCode) : List.of();
        }
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT)).distinct().toList();
    }
}
