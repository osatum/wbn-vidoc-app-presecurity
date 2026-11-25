package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.domain.model.journal.ExchangeRate;
import com.osatum.wbn.domain.repository.journal.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.TreeMap;
import java.util.HashSet;

/**
 * Przechowuje ostatnie poprawnie pobrane kursy NBP używane do porównywania opłat w PLN.
 * Wyszukiwanie odczytuje lokalne dane; ten serwis nie wykonuje połączeń z API NBP.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRateService {
    private final ExchangeRateRepository repository;

    /**
     * Tworzy niemodyfikowalny zestaw kursów dla operacji wyszukiwania, odrzucając duplikaty i daty przyszłe.
     * Nie wymaga kursu z dzisiejszą datą, dzięki czemu weekendy korzystają z ostatnich dostępnych tabel.
     */
    @Transactional(readOnly = true)
    public ExchangeRateSnapshot snapshot() {
        var rates = new TreeMap<String, ExchangeRate>();
        List<ExchangeRate> stored;
        try {
            stored = repository.findAll();
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new ExchangeRatesUnavailableException("Stored NBP rates unavailable", exception);
        }
        for (ExchangeRate rate : stored) {
            if (rates.put(rate.currency(), rate) != null || rate.effectiveDate().isAfter(today())) {
                throw new ExchangeRatesUnavailableException("Invalid stored exchange rates");
            }
        }
        return new ExchangeRateSnapshot(rates);
    }

    /**
     * Zapisuje w jednej transakcji zweryfikowaną paczkę zawierającą tabele A i B.
     * Niekompletna odpowiedź nie zastępuje dotychczasowego zestawu kursów.
     */
    @Transactional
    public void update(List<ExchangeRate> rates) {
        var currencies = new HashSet<String>();
        if (rates.isEmpty() || rates.stream().anyMatch(rate -> !currencies.add(rate.currency()) || rate.effectiveDate().isAfter(today()))
                || rates.stream().noneMatch(rate -> "A".equals(rate.tableType()))
                || rates.stream().noneMatch(rate -> "B".equals(rate.tableType()))) {
            throw new IllegalArgumentException("Invalid or incomplete NBP tables");
        }
        repository.saveAll(rates);
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Europe/Warsaw"));
    }
}
