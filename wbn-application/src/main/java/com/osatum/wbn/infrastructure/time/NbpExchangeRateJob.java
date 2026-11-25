package com.osatum.wbn.infrastructure.time;

import com.osatum.wbn.application.journal.service.ExchangeRateService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.osatum.wbn.domain.model.journal.ExchangeRate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Odświeża lokalne kursy na podstawie ostatnich dostępnych tabel A i B NBP.
 * Działa według harmonogramu i konfiguracji, niezależnie od importu ankiet oraz wywołań wyszukiwarki.
 */
@Component
@Slf4j
public class NbpExchangeRateJob {
    private final ExchangeRateService service;
    private final RestClient client;
    private final boolean enabled;
    private final int attempts;

    @Autowired
    public NbpExchangeRateJob(ExchangeRateService service,
                             @Value("${app.exchange-rates.enabled:false}") boolean enabled,
                             @Value("${app.exchange-rates.base-url:https://api.nbp.pl/api/}") URI baseUrl,
                             @Value("${app.exchange-rates.connect-timeout:5s}") Duration connectTimeout,
                             @Value("${app.exchange-rates.read-timeout:10s}") Duration readTimeout,
                             @Value("${app.exchange-rates.max-attempts:3}") int attempts) {
        this(service, enabled, createClient(baseUrl, connectTimeout, readTimeout), attempts);
    }

    public NbpExchangeRateJob(ExchangeRateService service, boolean enabled, RestClient client, int attempts) {
        if (attempts < 1 || attempts > 5) {
            throw new IllegalArgumentException("NBP max-attempts must be between 1 and 5");
        }
        this.service = service;
        this.enabled = enabled;
        this.client = client;
        this.attempts = attempts;
    }

    /**
     * Wymaga HTTPS, dodatnich limitów czasu i wyłącza przekierowania dla skonfigurowanego źródła kursów.
     */
    private static RestClient createClient(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
        if (!"https".equalsIgnoreCase(baseUrl.getScheme()) || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("NBP requires HTTPS and positive timeouts");
        }
        var http = HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl.toString()).requestFactory(factory).build();
    }

    /**
     * Zapisuje kursy dopiero po pobraniu obu tabel. Przy awarii ponawia całą próbę w ograniczonej liczbie,
     * a po niepowodzeniu pozostawia ostatnie poprawne dane zamiast zapisywać niepełną paczkę.
     */
    @Scheduled(initialDelayString = "${app.exchange-rates.initial-delay:10s}")
    @Scheduled(cron = "${app.exchange-rates.cron:0 15 15 * * *}", zone = "Europe/Warsaw")
    public void refresh() {
        if (!enabled) {
            return;
        }
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                var rates = new ArrayList<ExchangeRate>();
                rates.addAll(fetch("A"));
                rates.addAll(fetch("B"));
                service.update(rates);
                log.info("NBP exchange rates updated: {} currencies", rates.size());
                return;
            } catch (RuntimeException exception) {
                log.warn("NBP update failed on attempt {}/{}; keeping the previous rates ({})",
                        attempt, attempts, exception.getClass().getSimpleName());
            }
        }
    }

    private List<ExchangeRate> fetch(String type) {
        Table[] tables = client.get().uri("exchangerates/tables/{type}/", type).accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Table[].class);
        if (tables == null || tables.length != 1 || !type.equals(tables[0].table())
                || tables[0].rates() == null || tables[0].rates().isEmpty()) {
            throw new IllegalArgumentException("Invalid NBP table response");
        }
        Table table = tables[0];
        return table.rates().stream().map(rate -> new ExchangeRate(rate.code(), rate.mid(), type, table.no(), table.effectiveDate())).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Table(String table, String no, LocalDate effectiveDate, List<Rate> rates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rate(String code, BigDecimal mid) {
    }
}
