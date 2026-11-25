package com.osatum.wbn.infrastructure.elasticsearch.repository;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.osatum.wbn.application.journal.query.JournalSearchCriteria;
import com.osatum.wbn.application.journal.service.ExchangeRateService;
import com.osatum.wbn.application.journal.service.ExchangeRateSnapshot;
import com.osatum.wbn.application.journal.view.JournalFeeView;
import com.osatum.wbn.application.journal.view.JournalFeesView;
import com.osatum.wbn.domain.model.journal.FeeCategory;
import com.osatum.wbn.domain.model.journal.JournalFeeTerms;
import com.osatum.wbn.domain.service.journal.JournalFeePolicy;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Przekłada opłaty P1 na warunki indeksu i opis wyniku z zachowaniem wspólnego zestawu kursów.
 * Limity są stosowane przed paginacją, aby licznik i zawartość strony dotyczyły tego samego wyszukiwania.
 */
@Component
@RequiredArgsConstructor
public class JournalFeeQuerySupport {
    private final ExchangeRateService rates;
    private final JournalFeePolicy policy;
    private final ElasticsearchOperations operations;

    /**
     * Definiuje niezależne warunki braku opłaty, używane również przez globalne liczniki metadanych.
     */
    public Map<String, Query> noFeeFilters() {
        return Map.of("otherFeesFreeOnly", free(FeeCategory.OTHER),
                "openAccessFeeFreeOnly", free(FeeCategory.OPEN_ACCESS), "publicationFeeFreeOnly", free(FeeCategory.PUBLICATION),
                "rapidReviewFeeFreeOnly", free(FeeCategory.RAPID_REVIEW), "submissionFeeFreeOnly", free(FeeCategory.SUBMISSION));
    }

    private Query free(FeeCategory category) {
        return nested("fees", Query.of(query -> query.bool(value -> value.filter(category(category)).filter(noFee()))));
    }

    /**
     * Pobiera lokalne kursy tylko dla aktywnych limitów PLN i sprawdza pokrycie walut przed filtrowaniem.
     * Osobne warunki kategorii trafiają do wspólnego AND; samo zaznaczenie braku opłaty nie wymaga kursów.
     */
    public Context prepare(JournalSearchCriteria request, List<Query> baseFilters) {
        var limited = request.feeFilters().entrySet().stream().filter(entry -> entry.getValue().maximumPln() != null)
                .map(Map.Entry::getKey).toList();
        ExchangeRateSnapshot snapshot = limited.isEmpty() ? ExchangeRateSnapshot.plnOnly() : rates.snapshot();
        if (!limited.isEmpty()) {
            verifyCurrencyCoverage(baseFilters, limited, snapshot);
        }
        var conditions = new ArrayList<Query>();
        request.feeFilters().forEach((category, filter) -> {
            if (filter.noFee()) {
                conditions.add(free(category));
            } else if (filter.maximumPln() != null) {
                conditions.add(limit(category, filter.maximumPln(), snapshot));
            }
        });
        return new Context(List.copyOf(conditions), snapshot, !limited.isEmpty());
    }

    /**
     * Zachowuje zestaw kursów użyty przez filtr; bez limitów doczytuje go tylko wtedy, gdy wynik
     * wymaga wyboru największej kwoty spośród różnych walut. Nie przelicza kwot prezentowanych na PLN.
     */
    public ExchangeRateSnapshot forResults(List<JournalDocument> documents, Context context) {
        boolean needed = documents.stream().filter(document -> document.getFees() != null)
                .flatMap(document -> document.getFees().stream()).filter(fee -> fee.category() != FeeCategory.OTHER).anyMatch(policy::needsRates);
        ExchangeRateSnapshot snapshot = needed && !context.loaded() ? rates.snapshot() : context.snapshot();
        if (needed) {
            documents.stream().filter(document -> document.getFees() != null).flatMap(document -> document.getFees().stream())
                    .filter(fee -> fee.category() != FeeCategory.OTHER).filter(policy::needsRates)
                    .flatMap(fee -> fee.amounts().stream()).filter(amount -> amount.value() > 0)
                    .forEach(amount -> snapshot.require(amount.currency()));
        }
        return snapshot;
    }

    /**
     * Prezentuje wybrany wariant w oryginalnej walucie; dla innych opłat przekazuje jedynie ich występowanie.
     * Komunikat o całkowitym braku opłat wymaga potwierdzenia wszystkich pięciu kategorii.
     */
    public JournalFeesView summary(JournalDocument document, ExchangeRateSnapshot snapshot) {
        List<JournalFeeTerms> fees = document.getFees();
        if (fees == null || fees.isEmpty() || fees.stream().allMatch(fee -> fee.type() == null)) {
            return null;
        }
        boolean noFees = fees.size() == FeeCategory.values().length && fees.stream().allMatch(JournalFeeTerms::noFee);
        var items = new ArrayList<JournalFeeView>();
        for (JournalFeeTerms fee : fees) {
            if (fee.noFee() || fee.type() == null) {
                continue;
            }
            JournalFeeTerms.Amount selected = fee.category() == FeeCategory.OTHER ? null : policy.select(fee, snapshot.rates());
            items.add(new JournalFeeView(fee.category(), selected == null ? null : BigDecimal.valueOf(selected.value()),
                    selected == null ? null : selected.currency()));
        }
        return new JournalFeesView(noFees, List.copyOf(items));
    }

