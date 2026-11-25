package com.osatum.wbn.infrastructure.elasticsearch.repository;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortMode;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.FieldType;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.journal.query.JournalSearchCriteria;
import com.osatum.wbn.application.journal.query.JournalSearchCriteriaRules;
import com.osatum.wbn.application.journal.service.JournalListQueryHandler;
import com.osatum.wbn.application.journal.view.JournalListView;
import com.osatum.wbn.application.journal.view.JournalSearchMetadata;
import com.osatum.wbn.application.journal.view.JournalSearchCounts;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.journal.JournalInstitution;
import com.osatum.wbn.infrastructure.elasticsearch.assembler.JournalSearchResponseAssembler;
import com.osatum.wbn.infrastructure.elasticsearch.assembler.JournalSearchResponseParameters;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Wyszukuje roczne projekcje aktywnych czasopism oraz buduje globalne metadane P1.
 * Rok ankiety i ICV jest wspólnym rokiem oceny; obowiązujący wykaz MNiSW pozostaje niezależny.
 */
@Component
@RequiredArgsConstructor
public class JournalListQueryHandlerImpl implements JournalListQueryHandler {

    private final JournalSearchResponseAssembler assembler;

    @Value("${app.cover-path}")
    private String coverPath;
    private final ElasticsearchOperations operations;
    private final ConfigurationService configurationService;
    private final JournalFeeQuerySupport feeSupport;
    private final JournalSearchPageLoader pageLoader;

    /**
     * Liczy wartości w aktywnych czasopismach wskazanego roku ankiety, niezależnie od kryteriów formularza.
     * Zakres ICV obejmuje tylko ocenione rekordy INDEXED; klasy MNiSW pochodzą z obowiązującego wykazu.
     */
    @Override
    public JournalSearchMetadata getSearchMetadata() {
        return getSearchMetadata(null);
    }

