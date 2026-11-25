package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.application.journal.view.JournalView;
import com.osatum.wbn.domain.model.journal.FileData;
import com.osatum.wbn.domain.model.journal.Journal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Context;
import org.springframework.beans.factory.annotation.Value;

@Mapper(componentModel = "spring", uses = {
        CardinalityViewMapper.class,
        TitleViewSelectorMapper.class,
        IssnViewSelectorMapper.class,
        CountryViewMapper.class,
        LanguageViewMapper.class,
        InstitutionViewSelectorMapper.class,
        PersonViewSelectorMapper.class,
        DescriptionViewMapper.class,
        ScientificCategoryViewSelectorMapper.class,
        TrCategoryViewSelectorMapper.class,
        AsjcCategorySelectorMapper.class,
        IndexDbViewMapper.class,
        RatingViewSelectorMapper.class
})
public abstract class JournalViewMapper {

    @Value("${app.cover-path}")
    private String coverPath;

    public JournalView toView(Journal entity) {
        return toView(entity, null);
    }

    @Mapping(target = "surveyYear", ignore = true)
    @Mapping(target = "questionnaireAvailable", ignore = true)
    @Mapping(target = "availableSurveyYears", ignore = true)
    @Mapping(target = "kind", source = "kind")
    @Mapping(target = "cardinality", source = "cardinality")
    @Mapping(target = "titleOriginal", source = "titles", qualifiedByName = "original")
    @Mapping(target = "titleEnglish", source = "titles", qualifiedByName = "english")
    @Mapping(target = "titleShort", source = "titles", qualifiedByName = "short")
    @Mapping(target = "pissn", source = "issns", qualifiedByName = "pissn")
    @Mapping(target = "eissn", source = "issns", qualifiedByName = "eissn")
    @Mapping(target = "country", source = "country")
    @Mapping(target = "affiliationCountry", source = "affiliationCountry")
    @Mapping(target = "affiliationName", source = "affiliationName")
    @Mapping(target = "languages", source = "languages")
    @Mapping(target = "publishers", source = "institutions", qualifiedByName = "publishers")
    @Mapping(target = "editors", source = "institutions", qualifiedByName = "editors")
    @Mapping(target = "publisher", source = "institutions", qualifiedByName = "publisher")

    @Mapping(target = "descriptions", source = "descriptions")
    @Mapping(target = "scientificCategory", source = "scientificCategories", qualifiedByName = "basic")
    @Mapping(target = "scientificCategories", source = "scientificCategories", qualifiedByName = "other")
    @Mapping(target = "trCategory", source = "trCategories", qualifiedByName = "basic")
    @Mapping(target = "trCategories", source = "trCategories", qualifiedByName = "other")
    @Mapping(target = "ajscCategory", source = "ajscCategories", qualifiedByName = "basic")
    @Mapping(target = "ajscCategories", source = "ajscCategories", qualifiedByName = "other")
    @Mapping(target = "indexedDbs", source = "indexedDbs")
    @Mapping(target = "editorInChiefs", source = "persons", qualifiedByName = "editorInChief")
    @Mapping(target = "deputyEditorInChiefs", source = "persons", qualifiedByName = "deputyEditorInChief")
    @Mapping(target = "assistanceEditors", source = "persons", qualifiedByName = "assistanceEditor")
    @Mapping(target = "leadEditors", source = "persons", qualifiedByName = "leadEditor")
    @Mapping(target = "offices", source = "persons", qualifiedByName = "office")
    @Mapping(target = "editorialSecretaries", source = "persons", qualifiedByName = "editorialSecretaries")

    @Mapping(target = "icvRatings", source = "ratings", qualifiedByName = "icv")
    @Mapping(target = "mniswRatings", source = "ratings", qualifiedByName = "mnisw")

    @Mapping(target = "currentIcvYear", source = "ratings", qualifiedByName = "currentIcvYear")
    @Mapping(target = "currentIcvPoints", source = "ratings", qualifiedByName = "currentIcvPoints")
    @Mapping(target = "currentStatus", source = "ratings", qualifiedByName = "currentStatus")
    @Mapping(target = "currentReasonNotIndexing", source = "ratings", qualifiedByName = "currentReasonNotIndexing")
    @Mapping(target = "currentReasonNotIndexingFlag", source = "ratings", qualifiedByName = "currentReasonNotIndexingFlag")

    @Mapping(target = "cover", expression = "java(entityCoverPath(entity))")
    public abstract JournalView toView(Journal entity, @Context Integer surveyYear);

    public String entityCoverPath(Journal journal) {
        FileData cover = journal.getCover();
        if ( cover == null ) {
            return null;
        }
        return coverPath + "/" + cover.getPath();
    }


}