    /**
     * Przelicza domknięty limit PLN na progi w walutach źródłowych i wyklucza przekroczenie dowolnej kwoty.
     * Zaokrąglenie progu w dół odpowiada całkowitym kwotom zapisanym w projekcji opłat.
     */
    private Query limit(FeeCategory category, BigDecimal maximum, ExchangeRateSnapshot snapshot) {
        var excessive = new ArrayList<Query>();
        snapshot.rates().forEach((currency, rate) -> {
            BigDecimal threshold = maximum.divide(rate, 0, RoundingMode.FLOOR).min(BigDecimal.valueOf(Integer.MAX_VALUE));
            excessive.add(Query.of(query -> query.bool(value -> value
                    .filter(Query.of(term -> term.term(field -> field.field("fees.amounts.currency").value(currency))))
                    .filter(Query.of(range -> range.range(field -> field.number(number -> number.field("fees.amounts.value")
                            .gt(threshold.doubleValue()))))))));
        });
        Query overLimit = nested("fees.amounts", Query.of(query -> query.bool(value -> value.should(excessive).minimumShouldMatch("1"))));
        return nested("fees", Query.of(query -> query.bool(value -> value.filter(category(category))
                .should(noFee())
                .should(Query.of(candidate -> candidate.bool(conditions -> conditions
                        .filter(Query.of(term -> term.term(field -> field.field("fees.comparable").value(true))))
                        .mustNot(overLimit))))
                .minimumShouldMatch("1"))));
    }

    /**
     * Sprawdza dodatnie kwoty ograniczanych kategorii w całym zbiorze spełniającym pozostałe filtry,
     * nie tylko na bieżącej stronie. Brak kursu ma zgłosić niedostępność, a nie po cichu usunąć czasopismo.
     */
    private void verifyCurrencyCoverage(List<Query> filters, List<FeeCategory> categories, ExchangeRateSnapshot snapshot) {
        var query = NativeQuery.builder().withQuery(Query.of(value -> value.bool(conditions -> conditions.filter(filters))))
                .withMaxResults(0).withAggregation("feeCurrencies", Aggregation.of(value -> value.nested(nested -> nested.path("fees"))
                        .aggregations("selected", selected -> selected.filter(filter -> filter.terms(terms -> terms.field("fees.category")
                                        .terms(values -> values.value(categories.stream().map(category -> FieldValue.of(category.name())).toList()))))
                                .aggregations("amounts", amounts -> amounts.nested(nested -> nested.path("fees.amounts"))
                                        .aggregations("positive", positive -> positive.filter(filter -> filter.range(range -> range.number(number -> number
                                                        .field("fees.amounts.value").gt(0.0))))
                                                .aggregations("currencies", currencies -> currencies.terms(terms -> terms.field("fees.amounts.currency")
                                                        .size(10000).shardSize(10000)))))))).build();
        var hits = operations.search(query, JournalDocument.class);
        if (!(hits.getAggregations() instanceof ElasticsearchAggregations aggregations) || aggregations.get("feeCurrencies") == null) {
            throw new IllegalStateException("Fee currency metadata unavailable");
        }
        var currencies = aggregations.get("feeCurrencies").aggregation().getAggregate().nested().aggregations().get("selected")
                .filter().aggregations().get("amounts").nested().aggregations().get("positive").filter().aggregations().get("currencies").sterms();
        if ((currencies.sumOtherDocCount() != null && currencies.sumOtherDocCount() > 0)
                || (currencies.docCountErrorUpperBound() != null && currencies.docCountErrorUpperBound() > 0)) {
            throw new IllegalStateException("Fee currency metadata incomplete");
        }
        currencies.buckets().array().forEach(bucket -> snapshot.require(bucket.key().stringValue()));
    }

    private Query category(FeeCategory category) {
        return Query.of(query -> query.term(value -> value.field("fees.category").value(category.name())));
    }

    private Query noFee() {
        return Query.of(query -> query.term(value -> value.field("fees.noFee").value(true)));
    }

    private Query nested(String path, Query query) {
        return Query.of(value -> value.nested(nested -> nested.path(path).query(query)));
    }

    public record Context(List<Query> conditions, ExchangeRateSnapshot snapshot, boolean loaded) {
    }
}
