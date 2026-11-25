package com.osatum.wbn.domain.repository.journal;

import com.osatum.wbn.domain.model.journal.ExchangeRate;
import java.util.List;

public interface ExchangeRateRepository {
    List<ExchangeRate> findAll();
    void saveAll(List<ExchangeRate> rates);
}
