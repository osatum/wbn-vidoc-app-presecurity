package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.domain.model.journal.ExchangeRate;
import com.osatum.wbn.domain.repository.journal.ExchangeRateRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExchangeRateServiceTest {
    private final ExchangeRateRepository repository = mock(ExchangeRateRepository.class);
    private final ExchangeRateService service = new ExchangeRateService(repository);

    @Test
    void readsOneImmutableSnapshotIncludingPlnAndKeepsLastAvailableDatesOverAWeekend() {
        var rate = rate("USD", "4.1234", "A", LocalDate.of(2026, 9, 18));
        when(repository.findAll()).thenReturn(List.of(rate));
        var snapshot = service.snapshot();
        assertThat(snapshot.rates()).containsEntry("PLN", BigDecimal.ONE).containsEntry("USD", new BigDecimal("4.1234"));
        assertThat(snapshot.quotations().get("USD").effectiveDate()).isEqualTo(LocalDate.of(2026, 9, 18));
        when(repository.findAll()).thenReturn(List.of(rate("USD", "7", "A", LocalDate.of(2026, 9, 21))));
        assertThat(snapshot.rates().get("USD")).isEqualByComparingTo("4.1234");
        assertThat(service.snapshot().rates().get("USD")).isEqualByComparingTo("7");
        assertThatThrownBy(() -> snapshot.quotations().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.require("EUR")).isInstanceOf(ExchangeRatesUnavailableException.class);
        verify(repository, times(2)).findAll();
    }

    @Test
    void rejectsIncompleteDuplicateAndFutureTablesBeforeWriting() {
        var usd = rate("USD", "4", "A", LocalDate.of(2026, 9, 18));
        assertThatIllegalArgumentException().isThrownBy(() -> service.update(List.of(usd)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.update(List.of(usd, usd)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.update(List.of(usd, rate("THB", "0.1", "B", LocalDate.of(2999, 1, 1)))));
        verifyNoInteractions(repository);
    }

    @Test
    void writesOnlyAfterBothTablesAreValidated() {
        var rates = List.of(rate("USD", "4", "A", LocalDate.of(2026, 9, 18)), rate("THB", "0.1", "B", LocalDate.of(2026, 9, 16)));
        service.update(rates);
        verify(repository).saveAll(rates);
    }

    @Test
    void rejectsInvalidRatesInsteadOfUsingZeroOneOrRoundedValues() {
        for (String value : List.of("0", "-1", "0.0000000000001", "1000000000000")) {
            assertThatIllegalArgumentException().isThrownBy(() -> rate("USD", value, "A", LocalDate.of(2026, 9, 18)));
        }
        when(repository.findAll()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        assertThatThrownBy(service::snapshot).isInstanceOf(ExchangeRatesUnavailableException.class);
    }

    private ExchangeRate rate(String code, String value, String table, LocalDate date) {
        return new ExchangeRate(code, new BigDecimal(value), table, "test/" + table, date);
    }
}
