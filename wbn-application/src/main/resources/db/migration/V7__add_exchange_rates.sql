CREATE TABLE exchange_rate (
    currency varchar(3) PRIMARY KEY CHECK (currency ~ '^[A-Z]{3}$' AND currency <> 'PLN'),
    mid numeric(24,12) NOT NULL CHECK (mid > 0),
    table_type varchar(1) NOT NULL CHECK (table_type IN ('A', 'B')),
    table_number varchar(64) NOT NULL CHECK (length(trim(table_number)) > 0),
    effective_date date NOT NULL
);
