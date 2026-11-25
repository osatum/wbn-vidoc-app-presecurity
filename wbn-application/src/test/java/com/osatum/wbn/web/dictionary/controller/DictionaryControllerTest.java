package com.osatum.wbn.web.dictionary.controller;

import com.osatum.wbn.application.dictionary.service.DictionaryRepositoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryMniswDisciplineService;
import com.osatum.wbn.domain.model.dictionary.DictionaryMniswDiscipline;
import com.osatum.wbn.domain.model.dictionary.DictionaryRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DictionaryControllerTest {
    @Test
    void returnsSortedRepositoryCodesAndNamesWithoutExposingEntities() {
        var service = mock(DictionaryRepositoryService.class);
        var first = new DictionaryRepository();
        first.setId(10L);
        first.setName("DOAJ");
        var second = new DictionaryRepository();
        second.setId(20L);
        second.setName("ABC");
        when(service.getAll()).thenReturn(List.of(first, second));
        var controller = new DictionaryController(null, null, null, null, null, service, null);
        assertThat(controller.findRepositories()).extracting(value -> value.id()).containsExactly(20L, 10L);
        assertThat(controller.findRepositories()).extracting(value -> value.name()).containsExactly("ABC", "DOAJ");
    }

    @Test
    void returnsMniswCodesAndTranslationsWithoutExposingEntities() {
        var service = mock(DictionaryMniswDisciplineService.class);
        var discipline = new DictionaryMniswDiscipline();
        discipline.setCode("2021");
        discipline.setNamePl("automatyka");
        discipline.setNameEn("automation");
        discipline.setNameRu("автоматика");
        when(service.getAll()).thenReturn(List.of(discipline));
        var controller = new DictionaryController(null, null, null, null, null, null, service);

        assertThat(controller.findMniswDisciplines()).singleElement().satisfies(view -> {
            assertThat(view.code()).isEqualTo("2021");
            assertThat(view.namePl()).isEqualTo("automatyka");
            assertThat(view.nameEn()).isEqualTo("automation");
            assertThat(view.nameRu()).isEqualTo("автоматика");
        });
    }
}
