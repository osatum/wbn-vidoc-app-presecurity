package com.osatum.wbn.infrastructure.time;

import com.osatum.wbn.application.journal.service.ExchangeRateService;
import com.osatum.wbn.domain.model.journal.ExchangeRate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NbpExchangeRateJobTest {
    private final ExchangeRateService service = mock(ExchangeRateService.class);
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.nbp.pl/api/");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void fetchesLatestTablesABNotTodayAndPreservesSourcePrecisionAndDates() {
        table("A", "USD", "4.1234");
        table("B", "THB", "0.112345");
        new NbpExchangeRateJob(service, true, builder.build(), 1).refresh();
        ArgumentCaptor<List<ExchangeRate>> captured = ArgumentCaptor.forClass(List.class);
        verify(service).update(captured.capture());
        assertThat(captured.getValue()).extracting(ExchangeRate::currency).containsExactly("USD", "THB");
        assertThat(captured.getValue().getFirst().mid()).isEqualByComparingTo("4.1234");
        server.verify();
    }

    @Test
    void retriesTheWholeBatchWithoutPersistingAPartialTable() {
        table("A", "USD", "4.1234");
        server.expect(requestTo("https://api.nbp.pl/api/exchangerates/tables/B/")).andRespond(withServerError());
        table("A", "USD", "4.2");
        table("B", "THB", "0.1");
        new NbpExchangeRateJob(service, true, builder.build(), 2).refresh();
        verify(service, times(1)).update(anyList());
        server.verify();
    }

    @Test
    void failureDoesNotEraseTheLastPersistedRatesAndDisabledJobNeverCallsNbp() {
        server.expect(requestTo("https://api.nbp.pl/api/exchangerates/tables/A/")).andRespond(withServerError());
        new NbpExchangeRateJob(service, true, builder.build(), 1).refresh();
        verifyNoInteractions(service);
        server.verify();
        new NbpExchangeRateJob(service, false, builder.build(), 1).refresh();
        verifyNoInteractions(service);
    }

    @Test
    void timeoutDoesNotWriteOrEraseRates() {
        server.expect(requestTo("https://api.nbp.pl/api/exchangerates/tables/A/"))
                .andRespond(request -> { throw new java.net.SocketTimeoutException("test timeout"); });
        new NbpExchangeRateJob(service, true, builder.build(), 1).refresh();
        verifyNoInteractions(service);
        server.verify();
    }

    @Test
    void springCreatesTheProductionJobAndRegistersBothBackgroundSchedulesWithoutCallingNbp() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(ExchangeRateService.class, () -> service)
                .withUserConfiguration(JobConfiguration.class)
                .withInitializer(context -> context.getBeanFactory().setConversionService(
                        org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
                .withPropertyValues("app.exchange-rates.enabled=false", "app.exchange-rates.initial-delay=24h")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var processor = context.getBean(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class);
                    assertThat(processor.getScheduledTasks()).hasSize(2);
                    context.getBean(NbpExchangeRateJob.class).refresh();
                    verifyNoInteractions(service);
                });
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.context.annotation.Import(NbpExchangeRateJob.class)
    @org.springframework.scheduling.annotation.EnableScheduling
    static class JobConfiguration {
    }

    @Test
    void rejectsNonHttpsAndUnboundedRetries() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NbpExchangeRateJob(service, true,
                URI.create("http://api.nbp.pl/api/"), Duration.ofSeconds(1), Duration.ofSeconds(1), 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new NbpExchangeRateJob(service, true, builder.build(), 10));
    }

    private void table(String type, String currency, String mid) {
        server.expect(requestTo("https://api.nbp.pl/api/exchangerates/tables/" + type + "/"))
                .andRespond(withSuccess("[{\"table\":\"" + type + "\",\"no\":\"test/" + type + "\",\"effectiveDate\":\"2026-09-18\","
                        + "\"rates\":[{\"currency\":\"test currency\",\"code\":\"" + currency + "\",\"mid\":" + mid + "}]}]", MediaType.APPLICATION_JSON));
    }
}
