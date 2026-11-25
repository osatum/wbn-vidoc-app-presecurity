package com.osatum.wbn.infrastructure.elasticsearch.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.journal.query.JournalListQuery;
import com.osatum.wbn.application.journal.assembler.JournalSearchCriteriaMapper;
import org.mapstruct.factory.Mappers;
import com.osatum.wbn.application.journal.view.JournalListView;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.journal.TitleCode;
import com.osatum.wbn.infrastructure.elasticsearch.assembler.JournalSearchResponseAssembler;
import com.osatum.wbn.infrastructure.elasticsearch.assembler.JournalSearchResponseParameters;
import com.osatum.wbn.infrastructure.elasticsearch.assembler.simple.JournalDocumentAssembler;
import com.osatum.wbn.infrastructure.elasticsearch.assembler.simple.JournalDocumentParameters;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.Institution;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.Issn;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.Language;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.Rating;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.MniswDiscipline;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.ScientificCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "p1.elasticsearch.url", matches = ".+")
class JournalSearchQualificationIntegrationTest {
    @Test
    void contextualCountsExcludeOwnOrGroupAndKeepOtherGroupsAndUniqueJournals() {
        var query = request();
        query.setRatingStatus(RatingStatus.INDEXED);
        query.setCountryCodes(List.of("pl"));
        query.setDisciplineCodes(new String[]{"3"});
        var counts = handler.getSearchCounts(criteriaMapper.toCriteria(query));
        assertThat(counts.countryCounts()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("pl", 1L, "de", 1L));
        assertThat(counts.disciplineCounts()).containsExactly(java.util.Map.entry("3", 1L));
        assertThat(counts.languageCounts()).containsExactly(java.util.Map.entry("en", 1L));
        query.setCountryCodes(List.of("xx"));
        counts = handler.getSearchCounts(criteriaMapper.toCriteria(query));
        assertThat(counts.countryCounts()).containsEntry("pl", 1L).containsEntry("de", 1L);
        assertThat(counts.languageCounts()).isEmpty();
        assertThat(handler.getSearchMetadata().icvMax()).isEqualByComparingTo("95.75");
    }

    @Test
    void contextualRepositoryCountsRetainAllSelectedBasesAndAndFeatures() {
        var query = request();
        query.setRepositoryIds(List.of(java.math.BigDecimal.valueOf(20)));
        var counts = handler.getSearchCounts(criteriaMapper.toCriteria(query));
        assertThat(counts.repositoryCounts()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("10", 1L, "20", 1L));
        assertThat(counts.featureCounts()).containsEntry("doiOnly", 1L);
        query.setDoiOnly(true);
        query.setCountryCodes(List.of("de"));
        counts = handler.getSearchCounts(criteriaMapper.toCriteria(query));
        assertThat(counts.countryCounts()).containsExactly(java.util.Map.entry("pl", 1L));
        assertThat(counts.repositoryCounts()).isEmpty();
    }

    @Test
    void contextualMniswCountsRespectPointBoundsYearAndCountUniqueRootDocuments() {
        var journal = document(201, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70);
        journal.setRatings(List.of(mniswRating(2024, 70, "101", "101"), mniswRating(2024, 140, "202"),
                mniswRating(2025, 70, "999")));
        template.save(journal, index);
        template.indexOps(index).refresh();
        var query = request();
        query.setMniswOnly(true);
        query.setMniswPointsFrom(java.math.BigDecimal.valueOf(70));
        query.setMniswPointsTo(java.math.BigDecimal.valueOf(70));
        query.setMniswDisciplineCodes(List.of("missing"));
        var counts = handler.getSearchCounts(criteriaMapper.toCriteria(query));
        assertThat(counts.mniswDisciplineCounts()).containsExactly(java.util.Map.entry("101", 1L));
        assertThat(counts.languageCounts()).isEmpty();
    }

    @Test
    void contextualFeeCountsUseTheSameCurrencyLimitsAsResults() {
        var paid = freeStandards();
        paid.setPublicationFee(fee(com.osatum.wbn.domain.model.survey.model.FeeType.ONE_FEE, amount(100, "USD", null)));
        saveFeeDocuments(feeDocument(21, freeStandards()), feeDocument(22, paid));
        when(exchangeRates.snapshot()).thenReturn(feeRates("4", "3"));
        var query = feeRequest();
        query.setPublicationFeeMaxPln(java.math.BigDecimal.valueOf(399));
        var counts = handler.getSearchCounts(criteriaMapper.toCriteria(query));
        assertThat(counts.languageCounts()).containsExactly(java.util.Map.entry("en", 1L));
        assertThat(counts.featureCounts()).containsEntry("publicationFeeFreeOnly", 1L);
        query.setPublicationFeeMaxPln(java.math.BigDecimal.valueOf(400));
        counts = handler.getSearchCounts(criteriaMapper.toCriteria(query));
        assertThat(counts.languageCounts()).containsExactly(java.util.Map.entry("en", 2L));
    }

    private final JournalSearchCriteriaMapper criteriaMapper = Mappers.getMapper(JournalSearchCriteriaMapper.class);
    private ElasticsearchClient client;
    private ElasticsearchTemplate template;
    private IndexCoordinates index;
    private boolean created;
    private final ConfigurationService configuration = spy(new ConfigurationService());
    private JournalListQueryHandlerImpl handler;
    private final com.osatum.wbn.application.journal.service.ExchangeRateService exchangeRates = mock(com.osatum.wbn.application.journal.service.ExchangeRateService.class);
    private final com.osatum.wbn.domain.service.journal.JournalFeePolicy feePolicy =
            new com.osatum.wbn.domain.service.journal.JournalFeePolicy(new com.osatum.wbn.domain.service.journal.AuthorFeeSelectionPolicy());

    @BeforeEach
    void createIsolatedIndex() throws Exception {
        index = IndexCoordinates.of("wbn-p1-test-" + UUID.randomUUID());
        SSLContext ssl = sslContext();
        client = ElasticsearchClient.of(builder -> {
            builder.host(System.getProperty("p1.elasticsearch.url"));
            String username = System.getenv("P1_ELASTICSEARCH_USERNAME");
            if (username != null) {
                builder.usernameAndPassword(username, System.getenv("P1_ELASTICSEARCH_PASSWORD"));
            }
            if (ssl != null) {
                builder.sslContext(ssl);
            }
            return builder;
        });
        template = new ElasticsearchTemplate(client);
        String settings = new ClassPathResource("elastic/journals-settings.json").getContentAsString(StandardCharsets.UTF_8);
        String mapping = new ClassPathResource("elastic/journals-mapping.json").getContentAsString(StandardCharsets.UTF_8);
        client.indices().create(builder -> builder.index(index.getIndexName())
                .withJson(new StringReader("{\"settings\":" + settings + ",\"mappings\":" + mapping + "}")));
        created = true;
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        doAnswer(invocation -> template.search(invocation.getArgument(0, NativeQuery.class), JournalDocument.class, index))
                .when(operations).search(any(NativeQuery.class), eq(JournalDocument.class));
        JournalSearchResponseAssembler assembler = mock(JournalSearchResponseAssembler.class);
        when(assembler.toDto(any(JournalSearchResponseParameters.class))).thenAnswer(invocation -> {
            JournalSearchResponseParameters parameters = invocation.getArgument(0);
            JournalListView view = new JournalListView();
            view.setId(parameters.getEntity().getId());
            return view;
        });
        handler = new JournalListQueryHandlerImpl(assembler, operations, configuration,
                new JournalFeeQuerySupport(exchangeRates, feePolicy, operations), new JournalSearchPageLoader(operations));
        JournalDocument first = document(1, 2024, EntityState.ACTIVE, 80.25, RatingStatus.INDEXED, "pl", 70);
        first.setLanguages(List.of(new Language("en"), new Language("en")));
        first.setScientificCategories(List.of(new ScientificCategory("3"), new ScientificCategory("3")));
        JournalDocument second = document(2, 2024, EntityState.ACTIVE, 95.75, RatingStatus.INDEXED, "de", 140);
        second.setScientificCategories(List.of(new ScientificCategory("3"), new ScientificCategory("5")));
        JournalDocument otherYear = document(1, 2025, EntityState.ACTIVE, 80.25, RatingStatus.INDEXED, "gb", 70);
        JournalDocument inactive = document(3, 2024, EntityState.INACTIVE, 500, RatingStatus.INDEXED, "fr", 999);
        JournalDocument notIndexed = document(4, 2024, EntityState.ACTIVE, 600, RatingStatus.NOT_INDEXED, "fr", 999);
        JournalDocument noCurrentRating = document(5, 2024, EntityState.ACTIVE, 700, RatingStatus.INDEXED, "fr", 999);
        noCurrentRating.setRatings(List.of(rating("ICV", 2025, 700, RatingStatus.INDEXED),
                rating("MNW", 2024, 999, RatingStatus.INDEXED)));
        JournalDocument notEvaluated = document(6, 2024, EntityState.ACTIVE, 800, RatingStatus.NOT_EVALUATED, "fr", 999);
        JournalDocument noSurveyYear = document(7, 2024, EntityState.ACTIVE, 900, RatingStatus.INDEXED, "fr", 999);
        noSurveyYear.setSurveyYear(null);
        template.save(List.of(first, second, otherYear, inactive, notIndexed, noCurrentRating, notEvaluated, noSurveyYear), index);
        template.indexOps(index).refresh();
    }

