package com.osatum.wbn.application.analytics.assembler;

import com.osatum.wbn.application.analytics.view.AnalyticsContainerView;
import com.osatum.wbn.application.analytics.view.AnalyticsCountryView;
import com.osatum.wbn.application.analytics.view.AnalyticsItemView;
import com.osatum.wbn.application.analytics.view.AnalyticsJournalView;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.journal.assembler.CountryViewMapper;
import com.osatum.wbn.domain.model.analytics.JournalAnalytics;
import com.osatum.wbn.domain.model.analytics.JournalAnalyticsType;
import com.osatum.wbn.shared.assembler.AssemblerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

@Component
@RequiredArgsConstructor
public class AnalyticsJournalViewAssembler
        implements AssemblerDto<List<JournalAnalytics>, AnalyticsJournalView, AnalyticsJournalViewParameter> {

    private final CountryViewMapper countryViewMapper;

    @Override
    public AnalyticsJournalView toDto(AnalyticsJournalViewParameter parameters) {
        var view = Optional.ofNullable(parameters.getDto()).orElseGet(AnalyticsJournalView::new);
        view.setJournalId(parameters.getJournalId());

        var items = parameters.getEntity();
        //setContainerIfPresent(items, JournalAnalyticsType.IF, view::setIndicatorsIF);
        //setContainerIfPresent(items, JournalAnalyticsType.SJR, view::setIndicatorsSJR);
        //setContainerIfPresent(items, JournalAnalyticsType.TC, view::setIndicatorsTC);
       // setContainerIfPresent(items, JournalAnalyticsType.CS, view::setIndicatorsCS);
        //setContainerIfPresent(items, JournalAnalyticsType.TC, view::setIndicatorsTC);
        //setContainerIfPresent(items, JournalAnalyticsType.SNIP, view::setIndicatorsSNIP);
        //setContainerIfPresent(items, JournalAnalyticsType.AI, view::setIndicatorsAI);
        //setContainerIfPresent(items, JournalAnalyticsType.CNCI, view::setIndicatorsCNCI);



        //setContainerIfPresent(items, JournalAnalyticsType.ART_AMOUNT, view::setArticleAmount);
        setContainerIfPresent(items, JournalAnalyticsType.ART_SCIENTIFIC_AMOUNT, view::setArticleScientificAmount);
        setContainerIfPresent(items, JournalAnalyticsType.ART_MANUSCRIPT_RECEIVED, view::setArticleManuscriptReceived);

        setContainerIfPresent(items, JournalAnalyticsType.AUTHOR_UNIQUE, view::setAuthorUnique);
        //setContainerIfPresent(items, JournalAnalyticsType.AUTHOR_EXTERNAL, view::setAuthorExternal);
        //setContainerIfPresent(items, JournalAnalyticsType.AUTHOR_FOREIGN, view::setAuthorForeign);

        //setContainerIfPresent(items, JournalAnalyticsType.REVIEWER_UNIQUE, view::setReviewerUnique);
        //setContainerIfPresent(items, JournalAnalyticsType.REVIEWER_EXTERNAL, view::setReviewerExternal);
        setContainerIfPresent(items, JournalAnalyticsType.REVIEWER_FOREIGN, view::setReviewerForeign);

        //setContainerIfPresent(items, JournalAnalyticsType.DIRECTOR_UNIQUE, view::setDirectorUnique);
        //setContainerIfPresent(items, JournalAnalyticsType.DIRECTOR_EXTERNAL, view::setDirectorExternal);
        //setContainerIfPresent(items, JournalAnalyticsType.DIRECTOR_FOREIGN, view::setDirectorForeign);

        List<JournalAnalytics> reviewers = items.stream().filter(x-> JournalAnalyticsType.REVIEWER_COUNTRY.equals(x.getType())).toList();
        if (reviewers != null){
            view.setReviewersByCountry(new ArrayList<>());
            for (JournalAnalytics reviewer : reviewers) {
                AnalyticsCountryView countryView = new AnalyticsCountryView();
                countryView.setType(JournalAnalyticsType.REVIEWER_COUNTRY);
                countryView.setCountry(countryViewMapper.toView(reviewer.getCountryCode()));
                countryView.setValue(reviewer.getValue());
                view.getReviewersByCountry().add(countryView);
            }
        }
        return view;
    }



    private void setContainerIfPresent(
            List<JournalAnalytics> items,
            JournalAnalyticsType type,
            java.util.function.Consumer<AnalyticsContainerView> setter
    ) {
        var list = buildItems(items, type);
        if (CollectionUtils.isEmpty(list)) {
            return;
        }

        var range = calculateRange(list);
        setter.accept(new AnalyticsContainerView(type, list, range.min(), range.max()));
    }

    private List<AnalyticsItemView> buildItems(List<JournalAnalytics> items, JournalAnalyticsType type) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        // year -> value dla danego typu (pierwsza wartość wygrywa; jeśli chcesz inaczej, zmień merge)

        List<AnalyticsItemView> result = new ArrayList<>();

        for (var j : items) {
            if (!type.equals(j.getType())) {
                continue;
            }
            var year = j.getYear();
            if (year == null) {
                continue;
            }
            var v = new AnalyticsItemView();
            v.setYear(year);
            v.setValue(j.getValue());

            result.add(v);
        }

        return result;
    }

    private Range calculateRange(List<AnalyticsItemView> items) {
        if (items == null || items.isEmpty()) {
            return new Range(0D, 0D);
        }

        Double min = null;
        Double max = null;

        for (var it : items) {
            var val = it.getValue();
            if (val == null) continue;

            if (min == null || val < min) min = val;
            if (max == null || val > max) max = val;
        }

        if (min == null || max == null) {
            return new Range(0D, 0D);
        }

        // zachowuję Twoją logikę: +/- 25% i floor
        double minAdjusted = Math.floor(min - (min / 4));
        double maxAdjusted = Math.floor(Math.max(max + (max / 4), 0));

        return new Range(minAdjusted, maxAdjusted);
    }

    private record Range(Double min, Double max) {}
}
