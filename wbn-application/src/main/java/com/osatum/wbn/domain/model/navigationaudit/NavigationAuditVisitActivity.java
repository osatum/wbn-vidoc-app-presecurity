package com.osatum.wbn.domain.model.navigationaudit;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "navigation_audit_visit_activity")
@IdClass(NavigationAuditVisitActivity.ActivityId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NavigationAuditVisitActivity {
    @Id
    @Column(name = "login_session_id")
    private Long loginSessionId;

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityId implements Serializable {
        private Long loginSessionId;
        private UUID eventId;
    }
}