    @AfterEach
    void deleteOnlyOwnedIndex() throws Exception {
        if (client != null) {
            try {
                if (created) {
                    client.indices().delete(builder -> builder.index(index.getIndexName()));
                }
            } finally {
                client._transport().close();
            }
        }
    }

    @Test
    void defaultsToAllStatusesButExplicitStatusesKeepActiveStateAndConfiguredSurveyYear() {
        assertThat(handler.execute(criteriaMapper.toCriteria(request())).data()).extracting(JournalListView::getId)
                .containsExactlyInAnyOrder(1L, 2L, 4L, 5L, 6L);
        var indexed = request();
        indexed.setRatingStatus(RatingStatus.INDEXED);
        assertThat(handler.execute(criteriaMapper.toCriteria(indexed)).data()).extracting(JournalListView::getId)
                .containsExactlyInAnyOrder(1L, 2L);
        JournalListQuery inactive = request();
        inactive.setState(EntityState.INACTIVE);
        assertThat(handler.execute(criteriaMapper.toCriteria(inactive)).totalElements()).isZero();
        JournalListQuery notIndexed = request();
        notIndexed.setRatingStatus(RatingStatus.NOT_INDEXED);
        assertThat(handler.execute(criteriaMapper.toCriteria(notIndexed)).data()).extracting(JournalListView::getId).containsExactly(4L);
        notIndexed.setRatingStatus(RatingStatus.NOT_EVALUATED);
        assertThat(handler.execute(criteriaMapper.toCriteria(notIndexed)).data()).extracting(JournalListView::getId).containsExactlyInAnyOrder(5L, 6L);
        doReturn(2025).when(configuration).getCurrentRatingYear();
        assertThat(handler.execute(criteriaMapper.toCriteria(notIndexed)).totalElements()).isZero();
    }

    @Test
    void icvBoundsWorkWithoutAStatusParameter() {
        JournalListQuery request = request();
        request.setIcvFrom(new java.math.BigDecimal("80.25"));
        request.setIcvTo(new java.math.BigDecimal("80.25"));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
    }

    @Test
    void nonIndexedStatusCanUseMniswClassesAvailableOnlyForNonIndexedJournals() {
        var request = request();
        request.setRatingStatus(RatingStatus.NOT_INDEXED);
        request.setMniswOnly(true);
        request.setMniswPointsFrom(java.math.BigDecimal.valueOf(999));
        request.setMniswPointsTo(java.math.BigDecimal.valueOf(999));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(4L);
    }

    @Test
    void metadataCountsJournalsNotNestedRowsAndDoesNotReactToOtherFilters() {
        var metadata = handler.getSearchMetadata();
        assertThat(metadata.icvMin()).isEqualByComparingTo("80.25");
        assertThat(metadata.icvMax()).isEqualByComparingTo("95.75");
        assertThat(metadata.mniswPointClasses()).containsExactly(70, 140, 999);
        assertThat(metadata.countryCounts()).containsOnlyKeys("pl", "de", "fr");
        assertThat(metadata.countryCounts()).containsEntry("pl", 1L).containsEntry("de", 1L).containsEntry("fr", 3L);
        assertThat(metadata.languageCounts()).containsOnlyKeys("en").containsEntry("en", 5L);
        assertThat(metadata.disciplineCounts()).containsOnlyKeys("3", "5")
                .containsEntry("3", 5L).containsEntry("5", 1L);
        JournalListQuery request = request();
        request.setCountryCodes(List.of("pl"));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isEqualTo(1);
        assertThat(handler.getSearchMetadata()).isEqualTo(metadata);
    }

    @Test
    void mniswMembershipAndBoundsUseTheSameCurrentListAsMetadata() {
        JournalDocument second = document(2, 2024, EntityState.ACTIVE, 95.75, RatingStatus.INDEXED, "de", 140);
        second.getRatings().removeIf(value -> "MNW".equals(value.getRatingCode()) && value.getYear() == 2024);
        template.save(second, index);
        template.indexOps(index).refresh();
        assertThat(handler.execute(criteriaMapper.toCriteria(request())).totalElements()).isEqualTo(5);
        assertThat(handler.getSearchMetadata().mniswPointClasses()).containsExactly(70, 999);
        JournalListQuery membership = request();
        membership.setMniswOnly(true);
        assertThat(handler.execute(criteriaMapper.toCriteria(membership)).data()).extracting(JournalListView::getId)
                .containsExactlyInAnyOrder(1L, 4L, 5L, 6L);
        membership.setMniswPointsFrom(java.math.BigDecimal.valueOf(70));
        membership.setMniswPointsTo(java.math.BigDecimal.valueOf(70));
        assertThat(handler.execute(criteriaMapper.toCriteria(membership)).data()).extracting(JournalListView::getId).containsExactly(1L);
    }

