package com.osatum.wbn.infrastructure.persistence.audit;

import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.InstitutionActor;
import com.osatum.wbn.application.security.UserActor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(modifyOnCreate = false, dateTimeProviderRef = "auditingDateTimeProvider")
@RequiredArgsConstructor
public class JpaAuditingConfig {

    private static final String SYSTEM_ACTOR = "system";

    private final CurrentActorProvider currentActorProvider;
    private final Clock clock;

    @Bean
    DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(clock));
    }

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(currentActorProvider.findCurrentActor()
                .map(actor -> switch (actor) {
                    case UserActor userActor -> userActor.email() == null || userActor.email().isBlank()
                            ? "user:%d".formatted(userActor.userId())
                            : userActor.email();
                    case InstitutionActor institutionActor -> institutionActor.name() == null || institutionActor.name().isBlank()
                            ? "institution:%d".formatted(institutionActor.institutionId())
                            : institutionActor.name();
                })
                .orElse(SYSTEM_ACTOR));
    }
}
