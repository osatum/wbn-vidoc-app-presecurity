package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.application.journal.view.JournalDecisionIdentityView;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.JournalInstitution;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Mapper(componentModel = "spring", uses = {TitleViewSelectorMapper.class, IssnViewSelectorMapper.class,
        CountryViewMapper.class, LanguageViewMapper.class, DescriptionViewMapper.class, CardinalityViewMapper.class,
        InstitutionViewSelectorMapper.class, PersonViewSelectorMapper.class,
        ScientificCategoryViewSelectorMapper.class, TrCategoryViewSelectorMapper.class, AsjcCategorySelectorMapper.class})
public abstract class JournalDecisionIdentityMapper {
    @Value("${app.cover-path}")
    private String coverPath;

    @Mapping(target = "titleOriginal", source = "titles", qualifiedByName = "original")
    @Mapping(target = "titleEnglish", source = "titles", qualifiedByName = "english")
    @Mapping(target = "titleShort", source = "titles", qualifiedByName = "short")
    @Mapping(target = "pissn", source = "issns", qualifiedByName = "pissn")
    @Mapping(target = "eissn", source = "issns", qualifiedByName = "eissn")
    @Mapping(target = "publishers", source = "institutions", qualifiedByName = "publisherNames")
    @Mapping(target = "publisherDetails", source = "institutions", qualifiedByName = "publishers")
    @Mapping(target = "editors", source = "institutions", qualifiedByName = "editors")
    @Mapping(target = "editorInChiefs", source = "persons", qualifiedByName = "editorInChief")
    @Mapping(target = "deputyEditorInChiefs", source = "persons", qualifiedByName = "deputyEditorInChief")
    @Mapping(target = "editorialSecretaries", source = "persons", qualifiedByName = "editorialSecretaries")
    @Mapping(target = "scientificCategory", source = "scientificCategories", qualifiedByName = "basic")
    @Mapping(target = "scientificCategories", source = "scientificCategories", qualifiedByName = "other")
    @Mapping(target = "trCategory", source = "trCategories", qualifiedByName = "basic")
    @Mapping(target = "trCategories", source = "trCategories", qualifiedByName = "other")
    @Mapping(target = "ajscCategory", source = "ajscCategories", qualifiedByName = "basic")
    @Mapping(target = "ajscCategories", source = "ajscCategories", qualifiedByName = "other")
    @Mapping(target = "cover", expression = "java(cover(journal))")
    public abstract JournalDecisionIdentityView toView(Journal journal);

    protected String cover(Journal journal) {
        return journal.getCover() == null ? null : coverPath + "/" + journal.getCover().getPath();
    }

    @Named("publisherNames")
    protected List<String> publisherNames(Set<JournalInstitution> institutions) {
        return institutions == null ? List.of() : institutions.stream().filter(Objects::nonNull)
                .filter(value -> JournalInstitution.ROLE_PUBLISHER.equals(value.getRoleCode()))
                .map(JournalInstitution::getName).filter(Objects::nonNull)
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
    }
}
