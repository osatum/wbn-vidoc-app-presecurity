-- Zmiana kwalifikacji COUNTRY_SPECIFIC_FEE wymaga ponownego przygotowania wyników P2.
-- Nie usuwamy ankiet ani migawek kontrolnych; stare wyniki nie mogą być odczytywane jako aktualne.
UPDATE decision_analytics_state SET status = 'NOT_READY', ready_years = '', updated_at = CURRENT_TIMESTAMP;
