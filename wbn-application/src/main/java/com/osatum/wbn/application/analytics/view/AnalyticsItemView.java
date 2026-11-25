package com.osatum.wbn.application.analytics.view;

import com.osatum.wbn.domain.model.analytics.JournalAnalyticsType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsItemView {
    private Integer year;
    private Double value;
    private JournalAnalyticsType type;
}