    @Test
    void mniswDisciplinesUseOrWithinTheSameRatingAsPointsAndRemainIndependentOfJmlAndSurveyYear() {
        var first = document(101, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70);
        first.setRatings(List.of(rating("ICV", 2024, 80, RatingStatus.INDEXED), mniswRating(2024, 70, "101", "2021")));
        var second = document(102, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 140);
        second.setRatings(List.of(rating("ICV", 2024, 80, RatingStatus.INDEXED), mniswRating(2024, 140, "2061")));
        var historical = document(103, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70);
        historical.setScientificCategories(List.of(new ScientificCategory("5")));
        historical.setRatings(List.of(rating("ICV", 2024, 80, RatingStatus.INDEXED),
                mniswRating(2024, 70, "2061"), mniswRating(2025, 70, "101")));
        var otherCode = document(104, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70);
        var icvWithDiscipline = rating("ICV", 2024, 80, RatingStatus.INDEXED);
        icvWithDiscipline.setDisciplines(List.of(new MniswDiscipline("101")));
        otherCode.setRatings(List.of(icvWithDiscipline, mniswRating(2024, 70, "2061")));
        var separateRatings = document(105, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70);
        separateRatings.setRatings(List.of(rating("ICV", 2024, 80, RatingStatus.INDEXED),
                mniswRating(2024, 70, "2061"), mniswRating(2024, 140, "101")));
        var otherSurvey = document(106, 2025, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70);
        otherSurvey.setRatings(List.of(rating("ICV", 2024, 80, RatingStatus.INDEXED), mniswRating(2024, 70, "101")));
        template.save(List.of(first, second, historical, otherCode, separateRatings, otherSurvey), index);
        template.indexOps(index).refresh();
        var request = request();
        request.setMniswOnly(true);
        request.setMniswPointsFrom(java.math.BigDecimal.valueOf(70));
        request.setMniswPointsTo(java.math.BigDecimal.valueOf(70));
        request.setMniswDisciplineCodes(List.of("101"));

        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data())
                .extracting(JournalListView::getId).containsExactly(101L);
        request.setMniswDisciplineCodes(List.of("101", "2061"));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data())
                .extracting(JournalListView::getId).containsExactlyInAnyOrder(101L, 103L, 104L, 105L);
        request.setDisciplineCodes(new String[]{"5"});
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data())
                .extracting(JournalListView::getId).containsExactly(103L);
        request.setDisciplineCodes(null);
        request.setMniswDisciplineCodes(List.of("101"));
        doReturn(2025).when(configuration).getCurrentRatingYear();
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data())
                .extracting(JournalListView::getId).containsExactly(106L);
    }

    @Test
    void mniswDisciplineCountsAreUniqueJournalsFromTheCurrentListAndGlobalMetadataScope() {
        var duplicate = document(201, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70);
        var icv = rating("ICV", 2024, 80, RatingStatus.INDEXED);
        icv.setDisciplines(List.of(new MniswDiscipline("888")));
        duplicate.setRatings(List.of(icv, mniswRating(2024, 70, "101", "101"),
                mniswRating(2024, 140, "101"), mniswRating(2025, 70, "999")));
        var nonIndexed = document(202, 2024, EntityState.ACTIVE, 80, RatingStatus.NOT_INDEXED, "pl", 140);
        nonIndexed.setRatings(List.of(rating("ICV", 2024, 80, RatingStatus.NOT_INDEXED), mniswRating(2024, 140, "101")));
        var otherSurvey = document(203, 2025, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70);
        otherSurvey.setRatings(List.of(rating("ICV", 2024, 80, RatingStatus.INDEXED), mniswRating(2024, 70, "101")));
        var inactive = document(204, 2024, EntityState.DELETED, 80, RatingStatus.INDEXED, "pl", 70);
        inactive.setRatings(List.of(rating("ICV", 2024, 80, RatingStatus.INDEXED), mniswRating(2024, 70, "101")));
        template.save(List.of(duplicate, nonIndexed, otherSurvey, inactive), index);
        template.indexOps(index).refresh();

        assertThat(handler.getSearchMetadata().mniswDisciplineCounts()).containsExactly(java.util.Map.entry("101", 2L));
    }

    private Rating mniswRating(int year, double points, String... codes) {
        var rating = rating("MNW", year, points, null);
        rating.setDisciplines(Arrays.stream(codes).map(MniswDiscipline::new).toList());
        return rating;
    }

    @Test
    void currentRatingYearChangesSurveyAndIcvButNotTheMniswList() {
        doReturn(2025).when(configuration).getCurrentRatingYear();
        assertThat(handler.execute(criteriaMapper.toCriteria(request())).data()).extracting(JournalListView::getId).containsExactly(1L);
        var metadata = handler.getSearchMetadata();
        assertThat(metadata.icvMin()).isEqualByComparingTo("999");
        assertThat(metadata.icvMax()).isEqualByComparingTo("999");
        assertThat(metadata.mniswPointClasses()).containsExactly(70);
        assertThat(metadata.countryCounts()).containsOnlyKeys("gb");
    }

    @Test
    void emptyQualifiedSetHasEmptyMetadataWithoutSubstituteValues() {
        doReturn(2026).when(configuration).getCurrentRatingYear();
        assertThat(handler.execute(criteriaMapper.toCriteria(request())).totalElements()).isZero();
        var metadata = handler.getSearchMetadata();
        assertThat(metadata.icvMin()).isNull();
        assertThat(metadata.icvMax()).isNull();
        assertThat(metadata.mniswPointClasses()).isEmpty();
        assertThat(metadata.countryCounts()).isEmpty();
        assertThat(metadata.languageCounts()).isEmpty();
        assertThat(metadata.disciplineCounts()).isEmpty();
    }

    @Test
    void nextSearchAndMetadataReflectChangedDataWithoutPersistingAResultSet() {
        template.save(document(4, 2024, EntityState.ACTIVE, 100.5, RatingStatus.INDEXED, "pl", 200), index);
        template.indexOps(index).refresh();
        assertThat(handler.execute(criteriaMapper.toCriteria(request())).totalElements()).isEqualTo(5);
        var indexed = request();
        indexed.setRatingStatus(RatingStatus.INDEXED);
        assertThat(handler.execute(criteriaMapper.toCriteria(indexed)).totalElements()).isEqualTo(3);
        assertThat(handler.getSearchMetadata().countryCounts()).containsEntry("pl", 2L);
        assertThat(handler.getSearchMetadata().icvMax()).isEqualByComparingTo("100.5");
    }

    @ParameterizedTest
    @ValueSource(strings = {"doiOnly", "editorialSystemOnly", "newsletterOnly", "socialMediaOnly", "antiPlagiarismOnly", "aopOnly"})
    void nonFeeCheckboxesRequireTheDeclaredFeature(String feature) {
        JournalListQuery request = request();
        switch (feature) {
            case "doiOnly" -> request.setDoiOnly(true);
            case "editorialSystemOnly" -> request.setEditorialSystemOnly(true);
            case "newsletterOnly" -> request.setNewsletterOnly(true);
            case "socialMediaOnly" -> request.setSocialMediaOnly(true);
            case "antiPlagiarismOnly" -> request.setAntiPlagiarismOnly(true);
            case "aopOnly" -> request.setAopOnly(true);
            default -> throw new IllegalArgumentException(feature);
        }
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
        assertThat(handler.getSearchMetadata().featureCounts()).containsEntry(feature, 1L);
    }

    @Test
    void repositorySelectionsUseAndWhileLicenseFrequencyAndAccessListsUseOr() {
        JournalListQuery request = request();
        request.setRepositoryIds(List.of(java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(20)));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
        request.setRepositoryIds(List.of(java.math.BigDecimal.TEN));
        request.setLicenseCodes(List.of(com.osatum.wbn.domain.model.survey.model.ArticlePublishedLicense.CC_BY,
                com.osatum.wbn.domain.model.survey.model.ArticlePublishedLicense.CC_BY_NC));
        request.setFrequencyCodes(List.of(com.osatum.wbn.domain.model.journal.JournalFrequency.q,
                com.osatum.wbn.domain.model.journal.JournalFrequency.m));
        request.setAccessModels(List.of(com.osatum.wbn.domain.model.journal.PaidArticleType.FREE_ARTICLES,
                com.osatum.wbn.domain.model.journal.PaidArticleType.PAID_ARTICLES));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId)
                .containsExactlyInAnyOrder(1L, 2L, 4L, 5L, 6L);
        request.setAccessModels(List.of(com.osatum.wbn.domain.model.journal.PaidArticleType.PAID_ARTICLES));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId)
                .containsExactlyInAnyOrder(2L, 4L, 5L, 6L);
        request.setRepositoryIds(List.of(java.math.BigDecimal.valueOf(20)));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
    }

    @Test
    void firstIssueYearsAreInclusiveAndSupportIndependentBounds() {
        JournalListQuery request = request();
        request.setFirstIssueYearTo(java.math.BigDecimal.valueOf(1960));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
        request.setFirstIssueYearFrom(java.math.BigDecimal.valueOf(1960));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
        request.setFirstIssueYearFrom(java.math.BigDecimal.valueOf(1970));
        request.setFirstIssueYearTo(null);
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId)
                .containsExactlyInAnyOrder(2L, 4L, 5L, 6L);
    }

    @Test
    void reviewModelsMatchAnySelectedModelInTheRequestedSurveyYearAndExposeCounts() {
        var first = document(1, 2024, EntityState.ACTIVE, 80.25, RatingStatus.INDEXED, "pl", 70);
        first.setReviewModels(List.of("DOUBLE_BLIND_REVIEW", "OPEN_PEER_REVIEW"));
        var second = document(2, 2024, EntityState.ACTIVE, 95.75, RatingStatus.INDEXED, "de", 140);
        second.setReviewModels(List.of("SINGLE_BLIND_REVIEW"));
        var later = document(1, 2025, EntityState.ACTIVE, 80.25, RatingStatus.INDEXED, "pl", 70);
        later.setReviewModels(List.of("SINGLE_BLIND_REVIEW"));
        template.save(List.of(first, second, later), index);
        template.indexOps(index).refresh();
        var request = request();
        request.setReviewModels(List.of(com.osatum.wbn.domain.model.journal.JournalReviewModel.DOUBLE_BLIND_REVIEW));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
        request.setReviewModels(List.of(com.osatum.wbn.domain.model.journal.JournalReviewModel.DOUBLE_BLIND_REVIEW,
                com.osatum.wbn.domain.model.journal.JournalReviewModel.SINGLE_BLIND_REVIEW));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId)
                .containsExactlyInAnyOrder(1L, 2L);
        request.setCountryCodes(List.of("de"));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(2L);
        assertThat(handler.getSearchMetadata(2024).reviewModelCounts()).containsExactlyInAnyOrderEntriesOf(
                java.util.Map.of("DOUBLE_BLIND_REVIEW", 1L, "SINGLE_BLIND_REVIEW", 1L, "OPEN_PEER_REVIEW", 1L));
        request.setCountryCodes(List.of());
        request.setSurveyYear(2025);
        request.setReviewModels(List.of(com.osatum.wbn.domain.model.journal.JournalReviewModel.SINGLE_BLIND_REVIEW));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
        assertThat(handler.getSearchMetadata(2025).reviewModelCounts()).containsOnlyKeys("SINGLE_BLIND_REVIEW");
        request.setSurveyYear(2024);
        request.setReviewModels(List.of());
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isEqualTo(5);
    }

    @Test
    void newslettersAndSocialMediaAreIndependentAndCombinedWithAnd() {
        JournalDocument newsletter = document(9, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70);
        newsletter.setEditorialTools(List.of("NEWSLETTERS"));
        newsletter.setSocialMediaUsed(false);
        JournalDocument social = document(10, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70);
        social.setEditorialTools(List.of());
        social.setSocialMediaUsed(true);
        template.save(List.of(newsletter, social), index);
        template.indexOps(index).refresh();
        JournalListQuery request = request();
        request.setNewsletterOnly(true);
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactlyInAnyOrder(1L, 9L);
        request.setNewsletterOnly(false);
        request.setSocialMediaOnly(true);
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactlyInAnyOrder(1L, 10L);
        request.setNewsletterOnly(true);
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
    }

    @Test
    void advancedMetadataCountsTheQualifiedWholeSetNotTheAppliedSelection() {
        var metadata = handler.getSearchMetadata();
        assertThat(metadata.repositoryCounts()).containsEntry("10", 5L).containsEntry("20", 1L);
        assertThat(metadata.licenseCounts()).containsEntry("CC_BY", 1L).containsEntry("CC_BY_NC", 4L);
        assertThat(metadata.accessModelCounts()).containsEntry("FREE_ARTICLES", 1L).containsEntry("PAID_ARTICLES", 4L);
        assertThat(metadata.frequencyCounts()).containsEntry("q", 1L).containsEntry("m", 4L);
        JournalListQuery request = request();
        request.setDoiOnly(true);
        handler.execute(criteriaMapper.toCriteria(request));
        assertThat(handler.getSearchMetadata()).isEqualTo(metadata);
    }

    @Test
    void allAndPartDoiAreAcceptedWithoutUsingThePrefixAndClosedAccessIsIndependent() {
        JournalDocument closed = document(9, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70);
        closed.setDoiType("ALL");
        closed.setDoi(null);
        closed.setPaidArticle("ALL_ARTICLES_PAID");
        template.save(closed, index);
        template.indexOps(index).refresh();
        JournalListQuery request = request();
        request.setDoiOnly(true);
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactlyInAnyOrder(1L, 9L);
        request.setAccessModels(List.of(com.osatum.wbn.domain.model.journal.PaidArticleType.ALL_ARTICLES_PAID));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(9L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ŚWIAT NAUKI", "nauki swiat", "wia auk", "LODZKICH ŚWIAT", "ÓDZK BADAŃ", "Świat"})
    void titleSearchRequiresAllLiteralFragmentsInAnyOrderIgnoringCaseAndAccents(String title) {
        saveTextDocuments();
        JournalListQuery request = request();
        request.setJournalTitle(title);
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Świat Missing", "Nauka", "*", "?", "Świat OR Missing"})
    void titleSearchDoesNotUseFuzzinessOrInterpretSearchOperators(String title) {
        saveTextDocuments();
        JournalListQuery request = request();
        request.setJournalTitle(title);
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
    }

    @Test
    void searchesAlternativeTitlesWithoutCombiningWordsFromDifferentTitles() {
        JournalDocument first = withTitles(document(1, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70),
                TitleCode.ORIGINAL, "Alpha", TitleCode.ENGLISH, "Beta", TitleCode.ALTERNATIVE, "Dawny Świat Badań");
        template.save(first, index);
        template.indexOps(index).refresh();
        JournalListQuery request = request();
        request.setJournalTitle("BADAN dawny");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
        request.setJournalTitle("Alpha Beta");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
    }

    @Test
    void escapedWildcardCharactersAreLiteralAndCannotBroadenTheTitleFilter() {
        JournalDocument first = withTitles(document(1, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70),
                TitleCode.ORIGINAL, "Alpha*Beta?Gamma\\Delta");
        template.save(first, index);
        template.indexOps(index).refresh();
        JournalListQuery request = request();
        request.setJournalTitle("Beta?Gamma\\D");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
        request.setJournalTitle("Alpha*Missing");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
    }

    @Test
    void publisherSearchIncludesEveryPublisherButNotEditorsOrWordsFromDifferentInstitutions() {
        saveTextDocuments();
        JournalListQuery request = request();
        request.setPublisher("ŁÓDZK UNIW");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
        request.setPublisher("lodzk uniw");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
        request.setPublisher("Quantum");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
        request.setPublisher("Alpha lodzki");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
        request.setPublisher("Uniwersytat");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234-567X", "1234567x", "34 - 56 7 X", "\u00a01234\u2014567x\u00a0", "67X", "2345"})
    void issnSearchMatchesWholeOrInteriorFragmentsIgnoringSeparatorsAndCase(String issn) {
        saveTextDocuments();
        JournalListQuery request = request();
        request.setIssn(issn);
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"xyz", "1234-567Y", "1234*", "?", "---", "1234 OR 9876"})
    void invalidIssnTextDoesNotTurnIntoAnEmptyOrWildcardRestriction(String issn) {
        saveTextDocuments();
        JournalListQuery request = request();
        request.setIssn(issn);
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
    }

    @Test
    void eissnAndAllThreeTextCriteriaUseAndBeforePagination() {
        saveTextDocuments();
        JournalListQuery request = request();
        request.setIssn("8765");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(2L);
        request.setJournalTitle("Świat");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
        request.setIssn("2345");
        request.setPublisher("Alpha");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
        request.setPublisher("Other");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
    }

    @Test
    void textSearchDoesNotBorrowTitlesIssnsOrPublishersFromANewerSurvey() {
        saveTextDocuments();
        JournalDocument future = withTitles(document(1, 2025, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70),
                TitleCode.ORIGINAL, "Future journal");
        future.setIssns(List.of(new Issn(com.osatum.wbn.domain.model.journal.IssnType.P, "5555-6666")));
        future.setInstitutions(List.of(new Institution("P", "Future publisher")));
        template.save(future, index);
        template.indexOps(index).refresh();
        JournalListQuery request = request();
        request.setJournalTitle("Future");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
        request.setJournalTitle(null);
        request.setPublisher("Future");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
        request.setPublisher(null);
        request.setIssn("555566");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isZero();
        doReturn(2025).when(configuration).getCurrentRatingYear();
        request.setJournalTitle("Future");
        request.setPublisher("Future");
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactly(1L);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "SORT_BY_TITLE_ASC|13 14 12 17 16 15 11",
            "SORT_BY_TITLE_DESC|11 15 16 17 12 13 14",
            "SORT_BY_RATING_ICV_ASC|17 15 11 13 14 12 16",
            "SORT_BY_RATING_ICV_DESC|13 14 12 16 11 15 17",
            "SORT_BY_RATING_MNISW_ASC|14 16 11 13 17 15 12",
            "SORT_BY_RATING_MNISW_DESC|13 17 15 14 16 11 12",
            "SORT_BY_PUBLICATION_DAYS_ASC|14 13 11 12 17 16 15",
            "SORT_BY_PUBLICATION_DAYS_DESC|16 12 17 13 11 14 15"
    })
    void sortsTheWholeSetWithAlphabeticalTiesStableIdsAndCurrentYearRatings(JournalListQuery.SortBy sort, String expectedIds) {
        saveSortDocuments();
        List<Long> expected = Arrays.stream(expectedIds.split(" ")).map(Long::valueOf).toList();
        JournalListQuery request = request();
        request.setCountryCodes(List.of("sort"));
        request.setSortBy(sort);
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId).containsExactlyElementsOf(expected);
        request.setPage(1);
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).isEmpty();
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).totalElements()).isEqualTo(7);
    }

    @Test
    void titleSortingPreservesPolishCollationNumericOrderEdgeQuoteTrimmingAndScriptRank() {
        template.save(List.of(sortDocument(21, "„Łódź 10”", 80, 70, 10), sortDocument(22, "Łódź 2", 80, 70, 10),
                sortDocument(23, "Zebra", 80, 70, 10), sortDocument(24, "Żaba", 80, 70, 10),
                sortDocument(25, "Сравнение", 80, 70, 10), sortDocument(26, "42 Science", 80, 70, 10)), index);
        template.indexOps(index).refresh();
        JournalListQuery request = request();
        request.setCountryCodes(List.of("sort"));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId)
                .containsExactly(26L, 22L, 21L, 23L, 24L, 25L);
        request.setSortBy(JournalListQuery.SortBy.SORT_BY_TITLE_DESC);
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId)
                .containsExactly(24L, 23L, 21L, 22L, 26L, 25L);
    }

    @Test
    void missingOrDefaultSortUsesTitleAscendingAndDoesNotOrderByTextRelevance() {
        saveSortDocuments();
        JournalListQuery request = request();
        request.setCountryCodes(List.of("sort"));
        assertThat(handler.execute(criteriaMapper.toCriteria(request)).data()).extracting(JournalListView::getId)
                .containsExactly(13L, 14L, 12L, 17L, 16L, 15L, 11L);
        assertThat(new JournalListQuery().getSortBy()).isEqualTo(JournalListQuery.SortBy.SORT_BY_TITLE_ASC);
    }

    private void saveTextDocuments() {
        JournalDocument first = withTitles(document(1, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pl", 70),
                TitleCode.ORIGINAL, "Świat Nauki i Łódzkich Badań");
        first.setIssns(List.of(new Issn(com.osatum.wbn.domain.model.journal.IssnType.P, "1234-567X")));
        first.setInstitutions(List.of(new Institution("P", "Alpha Press"), new Institution("P", "Uniwersytet Łódzki"),
                new Institution("E", "Quantum Editorial")));
        JournalDocument second = withTitles(document(2, 2024, EntityState.ACTIVE, 90, RatingStatus.INDEXED, "de", 140),
                TitleCode.ORIGINAL, "Other Science");
        second.setIssns(List.of(new Issn(com.osatum.wbn.domain.model.journal.IssnType.E, "9876-5432")));
        second.setInstitutions(List.of(new Institution("P", "Other Press"), new Institution("E", "Uniwersytet Łódzki")));
        template.save(List.of(first, second), index);
        template.indexOps(index).refresh();
    }

    private void saveSortDocuments() {
        template.save(List.of(sortDocument(11, "Zulu", 80, 70, 10), sortDocument(12, "Beta", 90, null, 20),
                sortDocument(13, "Alpha", 90, 140, 10), sortDocument(14, "Alpha", 90, 70, 0),
                sortDocument(15, "Gamma", 70, 140, null),
                withTitles(sortDocument(16, "Unused", 90, 70, 30), TitleCode.ENGLISH, "Delta"),
                withTitles(sortDocument(17, "Unused", 60, 140, 20), TitleCode.SHORT, "Charlie")), index);
        template.indexOps(index).refresh();
    }

    private JournalDocument sortDocument(long id, String title, double icv, Integer mnisw, Integer days) {
        JournalDocument document = withTitles(document(id, 2024, EntityState.ACTIVE, icv, RatingStatus.INDEXED, "sort", 0),
                TitleCode.ORIGINAL, title, TitleCode.ENGLISH, id == 13 ? "Z alternative" : "A alternative");
        document.getRatings().removeIf(rating -> "MNW".equals(rating.getRatingCode()) && rating.getYear() == 2024);
        if (mnisw != null) {
            document.getRatings().add(rating("MNW", 2024, mnisw, null));
        }
        document.setPublicationDays(days);
        return document;
    }

    private JournalDocument withTitles(JournalDocument document, Object... codesAndTitles) {
        var journal = new com.osatum.wbn.domain.model.journal.Journal();
        journal.setTitles(new LinkedHashSet<>());
        for (int position = 0; position < codesAndTitles.length; position += 2) {
            var title = new com.osatum.wbn.domain.model.journal.Title();
            title.setCode((TitleCode) codesAndTitles[position]);
            title.setContent((String) codesAndTitles[position + 1]);
            journal.getTitles().add(title);
        }
        JournalDocument mapped = new JournalDocumentAssembler().toDto(new JournalDocumentParameters(journal, null));
        document.setTitles(mapped.getTitles());
        document.setTitleSortContentIcu(mapped.getTitleSortContentIcu());
        document.setTitleSortScriptRank(mapped.getTitleSortScriptRank());
        return document;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.osatum.wbn.domain.model.journal.FeeCategory.class)
    void noFeeFiltersAreIndependentAndMetadataCountsOnlyTheBaseSet(com.osatum.wbn.domain.model.journal.FeeCategory category) {
        var free = freeStandards();
        var paid = freeStandards();
        setFee(paid, category, fee(com.osatum.wbn.domain.model.survey.model.FeeType.ONE_FEE, amount(10, "PLN", "paid")));
        saveFeeDocuments(feeDocument(21, free), feeDocument(22, paid));
        JournalListQuery query = feeRequest();
        setNoFee(query, category);
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).data()).extracting(JournalListView::getId).containsExactly(21L);
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).data().getFirst().getFees().noFees()).isTrue();
        var before = handler.getSearchMetadata();
        assertThat(before.featureCounts()).containsEntry("otherFeesFreeOnly", category == com.osatum.wbn.domain.model.journal.FeeCategory.OTHER ? 1L : 2L);
        query.setCountryCodes(List.of("nonexistent"));
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isZero();
        assertThat(handler.getSearchMetadata()).isEqualTo(before);
        verifyNoInteractions(exchangeRates);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = com.osatum.wbn.domain.model.journal.FeeCategory.class,
            names = "OTHER", mode = org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE)
    void eachMoneyCategoryUsesTheSameExactInclusivePlnBoundary(com.osatum.wbn.domain.model.journal.FeeCategory category) {
        var standards = freeStandards();
        setFee(standards, category, fee(com.osatum.wbn.domain.model.survey.model.FeeType.ONE_FEE, amount(100, "USD", "range maximum")));
        saveFeeDocuments(feeDocument(21, standards));
        when(exchangeRates.snapshot()).thenReturn(feeRates("4.1234", "3"));
        var query = feeRequest();
        setMaximum(query, category, new java.math.BigDecimal("412.339999"));
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isZero();
        setMaximum(query, category, new java.math.BigDecimal("412.34"));
        var result = handler.execute(criteriaMapper.toCriteria(query));
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.data().getFirst().getFees().items()).singleElement().satisfies(item -> {
            assertThat(item.category()).isEqualTo(category);
            assertThat(item.amount()).isEqualByComparingTo("100");
            assertThat(item.currencyCode()).isEqualTo("USD");
        });
        verify(exchangeRates, times(2)).snapshot();
    }

    private void setMaximum(JournalListQuery query, com.osatum.wbn.domain.model.journal.FeeCategory category, java.math.BigDecimal limit) {
        switch (category) {
            case OPEN_ACCESS -> query.setOpenAccessFeeMaxPln(limit);
            case PUBLICATION -> query.setPublicationFeeMaxPln(limit);
            case RAPID_REVIEW -> query.setRapidReviewFeeMaxPln(limit);
            case SUBMISSION -> query.setSubmissionFeeMaxPln(limit);
            case OTHER -> throw new IllegalArgumentException("Other APC has no amount filter");
        }
    }

    @Test
    void variousFeeRequiresEveryComparableVariantWithinTheLimitAndReturnsTheHighestUsingOneSnapshot() {
        var standards = freeStandards();
        standards.setPublicationFee(fee(com.osatum.wbn.domain.model.survey.model.FeeType.VARIOUS_FEE,
                amount(100, "USD", "first"), amount(200, "EUR", "second")));
        saveFeeDocuments(feeDocument(21, standards));
        when(exchangeRates.snapshot()).thenReturn(feeRates("4", "3"));
        JournalListQuery query = feeRequest();
        query.setPublicationFeeMaxPln(new java.math.BigDecimal("599.9999"));
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isZero();
        query.setPublicationFeeMaxPln(new java.math.BigDecimal("600"));
        var result = handler.execute(criteriaMapper.toCriteria(query));
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.data().getFirst().getFees().items()).singleElement().satisfies(value -> {
            assertThat(value.amount()).isEqualByComparingTo("200");
            assertThat(value.currencyCode()).isEqualTo("EUR");
        });
        verify(exchangeRates, times(2)).snapshot();
    }

    @Test
    void changingRatesChangesTheChosenVariousVariantAndNextResultsWithoutReindexing() {
        var standards = freeStandards();
        standards.setPublicationFee(fee(com.osatum.wbn.domain.model.survey.model.FeeType.VARIOUS_FEE,
                amount(100, "USD", "first"), amount(200, "EUR", "second")));
        saveFeeDocuments(feeDocument(21, standards));
        when(exchangeRates.snapshot()).thenReturn(feeRates("4", "3"), feeRates("7", "2"), feeRates("7", "2"));
        JournalListQuery query = feeRequest();
        query.setPublicationFeeMaxPln(new java.math.BigDecimal("650"));
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).data().getFirst().getFees().items().getFirst().currencyCode()).isEqualTo("EUR");
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isZero();
        query.setPublicationFeeMaxPln(null);
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).data().getFirst().getFees().items().getFirst().currencyCode()).isEqualTo("USD");
        verify(exchangeRates, times(3)).snapshot();
    }

    @Test
    void feeCategoriesUseAndAndAreAppliedBeforeCountAndPagination() {
        var paid = freeStandards();
        paid.setPublicationFee(fee(com.osatum.wbn.domain.model.survey.model.FeeType.ONE_FEE, amount(1000, "PLN", null)));
        var free = freeStandards();
        var submissionPaid = freeStandards();
        submissionPaid.setSubmissionFee(fee(com.osatum.wbn.domain.model.survey.model.FeeType.ONE_FEE, amount(1000, "PLN", null)));
        saveFeeDocuments(feeDocument(21, paid), feeDocument(22, free), feeDocument(23, submissionPaid));
        when(exchangeRates.snapshot()).thenReturn(feeRates("4", "3"));
        JournalListQuery query = feeRequest();
        query.setPublicationFeeMaxPln(new java.math.BigDecimal("100"));
        query.setSubmissionFeeFreeOnly(true);
        var result = handler.execute(criteriaMapper.toCriteria(query));
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.data()).extracting(JournalListView::getId).containsExactly(22L);
    }

    @Test
    void nonComparableVariousIsNotFreeAndOnlyFailsItsOwnActiveLimit() {
        var standards = freeStandards();
        standards.setPublicationFee(fee(com.osatum.wbn.domain.model.survey.model.FeeType.VARIOUS_FEE,
                amount(null, "PLN", "text only")));
        saveFeeDocuments(feeDocument(21, standards));
        JournalListQuery query = feeRequest();
        query.setSubmissionFeeFreeOnly(true);
        var result = handler.execute(criteriaMapper.toCriteria(query));
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.data().getFirst().getFees().noFees()).isFalse();
        assertThat(result.data().getFirst().getFees().items().getFirst().amount()).isNull();
        query.setPublicationFeeFreeOnly(true);
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isZero();
        query.setPublicationFeeFreeOnly(false);
        query.setPublicationFeeMaxPln(new java.math.BigDecimal("100"));
        when(exchangeRates.snapshot()).thenReturn(feeRates("4", "3"));
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"PL, 1500, PLN, 1500", "DE, 350, EUR, 1050"})
    void countrySpecificFeesUseJournalCountryForAllAmountFiltersAndSourceCurrencies(String country, int amount, String currency, int maximumPln) {
        for (var category : com.osatum.wbn.domain.model.journal.FeeCategory.values()) {
            if (category == com.osatum.wbn.domain.model.journal.FeeCategory.OTHER) continue;
            var standards = freeStandards();
            setFee(standards, category, fee(com.osatum.wbn.domain.model.survey.model.FeeType.COUNTRY_SPECIFIC_FEE,
                    amount(350, "EUR", "EXTERNAL_AUTHORS"), amount(1500, "PLN", "INTERNAL_AUTHORS")));
            var document = feeDocument(21, standards, country);
            document.setIssns(List.of(new Issn(com.osatum.wbn.domain.model.journal.IssnType.P, "2082-1867")));
            saveFeeDocuments(document);
            when(exchangeRates.snapshot()).thenReturn(feeRates("4", "3"));
            var query = request();
            query.setIssn("2082-1867");
            setMaximum(query, category, java.math.BigDecimal.valueOf(maximumPln).subtract(new java.math.BigDecimal("0.01")));
            assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isZero();
            setMaximum(query, category, java.math.BigDecimal.valueOf(maximumPln));
            var results = handler.execute(criteriaMapper.toCriteria(query));
            assertThat(results.totalElements()).isEqualTo(1);
            assertThat(results.data().getFirst().getFees().items()).singleElement().satisfies(item -> {
                assertThat(item.category()).isEqualTo(category);
                assertThat(item.amount()).isEqualByComparingTo(Integer.toString(amount));
                assertThat(item.currencyCode()).isEqualTo(currency);
            });
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.osatum.wbn.domain.model.journal.FeeCategory.class)
    void countrySpecificFreeFiltersUseOnlyTheSelectedVariantAndExcludeMissingCountry(com.osatum.wbn.domain.model.journal.FeeCategory category) {
        var standards = freeStandards();
        setFee(standards, category, fee(com.osatum.wbn.domain.model.survey.model.FeeType.COUNTRY_SPECIFIC_FEE,
                amount(0, "PLN", "INTERNAL_AUTHORS"), amount(350, "EUR", "EXTERNAL_AUTHORS")));
        var polish = feeDocument(21, standards, "PL");
        var foreign = feeDocument(22, standards, "DE");
        var unknown = feeDocument(23, standards, null);
        saveFeeDocuments(polish, foreign, unknown);
        var query = request();
        setNoFee(query, category);
        var results = handler.execute(criteriaMapper.toCriteria(query));
        assertThat(results.data()).extracting(JournalListView::getId).containsExactly(21L);
        assertThat(results.data().getFirst().getFees().noFees()).isTrue();
    }

    @Test
    void actaBalneologicaAndOneFeeControlMatchTheApprovedPublicationThresholds() {
        var standards = freeStandards();
        standards.setPublicationFee(fee(com.osatum.wbn.domain.model.survey.model.FeeType.COUNTRY_SPECIFIC_FEE,
                amount(1500, "PLN", "INTERNAL_AUTHORS"), amount(350, "EUR", "EXTERNAL_AUTHORS")));
        var acta = feeDocument(26423, standards, "PL");
        acta.setIssns(List.of(new Issn(com.osatum.wbn.domain.model.journal.IssnType.P, "2082-1867")));
        var controlStandards = freeStandards();
        controlStandards.setPublicationFee(fee(com.osatum.wbn.domain.model.survey.model.FeeType.ONE_FEE, amount(500, "PLN", "FOR_ALL")));
        var control = feeDocument(16529, controlStandards, "PL");
        control.setIssns(List.of(new Issn(com.osatum.wbn.domain.model.journal.IssnType.P, "1730-1912")));
        saveFeeDocuments(acta, control);
        when(exchangeRates.snapshot()).thenReturn(feeRates("4", "3"));
        var query = request();
        query.setIssn("2082-1867");
        query.setPublicationFeeMaxPln(java.math.BigDecimal.valueOf(1500));
        var results = handler.execute(criteriaMapper.toCriteria(query));
        assertThat(results.data()).extracting(JournalListView::getId).containsExactly(26423L);
        assertThat(results.data().getFirst().getFees().items().getFirst().amount()).isEqualByComparingTo("1500");
        assertThat(results.data().getFirst().getFees().items().getFirst().currencyCode()).isEqualTo("PLN");
        query.setPublicationFeeMaxPln(java.math.BigDecimal.valueOf(1499));
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isZero();
        query.setIssn("1730-1912");
        query.setPublicationFeeMaxPln(java.math.BigDecimal.valueOf(500));
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).data()).extracting(JournalListView::getId).containsExactly(16529L);
        query.setPublicationFeeMaxPln(java.math.BigDecimal.valueOf(400));
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isZero();
    }

    @Test
    void missingRateIsAnIntegrationFailureNotAnImplicitExclusionOrZero() {
        var standards = freeStandards();
        standards.setPublicationFee(fee(com.osatum.wbn.domain.model.survey.model.FeeType.ONE_FEE, amount(100, "USD", null)));
        saveFeeDocuments(feeDocument(21, standards));
        when(exchangeRates.snapshot()).thenReturn(com.osatum.wbn.application.journal.service.ExchangeRateSnapshot.plnOnly());
        JournalListQuery query = feeRequest();
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isEqualTo(1);
        verifyNoInteractions(exchangeRates);
        query.setPublicationFeeMaxPln(new java.math.BigDecimal("500"));
        assertThatThrownBy(() -> handler.execute(criteriaMapper.toCriteria(query))).isInstanceOf(com.osatum.wbn.application.journal.service.ExchangeRatesUnavailableException.class);
    }

    @Test
    void otherApcHasOnlyPresenceInTheResultAndZeroAmountsAreFree() {
        var standards = freeStandards();
        standards.setApc(fee(com.osatum.wbn.domain.model.survey.model.FeeType.VARIOUS_FEE,
                amount(999, "USD", "private details"), amount(100, "EUR", "another currency")));
        standards.setPublicationFee(fee(com.osatum.wbn.domain.model.survey.model.FeeType.ONE_FEE, amount(0, null, null)));
        saveFeeDocuments(feeDocument(21, standards));
        var result = handler.execute(criteriaMapper.toCriteria(feeRequest()));
        assertThat(result.data().getFirst().getFees().items()).singleElement().satisfies(item -> {
            assertThat(item.category()).isEqualTo(com.osatum.wbn.domain.model.journal.FeeCategory.OTHER);
            assertThat(item.amount()).isNull();
            assertThat(item.currencyCode()).isNull();
        });
        verifyNoInteractions(exchangeRates);
    }

    private JournalListQuery feeRequest() {
        var request = request();
        request.setCountryCodes(List.of("fees"));
        return request;
    }

    private JournalDocument feeDocument(long id, com.osatum.wbn.domain.model.survey.Standards standards) {
        return feeDocument(id, standards, "fees");
    }

    private JournalDocument feeDocument(long id, com.osatum.wbn.domain.model.survey.Standards standards, String country) {
        var document = document(id, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, country, 70);
        document.setFees(feePolicy.project(standards, document.getCountryCode()));
        return document;
    }

    private void saveFeeDocuments(JournalDocument... documents) {
        template.save(List.of(documents), index);
        template.indexOps(index).refresh();
    }

    private com.osatum.wbn.domain.model.survey.Standards freeStandards() {
        var standards = new com.osatum.wbn.domain.model.survey.Standards();
        for (var category : com.osatum.wbn.domain.model.journal.FeeCategory.values()) {
            setFee(standards, category, fee(com.osatum.wbn.domain.model.survey.model.FeeType.NO_FEE));
        }
        return standards;
    }

    private void setFee(com.osatum.wbn.domain.model.survey.Standards standards, com.osatum.wbn.domain.model.journal.FeeCategory category,
                        com.osatum.wbn.domain.model.survey.model.AuthorsFee fee) {
        switch (category) {
            case OTHER -> standards.setApc(fee);
            case OPEN_ACCESS -> standards.setOpenAccessFee(fee);
            case PUBLICATION -> standards.setPublicationFee(fee);
            case RAPID_REVIEW -> standards.setRapidReview(fee);
            case SUBMISSION -> standards.setSubmissionFee(fee);
        }
    }

    private void setNoFee(JournalListQuery query, com.osatum.wbn.domain.model.journal.FeeCategory category) {
        switch (category) {
            case OTHER -> query.setOtherFeesFreeOnly(true);
            case OPEN_ACCESS -> query.setOpenAccessFeeFreeOnly(true);
            case PUBLICATION -> query.setPublicationFeeFreeOnly(true);
            case RAPID_REVIEW -> query.setRapidReviewFeeFreeOnly(true);
            case SUBMISSION -> query.setSubmissionFeeFreeOnly(true);
        }
    }

    private com.osatum.wbn.domain.model.survey.model.AuthorsFee fee(com.osatum.wbn.domain.model.survey.model.FeeType type,
                                                                 com.osatum.wbn.domain.model.survey.model.FeeDetail... details) {
        var fee = new com.osatum.wbn.domain.model.survey.model.AuthorsFee();
        fee.setFeeType(type);
        fee.setFeeDetails(List.of(details));
        return fee;
    }

    private com.osatum.wbn.domain.model.survey.model.FeeDetail amount(Integer value, String currency, String description) {
        var detail = new com.osatum.wbn.domain.model.survey.model.FeeDetail();
        detail.setMinValue(0);
        detail.setMaxValue(value);
        detail.setCurrencyCode(currency);
        detail.setDescription(description);
        return detail;
    }

    private com.osatum.wbn.application.journal.service.ExchangeRateSnapshot feeRates(String usd, String eur) {
        var date = java.time.LocalDate.of(2026, 9, 18);
        return new com.osatum.wbn.application.journal.service.ExchangeRateSnapshot(java.util.Map.of(
                "USD", new com.osatum.wbn.domain.model.journal.ExchangeRate("USD", new java.math.BigDecimal(usd), "A", "test/A", date),
                "EUR", new com.osatum.wbn.domain.model.journal.ExchangeRate("EUR", new java.math.BigDecimal(eur), "A", "test/A", date)));
    }

    @Test
    void numberedPagesContainTenTenAndSevenFilteredResultsAndReflectUpdates() {
        var free = feePolicy.project(freeStandards(), "pages");
        var documents = new ArrayList<JournalDocument>();
        for (int position = 0; position < 27; position++) {
            JournalDocument document = document(200 + position, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pages", 70);
            document.setFees(free);
            documents.add(document);
        }
        documents.add(document(300, 2025, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pages", 70));
        documents.add(document(301, 2024, EntityState.INACTIVE, 80, RatingStatus.INDEXED, "pages", 70));
        documents.add(document(302, 2024, EntityState.ACTIVE, 80, RatingStatus.INDEXED, "pages", 70));
        template.save(documents, index);
        template.indexOps(index).refresh();
        JournalListQuery query = request();
        query.setCountryCodes(List.of("pages"));
        query.setPublicationFeeFreeOnly(true);
        for (int page : new int[]{2, 0, 1}) {
            query.setPage(page);
            var result = handler.execute(criteriaMapper.toCriteria(query));
            assertThat(result.totalElements()).isEqualTo(27);
            assertThat(result.totalPages()).isEqualTo(3);
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.data()).extracting(JournalListView::getId)
                    .containsExactlyElementsOf(java.util.stream.LongStream.range(200 + page * 10, Math.min(227, 210 + page * 10)).boxed().toList());
        }
        query.setPage(3);
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).data()).isEmpty();
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isEqualTo(27);
        documents.getFirst().setState(EntityState.INACTIVE.name());
        template.save(documents.getFirst(), index);
        template.indexOps(index).refresh();
        query.setPage(2);
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isEqualTo(26);
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).data()).hasSize(6);
        verifyNoInteractions(exchangeRates);
    }

    @Test
    void exactCountAndRandomPagesBeyondTenThousandWorkForAllEightSortsWithoutStoredCursors() {
        for (int start = 0; start < 12037; start += 1000) {
            var batch = new ArrayList<JournalDocument>();
            for (int position = start; position < Math.min(start + 1000, 12037); position++) {
                JournalDocument document = new JournalDocument();
                document.setDocumentId((100000 + position) + ":2024");
                document.setId(100000L + position);
                document.setSurveyYear(2024);
                document.setState(EntityState.ACTIVE.name());
                document.setCountryCode("deep");
                document.setRatings(List.of(rating("ICV", 2024, 80, RatingStatus.INDEXED)));
                batch.add(document);
            }
            template.save(batch, index);
        }
        template.indexOps(index).refresh();
        JournalListQuery query = request();
        query.setCountryCodes(List.of("deep"));
        for (JournalListQuery.SortBy sort : JournalListQuery.SortBy.values()) {
            query.setSortBy(sort);
            query.setPage(1101);
            var result = handler.execute(criteriaMapper.toCriteria(query));
            assertThat(result.totalElements()).isEqualTo(12037);
            assertThat(result.totalPages()).isEqualTo(1204);
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.data()).extracting(JournalListView::getId)
                    .containsExactlyElementsOf(java.util.stream.LongStream.range(111010, 111020).boxed().toList());
        }
        for (int size : new int[]{25, 50, 100}) {
            query.setSize(size);
            for (int page : new int[]{0, 9999 / size, 10000 / size, 12036 / size}) {
                query.setPage(page);
                var result = handler.execute(criteriaMapper.toCriteria(query));
                assertThat(result.totalPages()).isEqualTo(Math.ceilDiv(12037, size));
                assertThat(result.size()).isEqualTo(size);
                assertThat(result.data()).extracting(JournalListView::getId).containsExactlyElementsOf(
                        java.util.stream.LongStream.range(100000L + (long) page * size,
                                100000L + Math.min(12037L, (long) (page + 1) * size)).boxed().toList());
            }
        }
        query.setSize(10);
        for (int page : new int[]{1203, 0, 999, 1000}) {
            query.setPage(page);
            var result = handler.execute(criteriaMapper.toCriteria(query));
            assertThat(result.totalElements()).isEqualTo(12037);
            assertThat(result.data()).extracting(JournalListView::getId)
                    .containsExactlyElementsOf(java.util.stream.LongStream.range(100000L + page * 10L, Math.min(112037L, 100010L + page * 10L)).boxed().toList());
        }
        query.setPage(Integer.MAX_VALUE);
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).data()).isEmpty();
        assertThat(handler.execute(criteriaMapper.toCriteria(query)).totalElements()).isEqualTo(12037);
        template.delete("100000:2024", index);
        template.indexOps(index).refresh();
        query.setPage(1000);
        var updated = handler.execute(criteriaMapper.toCriteria(query));
        assertThat(updated.totalElements()).isEqualTo(12036);
        assertThat(updated.data()).extracting(JournalListView::getId)
                .containsExactlyElementsOf(java.util.stream.LongStream.range(110001, 110011).boxed().toList());
        verifyNoInteractions(exchangeRates);
    }

    private JournalListQuery request() {
        JournalListQuery request = new JournalListQuery();
        request.setSize(10);
        request.setSortBy(null);
        return request;
    }

    private JournalDocument document(long id, int year, EntityState state, double icv, RatingStatus status,
                                     String country, double mnisw) {
        JournalDocument document = new JournalDocument();
        document.setDocumentId(id + ":" + year);
        document.setId(id);
        document.setSurveyYear(year);
        document.setState(state.name());
        document.setCountryCode(country);
        boolean advanced = id == 1 && year == 2024;
        document.setDoiType(advanced ? "PART" : "NONE");
        document.setDoi(advanced ? null : "10.9999");
        document.setEditorialTools(advanced ? List.of("EDITORIAL_SYSTEM", "NEWSLETTERS", "ANTI_PLAGIARISM_SYSTEM")
                : List.of("MANUSCRIPT_SYSTEM"));
        document.setSocialMediaUsed(advanced);
        document.setAop(advanced);
        document.setRepositoryIds(advanced ? List.of("10", "20", "10") : List.of("10"));
        document.setArticlePublishedLicense(advanced ? "CC_BY" : "CC_BY_NC");
        document.setPaidArticle(advanced ? "FREE_ARTICLES" : "PAID_ARTICLES");
        document.setFrequencyCode(advanced ? "q" : "m");
        document.setFirstIssueYear(advanced ? 1960 : 1970);
        document.setLanguages(List.of(new Language("en")));
        document.setScientificCategories(List.of(new ScientificCategory("3")));
        document.setRatings(new ArrayList<>(List.of(rating("ICV", 2024, icv, status),
                rating("ICV", 2025, 999, RatingStatus.INDEXED),
                rating("MNW", 2024, mnisw, null), rating("MNW", 2025, 999, null))));
        return document;
    }

    private Rating rating(String code, int year, double value, RatingStatus status) {
        Rating rating = new Rating();
        rating.setRatingCode(code);
        rating.setYear(year);
        rating.setValueNum(value);
        rating.setRatingStatus(status);
        return rating;
    }

    private SSLContext sslContext() throws Exception {
        String certificate = System.getProperty("p1.elasticsearch.ca");
        if (certificate == null) {
            return null;
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null);
        try (var stream = Files.newInputStream(Path.of(certificate))) {
            trustStore.setCertificateEntry("p1-test-ca", CertificateFactory.getInstance("X.509").generateCertificate(stream));
        }
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        return context;
    }
}
