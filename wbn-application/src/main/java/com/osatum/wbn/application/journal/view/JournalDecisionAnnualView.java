package com.osatum.wbn.application.journal.view;

import com.osatum.wbn.domain.model.journal.JournalFrequency;
import com.osatum.wbn.domain.model.journal.PaidArticleType;
import com.osatum.wbn.domain.model.survey.model.ArticlePublishedLicense;
import com.osatum.wbn.domain.model.survey.model.SocialMedia;
import java.util.List;

public record JournalDecisionAnnualView(Integer surveyYear, Times times, Process process,
                                        International international, Standards standards, Profile profile,
                                        DecisionValue<List<IndexedDatabase>> indexing,
                                        List<DecisionFeeView> fees) {
    public record Times(DecisionValue<Integer> initialAssessment, DecisionValue<Integer> review,
                        DecisionValue<Integer> publication) { }
    public record Process(DecisionValue<Integer> firstIssueYear, DecisionValue<Integer> continuedYear,
                          DecisionValue<JournalFrequency> frequency, DecisionValue<Boolean> aheadOfPrint,
                          DecisionValue<Double> rejectionRate, DecisionValue<Boolean> publishingBreak) { }
    public record International(DecisionValue<Double> authors, DecisionValue<Double> reviewers,
                                DecisionValue<Double> scientificCouncil) { }
    public enum Declaration { INDICATED, NONE, NOT_INDICATED }
    public record ReviewModel(String code, Boolean confirmed, DecisionValue<Double> share) { }
    public record Review(List<ReviewModel> models, Boolean none, DecisionValue.State status) { }
    public record Social(Boolean used, List<SocialMedia> channels, String other) { }
    public record Standards(DecisionValue<Declaration> antiPlagiarism, DecisionValue<Declaration> publicCriteria,
                            DecisionValue<Declaration> editorialSystem, Review reviewing,
                            DecisionValue<PaidArticleType> access, DecisionValue<List<String>> accessConditions,
                            DecisionValue<ArticlePublishedLicense> licence, DecisionValue<Social> socialMedia) { }
    public record Group(Long id, String namePl, String nameEn, String nameRu) { }
    public record Category(Long id, String code, Boolean main, String namePl, String nameEn, String nameRu) { }
    public record Profile(DecisionValue<Group> mainGroup, DecisionValue<List<Group>> memberships,
                          DecisionValue<List<Category>> scientificCategories,
                          DecisionValue<List<Category>> wosCategories, DecisionValue<List<Category>> scopusCategories) { }
    public record IndexedDatabase(Long id, String name) { }
}
