package com.osatum.wbn.infrastructure.elasticsearch.document.journal;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.List;
import com.osatum.wbn.domain.model.journal.JournalFeeTerms;

@Getter
@Setter
@NoArgsConstructor
@Document(indexName = "journal")
@Setting(settingPath = "elastic/journals-settings.json")
@Mapping(mappingPath = "elastic/journals-mapping.json")
public class JournalDocument {
    @Id
    @Field(type = FieldType.Keyword)
    private String documentId;
    @Field(name = "id", type = FieldType.Long)
    private Long id;
    @Field(type = FieldType.Integer)
    private Integer surveyYear;
    @Field(type = FieldType.Nested)
    private List<JournalFeeTerms> fees;
    private String titleSortContentIcu;
    @Field(type = FieldType.Integer)
    private Integer titleSortScriptRank;
    @Field(type = FieldType.Keyword)
    private String doiType;
    @Field(type = FieldType.Keyword)
    private List<String> editorialTools;
    @Field(type = FieldType.Boolean)
    private Boolean socialMediaUsed;
    @Field(type = FieldType.Keyword)
    private List<String> repositoryIds;
    @Field(type = FieldType.Keyword)
    private String articlePublishedLicense;
    @Field(type = FieldType.Keyword)
    private String paidArticle;
    @Field(type = FieldType.Boolean)
    private Boolean aop;
    @Field(type = FieldType.Keyword)
    private String frequencyCode;
    @Field(type = FieldType.Keyword)
    private List<String> reviewModels;
    @Field(type = FieldType.Integer)
    private Integer firstIssueYear;
    @Field(type = FieldType.Integer)
    private Integer reviewDays;
    @Field(type = FieldType.Integer)
    private Integer initialAssessmentDays;
    @Field(type = FieldType.Integer)
    private Integer publicationDays;
    @Field(type = FieldType.Double)
    private Double rejectionRate;
    @Field(type = FieldType.Double)
    private Double authorForeignAffiliationPercentage;
    @Field(type = FieldType.Double)
    private Double reviewerForeignAffiliationPercentage;
    private String cover;
    @Field(type = FieldType.Keyword, normalizer = "code_normalizer")
    private String countryCode;
    @MultiField(
            mainField = @Field(type = FieldType.Keyword, normalizer = "code_normalizer"),
            otherFields = {
                    @InnerField(suffix = "normalized", type = FieldType.Text)
            }
    )
    private String doi;
    @Field(type = FieldType.Keyword)
    private String warningCode;
    @Field(type = FieldType.Keyword)
    private String state;
    @Field(type = FieldType.Nested)
    private List<Title> titles;
    @Field(type = FieldType.Nested)
    private List<Issn> issns;
    @Field(type = FieldType.Nested)
    private List<Institution> institutions;
    @Field(type = FieldType.Nested)
    private List<Language> languages;
    @Field(type = FieldType.Nested)
    private List<Rating> ratings;
    @Field(type = FieldType.Nested)
    private List<ScientificCategory> scientificCategories;
    @Field(type = FieldType.Integer)
    private Integer numberOfPublications;
    @Field(type = FieldType.Integer)
    private Integer percentFullText;
    @Field(type = FieldType.Integer)
    private Integer percentAbstracts;
    @Field(type = FieldType.Integer)
    private Integer percentKeywords;
    @Field(type = FieldType.Integer)
    private Integer percentReferences;
}
