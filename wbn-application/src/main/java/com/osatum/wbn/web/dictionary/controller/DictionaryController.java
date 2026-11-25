package com.osatum.wbn.web.dictionary.controller;

import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.application.dictionary.service.DictionaryMniswDisciplineService;
import com.osatum.wbn.application.dictionary.view.MniswDisciplineDictionaryView;
import com.osatum.wbn.domain.model.dictionary.DictionaryMniswDiscipline;
import com.osatum.wbn.application.dictionary.service.DictionaryLanguageService;
import com.osatum.wbn.application.dictionary.service.DictionaryRepositoryService;
import com.osatum.wbn.application.dictionary.view.RepositoryDictionaryView;
import com.osatum.wbn.application.dictionary.service.DictionaryScientificCategoryService;
import com.osatum.wbn.application.dictionary.view.ScientificCategoryDictionaryView;
import com.osatum.wbn.application.journal.assembler.CountryViewMapper;
import com.osatum.wbn.application.journal.assembler.LanguageViewMapper;
import com.osatum.wbn.application.journal.view.CountryView;
import com.osatum.wbn.application.journal.view.LanguageView;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Udostępnia nazwy i identyfikatory wartości używanych w filtrach czasopism.
 * Liczniki wystąpień pochodzą osobno z metadanych wyszukiwarki; kategorie naukowe opisują hierarchię JML.
 */
@RestController
@RequestMapping("/api/dictionaries")
@RequiredArgsConstructor
@Tag(name = "Dictionary", description = "Słowniki referencyjne używane w formularzach i filtrach aplikacji")
public class DictionaryController {

    private final DictionaryScientificCategoryService scientificCategoryService;
    private final DictionaryCountryService countryService;
    private final DictionaryLanguageService languageService;
    private final CountryViewMapper countryViewMapper;
    private final LanguageViewMapper languageViewMapper;
    private final DictionaryRepositoryService repositoryService;
    private final DictionaryMniswDisciplineService mniswDisciplineService;

    @GetMapping(value = "/mnisw-disciplines", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Słownik dyscyplin MNiSW", operationId = "findMniswDisciplines")
    public List<MniswDisciplineDictionaryView> findMniswDisciplines() {
        return mniswDisciplineService.getAll().stream()
                .sorted(Comparator.comparing(DictionaryMniswDiscipline::getNamePl,
                        Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(DictionaryMniswDiscipline::getCode))
                .map(discipline -> new MniswDisciplineDictionaryView(discipline.getCode(), discipline.getNamePl(),
                        discipline.getNameEn(), discipline.getNameRu()))
                .toList();
    }

    /**
     * Zachowuje identyfikatory baz wykorzystywane w indeksie, aby etykiety nie zastępowały kluczy filtrowania.
     */
    @GetMapping(value = "/repositories", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lista baz indeksacyjnych", operationId = "findRepositories")
    public List<RepositoryDictionaryView> findRepositories() {
        return repositoryService.getAll().stream()
                .sorted(Comparator.comparing(com.osatum.wbn.domain.model.dictionary.DictionaryRepository::getName,
                        Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(com.osatum.wbn.domain.model.dictionary.DictionaryRepository::getId))
                .map(repository -> new RepositoryDictionaryView(repository.getId(), repository.getName()))
                .toList();
    }

    @GetMapping(value = "/countries", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Lista krajów",
            description = "Zwraca posortowany słownik krajów wykorzystywany przy prezentacji i filtrowaniu danych czasopism."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono listę krajów",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = CountryView.class))))
    public List<CountryView> findCountries() {
        return countryService.getAll().stream()
                .sorted(Comparator
                        .comparing(DictionaryCountry::getNamePl, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(DictionaryCountry::getCode, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(countryViewMapper::toView)
                .toList();
    }

    @GetMapping(value = "/languages", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Lista języków",
            description = "Zwraca posortowany słownik języków wykorzystywany przy prezentacji i filtrowaniu danych czasopism."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono listę języków",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = LanguageView.class))))
    public List<LanguageView> findLanguages() {
        return languageService.getAll().stream()
                .sorted(Comparator
                        .comparing(DictionaryLanguage::getNamePl, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(DictionaryLanguage::getCode, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(languageViewMapper::toView)
                .toList();
    }

    @GetMapping(value = "/scientific-categories", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Lista kategorii naukowych",
            description = "Zwraca posortowany słownik kategorii naukowych wraz ze strukturą hierarchii używaną w filtrach i formularzach."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono listę kategorii naukowych",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = ScientificCategoryDictionaryView.class))))
    public List<ScientificCategoryDictionaryView> findScientificCategories() {
        return scientificCategoryService.getAll().stream()
                .sorted(Comparator
                        .comparing(DictionaryScientificCategory::getLevel, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(DictionaryScientificCategory::getNamePl, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(DictionaryScientificCategory::getId))
                .map(this::toView)
                .toList();
    }

    private ScientificCategoryDictionaryView toView(DictionaryScientificCategory category) {
        return new ScientificCategoryDictionaryView(
                category.getId(),
                category.getParentId(),
                category.getParentPath(),
                category.getLevel(),
                category.getNamePl(),
                category.getNameEn(),
                category.getNameRu()
        );
    }
}
