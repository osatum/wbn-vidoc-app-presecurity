package com.osatum.wbn.application.analytics.view;

import com.osatum.wbn.application.journal.view.CountryView;
import com.osatum.wbn.domain.model.analytics.JournalAnalyticsType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AnalyticsCountryView {
    private JournalAnalyticsType type;
    private CountryView country;
    private Double value;
}
