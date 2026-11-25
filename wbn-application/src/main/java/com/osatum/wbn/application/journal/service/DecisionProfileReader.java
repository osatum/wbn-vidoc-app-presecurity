package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.dictionary.service.DictionaryScientificCategoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryRepositoryService;
import com.osatum.wbn.application.journal.view.DecisionValue;
import com.osatum.wbn.application.journal.view.JournalDecisionAnnualView.*;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;

/** Rozwiązuje roczne przypisania ankiety do Level 1; nie zastępuje ich bieżącym profilem katalogowym. */
@Service
@RequiredArgsConstructor
public class DecisionProfileReader {
    private final DictionaryScientificCategoryService categories;
    private final DictionaryRepositoryService repositories;

    public Profile profile(Survey survey) {
        return profile(survey, categories::getById);
    }

    Profile profile(Survey survey, Function<Long, DictionaryScientificCategory> lookup) {
        var data = survey.getSupplementaryData();
        var source = data == null ? null : data.getScientificCategories();
        var main = source == null ? List.<com.osatum.wbn.domain.model.survey.model.Category>of()
                : source.stream().filter(Objects::nonNull).filter(value -> Boolean.TRUE.equals(value.getMain())).toList();
        // Wiele głównych wskazań jest niejednoznacznością, nawet jeśli prowadzą do tej samej grupy nadrzędnej.
        var group = main.size() == 1 ? resolve(main.getFirst().getId(), lookup)
                : main.isEmpty() ? DecisionValue.<Group>missing() : DecisionValue.<Group>invalid();
        var groups = new TreeMap<Long, Group>();
        boolean invalid = false;
        if (source != null) for (var category : source) {
            var resolved = resolve(category == null ? null : category.getId(), lookup);
            if (resolved.value() == null) invalid = true; else groups.put(resolved.value().id(), resolved.value());
        }
        DecisionValue<List<Group>> memberships = source == null || source.isEmpty() ? DecisionValue.missing()
                : new DecisionValue<>(invalid ? DecisionValue.State.INVALID : DecisionValue.State.AVAILABLE, List.copyOf(groups.values()));
        return new Profile(group, memberships, categoryList(source),
                categoryList(data == null ? null : data.getTrCategories()), categoryList(data == null ? null : data.getAsjcCategories()));
    }

    public DecisionValue<Group> resolve(Long id) {
        return resolve(id, categories::getById);
    }

    private DecisionValue<Group> resolve(Long id, Function<Long, DictionaryScientificCategory> lookup) {
        if (id == null) return DecisionValue.missing();
        var visited = new HashSet<Long>();
        while (id != null && visited.add(id)) {
            var item = lookup.apply(id);
            if (item == null || item.getLevel() == null || item.getLevel() < 1) return DecisionValue.invalid();
            if (item.getLevel() == 1) return DecisionValue.available(new Group(item.getId(), item.getNamePl(), item.getNameEn(), item.getNameRu()));
            id = item.getParentId();
        }
        return DecisionValue.invalid();
    }

    private DecisionValue<List<Category>> categoryList(List<com.osatum.wbn.domain.model.survey.model.Category> values) {
        if (values == null || values.isEmpty()) return DecisionValue.missing();
        var result = values.stream().filter(Objects::nonNull)
                .map(value -> new Category(value.getId(), value.getCode(), value.getMain(), value.getNamePl(), value.getNameEn(), value.getNameRu()))
                .distinct().toList();
        return new DecisionValue<>(result.size() < values.size() && values.stream().anyMatch(Objects::isNull)
                ? DecisionValue.State.INVALID : DecisionValue.State.AVAILABLE, result);
    }

    public DecisionValue<List<IndexedDatabase>> indexing(Survey survey) {
        var data = survey.getDigitalization() == null ? null : survey.getDigitalization().getExternalRepositories();
        if (data == null) return DecisionValue.missing();
        var result = new LinkedHashSet<IndexedDatabase>();
        boolean invalid = false;
        if (data.getGeneral() != null) for (var value : data.getGeneral()) {
            if (value == null) { invalid = true; continue; }
            String name = value.getName();
            if (value.getId() != null) {
                var dictionary = repositories.getById(value.getId());
                if (dictionary != null) name = dictionary.getName();
            }
            if (name == null || name.isBlank()) invalid = true;
            else result.add(new IndexedDatabase(value.getId(), name));
        }
        if (result.isEmpty() && !invalid) return DecisionValue.missing();
        return new DecisionValue<>(invalid ? DecisionValue.State.INVALID : DecisionValue.State.AVAILABLE, List.copyOf(result));
    }
}
