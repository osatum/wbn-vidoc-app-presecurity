package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory;
import com.osatum.wbn.domain.model.journal.FeeCategory;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.*;
import java.util.List;

public final class DecisionFixture {
    private DecisionFixture() { }
    public static Survey survey(int year, int days) {
        var survey = new Survey(); survey.setEvaluationYear(year);
        survey.getSupplementaryData().setScientificCategories(List.of(
                new Category(1082L, "A", "Specjalność", "Speciality", null, true),
                new Category(1008L, "B", "Grupa", "Group", null, false)));
        var times = survey.getStandards().getAvgManuscriptTimes();
        times.setInitialAssessment(days); times.setReview(days); times.setPublication(days);
        var row = new ArticlePublished(); row.setYear(year); row.setReceivedManuscriptCount(100); row.setRejectedManuscriptCount(25);
        survey.getStability().setArticlePublished(List.of(row));
        survey.getCooperation().setAuthorMetric(metric(.5));
        survey.getCooperation().setReviewerMetric(metric(100));
        survey.getCooperation().setDirectorsMetric(metric(0));
        for (var category : FeeCategory.values()) category.from(survey.getStandards()).setFeeType(FeeType.NO_FEE);
        return survey;
    }
    public static List<DictionaryScientificCategory> categories() {
        return List.of(node(1082, 2, 1008L), node(1008, 1, 1001L), node(2000, 1, null));
    }
    private static DictionaryScientificCategory node(long id, int level, Long parent) {
        var node = new DictionaryScientificCategory(); node.setId(id); node.setLevel(level); node.setParentId(parent);
        node.setNameEn("Group " + id); return node;
    }
    private static ScientistMetric metric(double percentage) {
        var metric = new ScientistMetric(); metric.setForeignAffiliationPercentage(percentage); metric.setExternalAffiliationPercentage(99.0); return metric;
    }
}
