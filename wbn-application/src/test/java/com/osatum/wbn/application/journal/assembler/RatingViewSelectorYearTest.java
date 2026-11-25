package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.journal.Rating;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RatingViewSelectorYearTest {
    @Test
    void selectedHistoricalYearControlsStatusPointsAndHistoryWithoutChangingTheDefault() {
        var configuration = spy(new ConfigurationService());
        doReturn(2025).when(configuration).getCurrentRatingYear();
        var mapper = new RatingViewSelectorMapper() {};
        ReflectionTestUtils.setField(mapper, "configurationService", configuration);
        var historical = new Rating();
        historical.setRatingCode("ICV");
        historical.setYear(2024);
        historical.setRatingStatus(RatingStatus.INDEXED);
        historical.setValueNum(80.5);
        var current = new Rating();
        current.setRatingCode("ICV");
        current.setYear(2025);
        current.setRatingStatus(RatingStatus.NOT_INDEXED);
        var ratings = Set.of(historical, current);

        assertThat(mapper.currentStatus(ratings, 2024)).isEqualTo(RatingStatus.INDEXED);
        assertThat(mapper.currentIcvYear(ratings, 2024)).isEqualTo(2024);
        assertThat(mapper.currentIcvPoints(ratings, 2024)).isEqualTo(80.5);
        assertThat(mapper.icv(ratings, 2024)).extracting(value -> value.getYear()).containsExactly(2024);
        assertThat(mapper.currentStatus(ratings, null)).isEqualTo(RatingStatus.NOT_INDEXED);
        assertThat(mapper.currentIcvYear(ratings, null)).isEqualTo(2025);
    }
}
