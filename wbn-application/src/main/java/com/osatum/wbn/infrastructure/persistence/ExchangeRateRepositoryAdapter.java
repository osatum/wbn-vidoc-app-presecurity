package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.journal.ExchangeRate;
import com.osatum.wbn.domain.repository.journal.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ExchangeRateRepositoryAdapter implements ExchangeRateRepository {
    private final JdbcTemplate jdbc;

    @Override
    public List<ExchangeRate> findAll() {
        return jdbc.query("select currency, mid, table_type, table_number, effective_date from exchange_rate order by currency",
                (row, position) -> new ExchangeRate(row.getString("currency"), row.getBigDecimal("mid"),
                        row.getString("table_type"), row.getString("table_number"), row.getDate("effective_date").toLocalDate()));
    }

    @Override
    public void saveAll(List<ExchangeRate> rates) {
        jdbc.batchUpdate("""
                insert into exchange_rate(currency, mid, table_type, table_number, effective_date)
                values (?, ?, ?, ?, ?)
                on conflict(currency) do update set mid=excluded.mid, table_type=excluded.table_type,
                    table_number=excluded.table_number, effective_date=excluded.effective_date
                where excluded.effective_date >= exchange_rate.effective_date
                """, rates, rates.size(), (statement, rate) -> {
            statement.setString(1, rate.currency());
            statement.setBigDecimal(2, rate.mid());
            statement.setString(3, rate.tableType());
            statement.setString(4, rate.tableNumber());
            statement.setDate(5, Date.valueOf(rate.effectiveDate()));
        });
    }
}