    @Override
    public JournalSearchMetadata getSearchMetadata(Integer surveyYear) {
        Integer year = configurationService.resolveSearchYear(surveyYear);
        NativeQuery query = NativeQuery.builder()
                .withQuery(qualifiedJournals(null, year))
                .withMaxResults(0)
                .withAggregation("countries", dictionaryAggregation(null, "countryCode"))
                .withAggregation("languages", dictionaryAggregation("languages", "languages.code"))
                .withAggregation("disciplines", dictionaryAggregation("scientificCategories", "scientificCategories.code"))
                .withAggregation("repositories", dictionaryAggregation(null, "repositoryIds"))
                .withAggregation("licenses", dictionaryAggregation(null, "articlePublishedLicense"))
                .withAggregation("accessModels", dictionaryAggregation(null, "paidArticle"))
                .withAggregation("frequencies", dictionaryAggregation(null, "frequencyCode"))
                .withAggregation("reviewModels", dictionaryAggregation(null, "reviewModels"))
                .withAggregation("features", Aggregation.of(value -> value.filters(filters -> filters
                        .filters(conditions -> conditions.keyed(featureFilters())))))
                .withAggregation("icv", Aggregation.of(value -> value.nested(nested -> nested.path("ratings"))
                        .aggregations("current", current -> current
                                .filter(ratingStatusInnerFilter("ICV", year, RatingStatus.INDEXED))
                                .aggregations("range", range -> range.stats(stats -> stats.field("ratings.valueNum"))))))
                .withAggregation("mnisw", Aggregation.of(value -> value.nested(nested -> nested.path("ratings"))
                        .aggregations("current", current -> current
                                .filter(ratingInnerFilter("MNW", configurationService.getCurrentMniswYear()))
                                .aggregations("classes", classes -> classes.terms(terms -> terms.field("ratings.valueNum").size(10000)))
                                .aggregations("disciplines", dictionaryAggregation("ratings.disciplines", "ratings.disciplines.code")))))
                .build();
        SearchHits<JournalDocument> hits = operations.search(query, JournalDocument.class);
        if (!(hits.getAggregations() instanceof ElasticsearchAggregations aggregations) || aggregations.get("mnisw") == null) {
            throw new IllegalStateException("MNiSW search metadata is unavailable");
        }
        Aggregate classes = aggregations.get("mnisw").aggregation().getAggregate().nested()
                .aggregations().get("current").filter().aggregations().get("classes");
        List<Integer> points;
        if (classes.isDterms()) {
            if (classes.dterms().sumOtherDocCount() != null && classes.dterms().sumOtherDocCount() > 0) {
                throw new IllegalStateException("MNiSW point classes aggregation is incomplete");
            }
            points = classes.dterms().buckets().array().stream().map(bucket -> pointClass(bucket.key())).toList();
        } else if (classes.isLterms()) {
            if (classes.lterms().sumOtherDocCount() != null && classes.lterms().sumOtherDocCount() > 0) {
                throw new IllegalStateException("MNiSW point classes aggregation is incomplete");
            }
            points = classes.lterms().buckets().array().stream().map(bucket -> pointClass(bucket.key())).toList();
        } else {
            throw new IllegalStateException("Unsupported MNiSW point classes aggregation");
        }
        var range = requiredAggregate(aggregations, "icv").nested().aggregations().get("current")
                .filter().aggregations().get("range").stats();
        BigDecimal minimum = range.count() == 0 ? null : icvBound(range.min());
        BigDecimal maximum = range.count() == 0 ? null : icvBound(range.max());
        return new JournalSearchMetadata(points.stream().distinct().sorted().toList(), minimum, maximum,
                dictionaryCounts(requiredAggregate(aggregations, "countries"), false),
                dictionaryCounts(requiredAggregate(aggregations, "languages"), true),
                dictionaryCounts(requiredAggregate(aggregations, "disciplines"), true),
                dictionaryCounts(requiredAggregate(aggregations, "repositories"), false),
                dictionaryCounts(requiredAggregate(aggregations, "licenses"), false),
                dictionaryCounts(requiredAggregate(aggregations, "accessModels"), false),
                dictionaryCounts(requiredAggregate(aggregations, "frequencies"), false),
                featureCounts(requiredAggregate(aggregations, "features")),
                dictionaryCounts(requiredAggregate(aggregations, "mnisw").nested().aggregations().get("current")
                        .filter().aggregations().get("disciplines"), true),
                dictionaryCounts(requiredAggregate(aggregations, "reviewModels"), false));
    }

    private Map<String, Long> featureCounts(Aggregate aggregate) {
        Map<String, Long> counts = new TreeMap<>();
        aggregate.filters().buckets().keyed().forEach((code, bucket) -> counts.put(code, bucket.docCount()));
        return Map.copyOf(counts);
    }

    private Map<String, Query> featureFilters() {
        var filters = new TreeMap<>(nonFeeFeatureFilters());
        filters.putAll(feeSupport.noFeeFilters());
        return filters;
    }

    private Map<String, Query> nonFeeFeatureFilters() {
        return Map.of(
                "doiOnly", termIn("doiType", new String[]{"ALL", "PART"}),
                "editorialSystemOnly", term("editorialTools", "EDITORIAL_SYSTEM"),
                "newsletterOnly", term("editorialTools", "NEWSLETTERS"),
                "socialMediaOnly", Query.of(query -> query.term(term -> term.field("socialMediaUsed").value(true))),
                "antiPlagiarismOnly", term("editorialTools", "ANTI_PLAGIARISM_SYSTEM"),
                "aopOnly", Query.of(query -> query.term(term -> term.field("aop").value(true))));
    }

    /**
     * Łączy różne kryteria przez AND. Wybrane bazy wymagają wszystkich identyfikatorów,
     * natomiast wartości pojedynczych filtrów licencyjnych, dostępu i częstotliwości tworzą OR.
     */
    private void addNonFeeFilters(List<Query> filters, JournalSearchCriteria request) {
        Map<String, Boolean> selected = Map.of("doiOnly", request.doiOnly(),
                "editorialSystemOnly", request.editorialSystemOnly(), "newsletterOnly", request.newsletterOnly(),
                "socialMediaOnly", request.socialMediaOnly(), "antiPlagiarismOnly", request.antiPlagiarismOnly(),
                "aopOnly", request.aopOnly());
        nonFeeFeatureFilters().forEach((code, query) -> {
            if (selected.get(code)) {
                filters.add(query);
            }
        });
        if (request.repositoryIds() != null) {
            request.repositoryIds().stream().map(value -> value.toBigIntegerExact().toString()).distinct()
                    .forEach(id -> filters.add(term("repositoryIds", id)));
        }
        addRangeFilter(filters, "firstIssueYear", request.firstIssueYearFrom(), request.firstIssueYearTo());
    }

