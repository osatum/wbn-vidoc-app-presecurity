package com.osatum.wbn.application.analytics.view;

import com.osatum.wbn.domain.model.analytics.JournalAnalyticsType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsContainerView {
    private JournalAnalyticsType type;
    private List<AnalyticsItemView> items;
    private Double min;
    private Double max;
}
