package com.osatum.wbn.application.system.service;

import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Wspólna retencja analityki wyszukiwarki P1 i Karty P2, domyślnie 365 dni.
 * Zachowuje istniejącą konfigurację; nie usuwa pozostałego audytu nawigacji i pobierania plików.
 */
@Service
@Slf4j
public class JournalSearchAuditRetentionService {
    private final NavigationAuditEventRepository repository;
    private final Clock clock;
    private final int days;
    private final int batchSize;
    private final boolean enabled;

    public JournalSearchAuditRetentionService(NavigationAuditEventRepository repository, Clock clock,
            @Value("${app.navigation-audit.journal-search-retention.days:365}") int days,
            @Value("${app.navigation-audit.journal-search-retention.batch-size:5000}") int batchSize,
            @Value("${app.navigation-audit.journal-search-retention.enabled:true}") boolean enabled) {
        if (days < 1 || days > 3650 || batchSize < 1 || batchSize > 100000) {
            throw new IllegalArgumentException("Invalid journal analytics retention configuration.");
        }
        this.repository = repository;
        this.clock = clock;
        this.days = days;
        this.batchSize = batchSize;
        this.enabled = enabled;
    }

    /**
     * Usuwa jedną ograniczoną partię starszych zdarzeń na wywołanie, aby nie blokować dużej tabeli audytu.
     * Granicę czasu wyznacza zegar aplikacji; wyłączona retencja nie modyfikuje danych.
     */
    @Scheduled(cron = "${app.navigation-audit.journal-search-retention.cron:0 30 * * * *}", zone = "UTC")
    @Transactional
    public int purge() {
        if (!enabled) return 0;
        int deleted = repository.deleteJournalSearchEventsBefore(clock.instant().minus(Duration.ofDays(days)), batchSize);
        if (deleted > 0) log.info("Removed {} expired journal analytics events", deleted);
        return deleted;
    }
}
