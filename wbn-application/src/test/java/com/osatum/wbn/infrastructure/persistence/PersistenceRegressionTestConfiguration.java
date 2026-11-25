package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.infrastructure.persistence.audit.JpaAuditingConfig;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;

import static org.mockito.Mockito.mock;

/** Rzeczywista persystencja H2 bez uruchamiania HTTP, JWT, importów i zewnętrznych usług aplikacji. */
@TestConfiguration
@EnableAutoConfiguration
@EntityScan("com.osatum.wbn.domain.model")
@Import({DictionaryReferenceCleanupRepository.class, UserPersonalDataDeletionRepositoryAdapter.class, JpaAuditingConfig.class})
class PersistenceRegressionTestConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CurrentActorProvider currentActorProvider() {
        return mock(CurrentActorProvider.class);
    }
}
