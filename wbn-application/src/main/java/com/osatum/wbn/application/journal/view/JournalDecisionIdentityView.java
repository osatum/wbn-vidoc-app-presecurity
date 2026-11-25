package com.osatum.wbn.application.journal.view;

import com.osatum.wbn.domain.model.journal.JournalKind;

import java.util.List;

/** Catalog identification only; annual survey data is mapped separately. */
public record JournalDecisionIdentityView(Long id, String titleOriginal, String titleEnglish,
                                          String pissn, String eissn, String website, String cover,
                                          CountryView country, List<LanguageView> languages,
                                          List<String> publishers, List<DescriptionView> descriptions,
                                          CardinalityView cardinality,
                                          String titleShort, JournalKind kind,
                                          Boolean printedVersion, Boolean electronicVersion,
                                          PaidArticleView paidArticle,
                                          CategoryView scientificCategory, List<CategoryView> scientificCategories,
                                          CategoryView trCategory, List<CategoryView> trCategories,
                                          CategoryView ajscCategory, List<CategoryView> ajscCategories,
                                          List<PersonView> editorInChiefs, List<PersonView> deputyEditorInChiefs,
                                          List<PersonView> editorialSecretaries,
                                          List<InstitutionView> editors, List<InstitutionView> publisherDetails,
                                          String affiliationName) {
}
