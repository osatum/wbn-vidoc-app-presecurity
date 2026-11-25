package com.osatum.wbn.domain.model.analytics;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "analytics_journal")
@Setter
@Getter
@NoArgsConstructor
public class JournalAnalytics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long journalId;
    @Column(name = "year_item")
    private Integer year;
    @Column(name = "double_value")
    private Double value;
    @Enumerated(EnumType.STRING)
    @Column(name = "analytics_type", length = 64)
    private JournalAnalyticsType type;
    @Column(name = "country_code", length = 20)
    private String countryCode;
}
