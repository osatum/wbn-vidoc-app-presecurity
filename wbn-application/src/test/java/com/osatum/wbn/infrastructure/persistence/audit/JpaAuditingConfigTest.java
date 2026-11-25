package com.osatum.wbn.infrastructure.persistence.audit;

import com.osatum.wbn.application.security.CurrentActorProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JpaAuditingConfigTest {

    @Test
    void providesOffsetDateTimeCompatibleWithAuditedEntities() {
        var instant = Instant.parse("2026-07-15T16:11:47Z");
        var config = new JpaAuditingConfig(
                mock(CurrentActorProvider.class),
                Clock.fixed(instant, ZoneOffset.UTC)
        );

        assertThat(config.auditingDateTimeProvider().getNow())
                .contains(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
        assertThat(JpaAuditingConfig.class.getAnnotation(EnableJpaAuditing.class).dateTimeProviderRef())
                .isEqualTo("auditingDateTimeProvider");
    }
}