    private void addEnumFilter(List<Query> filters, String field, List<? extends Enum<?>> values) {
        if (values != null && !values.isEmpty()) {
            filters.add(termIn(field, values.stream().map(Enum::name).distinct().toArray(String[]::new)));
        }
    }

    /**
     * Zawsze ogranicza do aktywnego rekordu i skonfigurowanego roku ankiety.
     * Brak ograniczenia statusu służy metadanym; wyniki domyślnie wymagają INDEXED, chyba że wybrano inny status.
     */
    private Query qualifiedJournals(RatingStatus ratingStatus, Integer year) {
        return Query.of(query -> query.bool(conditions -> {
            conditions.filter(term("state", EntityState.ACTIVE.name()))
                    .filter(Query.of(value -> value.term(term -> term.field("surveyYear")
                            .value(year))));
            if (ratingStatus != null) {
                conditions.filter(ratingStatus == RatingStatus.INDEXED
                        ? ratingFilter("ICV", year, null, null, ratingStatus)
                        : ratingStatusFilter("ICV", year, ratingStatus));
            }
            return conditions;
        }));
    }

    /**
     * Dla pól zagnieżdżonych wraca do poziomu czasopisma, aby licznik oznaczał czasopisma, nie elementy kolekcji.
     */
    private Aggregation dictionaryAggregation(String path, String field) {
        Aggregation values = Aggregation.of(value -> {
            value.terms(terms -> terms.field(field).size(10000).shardSize(10000));
            if (path != null) {
                value.aggregations("journals", journals -> journals.reverseNested(reverse -> reverse));
            }
            return value;
        });
        return path == null ? values : Aggregation.of(value -> value.nested(nested -> nested.path(path))
                .aggregations("values", values));
    }

    private Aggregate requiredAggregate(ElasticsearchAggregations aggregations, String name) {
        if (aggregations.get(name) == null) {
            throw new IllegalStateException("Search metadata is unavailable: " + name);
        }
        return aggregations.get(name).aggregation().getAggregate();
    }

    private Map<String, Long> dictionaryCounts(Aggregate aggregate, boolean nested) {
        Aggregate values = nested ? aggregate.nested().aggregations().get("values") : aggregate;
        if (values == null || !values.isSterms()) {
            throw new IllegalStateException("Search dictionary metadata is unavailable");
        }
        var terms = values.sterms();
        if ((terms.sumOtherDocCount() != null && terms.sumOtherDocCount() > 0)
                || (terms.docCountErrorUpperBound() != null && terms.docCountErrorUpperBound() > 0)) {
            throw new IllegalStateException("Search dictionary aggregation is incomplete");
        }
        Map<String, Long> counts = new TreeMap<>();
        for (var bucket : terms.buckets().array()) {
            counts.put(bucket.key().stringValue(), nested
                    ? bucket.aggregations().get("journals").reverseNested().docCount() : bucket.docCount());
        }
        return Map.copyOf(counts);
    }

