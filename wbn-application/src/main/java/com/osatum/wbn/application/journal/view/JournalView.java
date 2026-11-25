package com.osatum.wbn.application.journal.view;

import com.osatum.wbn.domain.model.journal.JournalFrequency;
import com.osatum.wbn.domain.model.journal.JournalKind;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class JournalView extends AbstractJournalView {

    private boolean hasIssues = true;
    private boolean questionnaireAllowed = true;
    private Integer surveyYear;
    private boolean questionnaireAvailable;
    private List<Integer> availableSurveyYears;

    private JournalKind kind;
    private String website;
    private Boolean printedVersion;
    private Boolean electronicVersion;
    private Boolean freeFullText;
    private Integer firstIssueYear;
    private JournalFrequency frequency;
    private String warningCode;
    private Integer circulation;

    private String publisher;
    private String cover;

    private CardinalityView cardinality;
    private List<InstitutionView> publishers;
    private List<InstitutionView> editors;
    private CountryView affiliationCountry;
    private String affiliationName;
    private PaidArticleView paidArticle;
    private List<PersonView> editorInChiefs;
    private List<PersonView> deputyEditorInChiefs;
    private List<PersonView> assistanceEditors;
    private List<PersonView> leadEditors;
    private List<PersonView> offices;
    private List<PersonView> editorialSecretaries;

    private List<DescriptionView> descriptions;

    private CategoryView scientificCategory;
    private List<CategoryView> scientificCategories;

    private CategoryView trCategory;
    private List<CategoryView> trCategories;

    private CategoryView ajscCategory;
    private List<CategoryView> ajscCategories;
    private List<String> indexedDbs;

    private List<RatingView> icvRatings;
    private List<RatingView> mniswRatings;

}
