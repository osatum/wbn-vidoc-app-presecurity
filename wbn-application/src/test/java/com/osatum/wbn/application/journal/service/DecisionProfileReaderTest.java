package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.dictionary.service.*;
import com.osatum.wbn.application.journal.view.DecisionValue.State;
import com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Category;
import com.osatum.wbn.domain.model.survey.model.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionProfileReaderTest {
    private final DictionaryScientificCategoryService categories = mock();
    private final DictionaryRepositoryService repositories = mock();
    private final DecisionProfileReader reader = new DecisionProfileReader(categories, repositories);

    @Test
    void resolvesMainAndDeduplicatesAllMembershipsByStableLevelOneId() {
        node(1082, 2, 1008L); node(1083, 3, 1008L); node(1008, 1, 1001L); node(2000, 1, null);
        var survey = new Survey();
        survey.getSupplementaryData().setScientificCategories(List.of(category(1082, true), category(1083, false), category(2000, false)));
        var profile = reader.profile(survey);
        assertThat(profile.mainGroup().value().id()).isEqualTo(1008L);
        assertThat(profile.mainGroup().value().nameEn()).isEqualTo("Group 1008");
        assertThat(profile.memberships().value()).extracting(value -> value.id()).containsExactly(1008L, 2000L);
        assertThat(reader.resolve(1008L).value().id()).isEqualTo(1008L);
        verify(categories, never()).getById(1001L);
        survey.getSupplementaryData().setScientificCategories(List.of(category(1082, true), category(1083, true)));
        assertThat(reader.profile(survey).mainGroup().status()).isEqualTo(State.INVALID);
        survey.getSupplementaryData().setScientificCategories(List.of(category(1008, true), category(9999, false)));
        var partial = reader.profile(survey);
        assertThat(partial.mainGroup().status()).isEqualTo(State.AVAILABLE);
        assertThat(partial.memberships().status()).isEqualTo(State.INVALID);
        assertThat(partial.memberships().value()).extracting(value -> value.id()).containsExactly(1008L);
    }

    @Test
    void rejectsBrokenHierarchyCyclesAndLevelZeroWithoutFallback() {
        assertThat(reader.resolve(1L).status()).isEqualTo(State.INVALID);
        node(1, 2, 2L); node(2, 2, 1L);
        assertThat(reader.resolve(1L).status()).isEqualTo(State.INVALID);
        node(2, 0, null); assertThat(reader.resolve(1L).status()).isEqualTo(State.INVALID);
        node(2, 2, null); assertThat(reader.resolve(1L).status()).isEqualTo(State.INVALID);
        var missing = node(2, 2, null); missing.setLevel(null);
        assertThat(reader.resolve(1L).status()).isEqualTo(State.INVALID);
        assertThat(reader.resolve(null).status()).isEqualTo(State.MISSING);
        var survey = new Survey(); survey.getSupplementaryData().setScientificCategories(List.of(category(1, false)));
        assertThat(reader.profile(survey).mainGroup().status()).isEqualTo(State.MISSING);
    }

    @Test
    void readsGeneralAnnualRepositoriesAndOmitsOthers() {
        var survey = new Survey();
        var dictionary = new com.osatum.wbn.domain.model.dictionary.DictionaryRepository(); dictionary.setName("Dictionary name");
        when(repositories.getById(7L)).thenReturn(dictionary);
        survey.getDigitalization().getExternalRepositories().setGeneral(List.of(new Repository(7L, "Old name"), new Repository(null, "General name")));
        survey.getDigitalization().getExternalRepositories().setOthers(List.of("Other database"));
        var result = reader.indexing(survey);
        assertThat(result.status()).isEqualTo(State.AVAILABLE);
        assertThat(result.value()).extracting(value -> value.name()).containsExactly("Dictionary name", "General name");
        assertThat(result.value().getFirst().id()).isEqualTo(7L);
        assertThat(result.value().get(1).id()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void doesNotFallBackToOtherRepositoriesWhenGeneralRepositoriesAreMissing(List<Repository> general) {
        var survey = new Survey();
        survey.getDigitalization().getExternalRepositories().setGeneral(general);
        survey.getDigitalization().getExternalRepositories().setOthers(List.of("Other database"));

        var result = reader.indexing(survey);

        assertThat(result.status()).isEqualTo(State.MISSING);
        assertThat(result.value()).isNull();
        verifyNoInteractions(repositories);
    }

    @Test
    void doesNotReplaceInvalidGeneralRepositoriesWithOthers() {
        var survey = new Survey();
        survey.getDigitalization().getExternalRepositories().setGeneral(List.of(new Repository(null, " ")));
        survey.getDigitalization().getExternalRepositories().setOthers(List.of("Other database"));

        var result = reader.indexing(survey);

        assertThat(result.status()).isEqualTo(State.INVALID);
        assertThat(result.value()).isEmpty();
    }
    private DictionaryScientificCategory node(long id, int level, Long parent) {
        var value = new DictionaryScientificCategory(); value.setId(id); value.setLevel(level); value.setParentId(parent); value.setNameEn("Group " + id);
        when(categories.getById(id)).thenReturn(value); return value;
    }
    private Category category(long id, boolean main) { return new Category(id, null, "Nazwa", "Name", null, main); }
}