    private BigDecimal icvBound(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            throw new IllegalStateException("Invalid ICV range in the search index");
        }
        return BigDecimal.valueOf(value).stripTrailingZeros();
    }

    private Integer pointClass(double value) {
        if (!Double.isFinite(value) || value < 0 || value > Integer.MAX_VALUE || value != Math.rint(value)) {
            throw new IllegalStateException("Invalid MNiSW point class in the search index");
        }
        return (int) value;
    }

    /**
     * Buduje wspólny filtr, sprawdza rzeczywiste klasy MNiSW i stosuje limity opłat przed pobraniem strony.
     * Opis opłat korzysta z tego samego kontekstu kursów, a wskaźnik odrzuceń pozostaje wyłącznie filtrem.
     */
    @Override
    public PageResult<JournalListView> execute(JournalSearchCriteria req) {
        var prepared = prepareFilters(req);
        var filter = prepared.all();
        var feeContext = feeSupport.prepare(req, filter);
        filter.addAll(feeContext.conditions());
        Integer year = configurationService.resolveSearchYear(req.surveyYear());
        var result = pageLoader.load(Query.of(value -> value.bool(conditions -> conditions.filter(filter))),
                sortOptions(req.sortBy(), year), req.page(), req.size());
        var feeRates = feeSupport.forResults(result.hits().stream().map(SearchHit::getContent).toList(), feeContext);
        List<JournalListView> content = result.hits().stream()
                .map(SearchHit::getContent)
                .map(document -> {
                    JournalListView view = assembler.toDto(new JournalSearchResponseParameters(document, year, coverPath));
                    view.setFees(feeSupport.summary(document, feeRates));
                    return view;
                })
                .toList();

        return new PageResult<>(content, result.total(), Math.toIntExact(Math.ceilDiv(result.total(), req.size())), req.size());
    }

    private PreparedFilters prepareFilters(JournalSearchCriteria req) {
        if (!JournalSearchCriteriaRules.isValid(req)) {
            throw new IllegalArgumentException("Invalid journal search criteria");
        }
        List<Query> filter = new ArrayList<>();
        Map<String, List<Query>> alternatives = new TreeMap<>();
        addAlternative(alternatives, "licenses", "articlePublishedLicense", req.licenseCodes());
        addAlternative(alternatives, "accessModels", "paidArticle", req.accessModels());
        addAlternative(alternatives, "frequencies", "frequencyCode", req.frequencyCodes());
        addAlternative(alternatives, "reviewModels", "reviewModels", req.reviewModels());
        Integer year = configurationService.resolveSearchYear(req.surveyYear());
        filter.add(qualifiedJournals(req.ratingStatus(), year));
        addNonFeeFilters(filter, req);

        if (req.mniswOnly()) {
            if (req.mniswPointsFrom() != null || req.mniswPointsTo() != null) {
                List<Integer> pointClasses = getSearchMetadata(req.surveyYear()).mniswPointClasses();
                if (!isAvailablePointClass(req.mniswPointsFrom(), pointClasses)
                        || !isAvailablePointClass(req.mniswPointsTo(), pointClasses)) {
                    throw new IllegalArgumentException("MNiSW bounds must use point classes available in the current search metadata");
                }
            }
            filter.add(ratingFilter("MNW", configurationService.getCurrentMniswYear(),
                    req.mniswPointsFrom(), req.mniswPointsTo(), null));
            if (!req.mniswDisciplineCodes().isEmpty()) {
                alternatives.put("mniswDisciplines", List.of(ratingFilter("MNW", configurationService.getCurrentMniswYear(),
                        req.mniswPointsFrom(), req.mniswPointsTo(), null, req.mniswDisciplineCodes())));
            }
        }
        addRangeFilter(filter, "reviewDays", req.reviewDaysFrom(), req.reviewDaysTo());
        addRangeFilter(filter, "initialAssessmentDays", req.initialAssessmentDaysFrom(), req.initialAssessmentDaysTo());
        addRangeFilter(filter, "publicationDays", req.publicationDaysFrom(), req.publicationDaysTo());
        addRangeFilter(filter, "rejectionRate", req.rejectionRateFrom(), req.rejectionRateTo());
        // Afiliacja zagraniczna nie jest afiliacją zewnętrzną; filtr używa odrębnych odsetków z wybranego roku ankiety.
        addRangeFilter(filter, "authorForeignAffiliationPercentage", req.authorForeignAffiliationPercentageFrom(), req.authorForeignAffiliationPercentageTo());
        addRangeFilter(filter, "reviewerForeignAffiliationPercentage", req.reviewerForeignAffiliationPercentageFrom(), req.reviewerForeignAffiliationPercentageTo());

        // Dodatkowy stan z żądania nie znosi wymogu ACTIVE ustalonego przez kwalifikację P1.
        if (req.state() != null) {
            filter.add(term("state", req.state().name()));
        }

        if (hasText(req.journalTitle())) {
            filter.add(nested("titles", textFragments("titles.content.search", req.journalTitle())));
        }
        if (hasText(req.issn())) {
            String normalized = req.issn().replaceAll("[\\p{Pd}\\s\\p{Z}]+", "");
            filter.add(normalized.isEmpty() ? Query.of(query -> query.matchNone(value -> value))
                    : nested("issns", containsLiteral("issns.content.search", normalized)));
        }

        // Historyczny parametr editor zachowuje dotychczasowe mapowanie na rolę wydawcy.
        if (hasText(req.publisher())) {
            filter.add(institutionRoleName(JournalInstitution.ROLE_PUBLISHER, req.publisher().trim()));
        }
        if (hasText(req.editor())) {
            filter.add(institutionRoleName(JournalInstitution.ROLE_PUBLISHER, req.editor().trim()));
        }

        // Wielowybór języków działa jako OR; starszy pojedynczy kod jest obsługiwany jako wartość zapasowa.
        String[] languageCodes = req.languageCodes().toArray(String[]::new);
        if (languageCodes.length > 0) {
            alternatives.put("languages", List.of(nested("languages", termIn("languages.code", languageCodes))));
        }

        // Pole rocznej projekcji zawiera kraj po korekcie; nie odczytujemy kraju z ankiety innego roku.
        String[] countryCodes = req.countryCodes().toArray(String[]::new);
        if (countryCodes.length > 0) {
            alternatives.put("countries", List.of(termIn("countryCode", countryCodes)));
        }

        // Rok, status i granice muszą dotyczyć tego samego zagnieżdżonego wpisu oceny ICV.
        if (req.icvFrom() != null || req.icvTo() != null) {
            filter.add(ratingFilter("ICV", year, req.icvFrom(), req.icvTo(), RatingStatus.INDEXED));
        }

        if (!req.disciplineCodes().isEmpty()) {
            List<String> codes = req.disciplineCodes().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();

            if (!codes.isEmpty()) {
                alternatives.put("disciplines", List.of(nested("scientificCategories",
                        Query.of(x -> x.terms(t -> t
                                .field("scientificCategories.code")
                                .terms(v -> v.value(codes.stream().map(FieldValue::of).toList()))
                        ))
                )));
            }
        }

        return new PreparedFilters(filter, alternatives);
    }

    @Override
    public JournalSearchCounts getSearchCounts(JournalSearchCriteria req) {
        var prepared = prepareFilters(req);
        // Sprawdzenie walut obejmuje sumę zbiorów używanych przez liczniki poszczególnych grup.
        var feeContext = feeSupport.prepare(req, prepared.countScope());
        prepared.common().addAll(feeContext.conditions());
        var mniswFilters = new ArrayList<Query>();
        mniswFilters.add(ratingInnerFilter("MNW", configurationService.getCurrentMniswYear()));
        if (req.mniswOnly()) addRangeFilter(mniswFilters, "ratings.valueNum", req.mniswPointsFrom(), req.mniswPointsTo());
        Map<String, Aggregation> facets = new TreeMap<>();
        facets.put("countries", dictionaryAggregation(null, "countryCode"));
        facets.put("languages", dictionaryAggregation("languages", "languages.code"));
        facets.put("disciplines", dictionaryAggregation("scientificCategories", "scientificCategories.code"));
        facets.put("repositories", dictionaryAggregation(null, "repositoryIds"));
        facets.put("licenses", dictionaryAggregation(null, "articlePublishedLicense"));
        facets.put("accessModels", dictionaryAggregation(null, "paidArticle"));
        facets.put("frequencies", dictionaryAggregation(null, "frequencyCode"));
        facets.put("reviewModels", dictionaryAggregation(null, "reviewModels"));
        facets.put("features", Aggregation.of(value -> value.filters(filters -> filters.filters(values -> values.keyed(featureFilters())))));
        facets.put("mniswDisciplines", Aggregation.of(value -> value.nested(nested -> nested.path("ratings"))
                .aggregations("current", current -> current.filter(filter -> filter.bool(bool -> bool.filter(mniswFilters)))
                        .aggregations("disciplines", dictionaryAggregation("ratings.disciplines", "ratings.disciplines.code")))));
        var builder = NativeQuery.builder().withMaxResults(0).withTrackTotalHits(false)
                .withQuery(query -> query.bool(bool -> bool.filter(prepared.common())));
        facets.forEach((group, aggregation) -> builder.withAggregation(group, Aggregation.of(value -> value
                .filter(prepared.otherGroups(group)).aggregations("values", aggregation))));
        var hits = operations.search(builder.build(), JournalDocument.class);
        if (!(hits.getAggregations() instanceof ElasticsearchAggregations aggregations)) {
            throw new IllegalStateException("Search counts are unavailable");
        }
        return new JournalSearchCounts(
                dictionaryCounts(countAggregate(aggregations, "countries"), false),
                dictionaryCounts(countAggregate(aggregations, "languages"), true),
                dictionaryCounts(countAggregate(aggregations, "disciplines"), true),
                dictionaryCounts(countAggregate(aggregations, "repositories"), false),
                dictionaryCounts(countAggregate(aggregations, "licenses"), false),
                dictionaryCounts(countAggregate(aggregations, "accessModels"), false),
                dictionaryCounts(countAggregate(aggregations, "frequencies"), false),
                featureCounts(countAggregate(aggregations, "features")),
                dictionaryCounts(countAggregate(aggregations, "mniswDisciplines").nested().aggregations().get("current")
                        .filter().aggregations().get("disciplines"), true),
                dictionaryCounts(countAggregate(aggregations, "reviewModels"), false));
    }

    private Aggregate countAggregate(ElasticsearchAggregations aggregations, String group) {
        return requiredAggregate(aggregations, group).filter().aggregations().get("values");
    }

    private void addAlternative(Map<String, List<Query>> alternatives, String group, String field, List<? extends Enum<?>> values) {
        var filters = new ArrayList<Query>();
        addEnumFilter(filters, field, values);
        if (!filters.isEmpty()) alternatives.put(group, filters);
    }

    private record PreparedFilters(List<Query> common, Map<String, List<Query>> alternatives) {
        List<Query> all() {
            var filters = new ArrayList<>(common);
            alternatives.values().forEach(filters::addAll);
            return filters;
        }

        Query otherGroups(String group) {
            var filters = new ArrayList<Query>();
            alternatives.forEach((name, queries) -> {
                if (!name.equals(group)) filters.addAll(queries);
            });
            return filters.isEmpty() ? Query.of(query -> query.matchAll(value -> value))
                    : Query.of(query -> query.bool(value -> value.filter(filters)));
        }

        List<Query> countScope() {
            var filters = new ArrayList<>(common);
            if (!alternatives.isEmpty()) {
                filters.add(Query.of(query -> query.bool(value -> value
                        .should(alternatives.keySet().stream().map(this::otherGroups).toList()).minimumShouldMatch("1"))));
            }
            return filters;
        }
    }
    // ---------------- Helpers ----------------

    private Query term(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    private Query termIn(String field, String[] value) {
        return Query.of(q -> q.terms(t -> t.field(field).terms(v -> v.value(List.of(value).stream().map(FieldValue::of).toList()))));
    }

    private boolean isAvailablePointClass(BigDecimal value, List<Integer> classes) {
        return value == null || classes.stream().anyMatch(point -> value.compareTo(BigDecimal.valueOf(point)) == 0);
    }

    private Query nested(String path, Query innerQuery) {
        return Query.of(q -> q.nested(n -> n.path(path).query(innerQuery)));
    }

    /**
     * Traktuje gwiazdki i znaki zapytania wpisane przez użytkownika jako tekst, a nie operatory wildcard.
     */
    private Query containsLiteral(String field, String value) {
        String escaped = value.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?");
        return Query.of(query -> query.wildcard(wildcard -> wildcard.field(field).value("*" + escaped + "*")));
    }

    private Query textFragments(String field, String value) {
        List<Query> fragments = Arrays.stream(value.split("(?U)\\s+")).filter(fragment -> !fragment.isBlank())
                .distinct().map(fragment -> containsLiteral(field, fragment)).toList();
        return fragments.isEmpty() ? Query.of(query -> query.matchNone(none -> none))
                : Query.of(query -> query.bool(conditions -> conditions.filter(fragments)));
    }

    private Query institutionRoleName(String roleCode, String nameQuery) {
        Query inner = Query.of(q -> q.bool(b -> b
                .must(Query.of(q1 -> q1.term(t -> t.field("institutions.roleCode").value(roleCode))))
.must(textFragments("institutions.name.search", nameQuery))
        ));
        return nested("institutions", inner);
    }



    private Query ratingFilter(String ratingCode, Integer year, BigDecimal from, BigDecimal to, RatingStatus ratingStatus) {
        return ratingFilter(ratingCode, year, from, to, ratingStatus, List.of());
    }

    private Query ratingFilter(String ratingCode, Integer year, BigDecimal from, BigDecimal to,
                               RatingStatus ratingStatus, List<String> disciplineCodes) {
        Query inner = Query.of(q -> q.bool(b -> {
            b.must(Query.of(q1 -> q1.term(t -> t.field("ratings.ratingCode").value(normalizeCode(ratingCode)))));

            if (year != null) {
                b.must(Query.of(q2 -> q2.term(t -> t.field("ratings.year").value(year))));
            }

            if (ratingStatus != null) {
                b.must(Query.of(q3 -> q3.term(t -> t
                        .field("ratings.ratingStatus")
                        .value(normalizeCode(ratingStatus.name()))
                )));
            }

            if (from != null || to != null) {
                b.must(rangeNumber("ratings.valueNum", from, to));
            }

            if (!disciplineCodes.isEmpty()) {
                b.must(nested("ratings.disciplines", termIn("ratings.disciplines.code", disciplineCodes.toArray(String[]::new))));
            }

            return b;
        }));
        return nested("ratings", inner);
    }

    /**
     * Brak oceny wskazanego roku traktuje jak NOT_EVALUATED, bez zastępowania go oceną historyczną.
     */
    private Query ratingStatusFilter(String ratingCode, Integer year, RatingStatus ratingStatus) {
        if (ratingStatus == null) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        Query explicitStatusFilter = nested(
                "ratings",
                ratingStatusInnerFilter(ratingCode, year, ratingStatus)
        );

        if (!RatingStatus.NOT_EVALUATED.equals(ratingStatus)) {
            return explicitStatusFilter;
        }

        Query ratingForYearFilter = nested(
                "ratings",
                ratingInnerFilter(ratingCode, year)
        );

        return Query.of(q -> q.bool(b -> b
                .should(explicitStatusFilter)
                .should(Query.of(q1 -> q1.bool(b1 -> b1.mustNot(ratingForYearFilter))))
                .minimumShouldMatch("1")
        ));
    }

    private Query ratingStatusInnerFilter(String ratingCode, Integer year, RatingStatus ratingStatus) {
        return Query.of(q -> q.bool(b -> {
            b.must(ratingInnerFilter(ratingCode, year));
            b.must(Query.of(q1 -> q1.term(t -> t
                    .field("ratings.ratingStatus")
                    .value(normalizeCode(ratingStatus.name()))
            )));

            return b;
        }));
    }

    private Query ratingInnerFilter(String ratingCode, Integer year) {
        return Query.of(q -> q.bool(b -> {
            b.must(Query.of(q1 -> q1.term(t -> t.field("ratings.ratingCode").value(normalizeCode(ratingCode)))));

            if (year != null) {
                b.must(Query.of(q2 -> q2.term(t -> t.field("ratings.year").value(year))));
            }

            return b;
        }));
    }

    /**
     * Obsługuje osiem sortowań P1; remisy rozstrzyga tytuł i identyfikator.
     * Brak punktów MNiSW pozostaje na końcu w obu kierunkach, a końcowy ID umożliwia użycie search_after.
     */
    private List<SortOptions> sortOptions(JournalSearchCriteria.SortBy sortBy, Integer year) {
        JournalSearchCriteria.SortBy selected = sortBy == null ? JournalSearchCriteria.SortBy.SORT_BY_TITLE_ASC : sortBy;
        List<SortOptions> options = new ArrayList<>();
        switch (selected) {
            case SORT_BY_TITLE_ASC -> options.addAll(titleSortOptions(SortOrder.Asc));
            case SORT_BY_TITLE_DESC -> options.addAll(titleSortOptions(SortOrder.Desc));
            case SORT_BY_RATING_ICV_ASC -> options.add(ratingSortOption("ICV", SortOrder.Asc, year, RatingStatus.INDEXED));
            case SORT_BY_RATING_ICV_DESC -> options.add(ratingSortOption("ICV", SortOrder.Desc, year, RatingStatus.INDEXED));
            case SORT_BY_RATING_MNISW_ASC -> options.add(ratingSortOption("MNW", SortOrder.Asc, configurationService.getCurrentMniswYear(), null));
            case SORT_BY_RATING_MNISW_DESC -> options.add(ratingSortOption("MNW", SortOrder.Desc, configurationService.getCurrentMniswYear(), null));
            case SORT_BY_PUBLICATION_DAYS_ASC -> options.add(publicationDaysSortOption(SortOrder.Asc));
            case SORT_BY_PUBLICATION_DAYS_DESC -> options.add(publicationDaysSortOption(SortOrder.Desc));
        }
        if (selected != JournalSearchCriteria.SortBy.SORT_BY_TITLE_ASC && selected != JournalSearchCriteria.SortBy.SORT_BY_TITLE_DESC) {
            options.addAll(titleSortOptions(SortOrder.Asc));
        }
        options.add(SortOptions.of(sort -> sort.field(field -> field.field("id").order(SortOrder.Asc))));
        return options;
    }

    private List<SortOptions> titleSortOptions(SortOrder order) {
        return List.of(
                SortOptions.of(sort -> sort.field(field -> field.field("titleSortScriptRank")
                        .order(SortOrder.Asc).missing("_last").unmappedType(FieldType.Integer))),
                SortOptions.of(sort -> sort.field(field -> field.field("titleSortContentIcu")
                        .order(order).missing("_last").unmappedType(FieldType.Keyword))));
    }

    private SortOptions publicationDaysSortOption(SortOrder order) {
        return SortOptions.of(sort -> sort.field(field -> field.field("publicationDays")
                .order(order).missing("_last").unmappedType(FieldType.Integer)));
    }

    private SortOptions ratingSortOption(String code, SortOrder order, Integer year, RatingStatus status) {
        Query condition = status == null ? ratingInnerFilter(code, year) : ratingStatusInnerFilter(code, year, status);
        return SortOptions.of(sort -> sort.field(field -> field
                .field("ratings.valueNum")
                .order(order)
                .mode(sortMode(order))
                .missing("_last")
                .unmappedType(FieldType.Double)
                .nested(nested -> nested.path("ratings").filter(condition))));
    }

    private SortMode sortMode(SortOrder order) {
        return SortOrder.Desc.equals(order) ? SortMode.Max : SortMode.Min;
    }

    /**
     * Buduje przedział domknięty; pominięta granica nie wprowadza ograniczenia z danej strony.
     */
    private Query rangeNumber(String field, BigDecimal from, BigDecimal to) {
        return Query.of(q -> q.range(r -> r.untyped(u -> {
            u.field(field);

            if (from != null) {
                u.gte(JsonData.of(from));
            }
            if (to != null) {
                u.lte(JsonData.of(to));
            }

            return u;
        })));
    }

    /**
     * Pomija tylko brak obu granic; liczbowe zero jest poprawnym ograniczeniem, a nie brakiem danych.
     */
    private void addRangeFilter(List<Query> filters, String field, BigDecimal from, BigDecimal to) {
        if (from != null || to != null) {
            filters.add(rangeNumber(field, from, to));
        }
    }

    private boolean hasText(String s) {
        return s != null && !s.trim().isBlank();
    }


    private String normalizeCode(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT);
    }
}
