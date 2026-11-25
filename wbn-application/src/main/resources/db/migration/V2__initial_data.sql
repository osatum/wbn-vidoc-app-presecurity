-- Initial reference data for a newly created PostgreSQL database.
-- Applied after V1__initial_schema.sql.


-- =========================================================
-- INSTITUTIONS
-- =========================================================
insert into institution (id,
                         name,
                         description,
                         contact_data,
                         institution_type,
                         auth_mode,
                         allow_registration,
                         allow_other_ip,
                         allow_other_email_domain,
                         auto_activate_new_accounts,
                         created_at,
                         state)
values (1, 'Index Copernicus', 'Centralny operator systemu dostępowego dla instytucji naukowych',
        'support@nsao.example', 'SYSTEM_OPERATOR', 'AUTH_REQUIRED', false, true, false, false, current_timestamp,
        'ACTIVE');



-- =========================================================
-- USERS
--
-- password for test users: password
-- bcrypt example hash:
--   $2a$10$7EqJtq98hPqEX7fNZaFWoOeP9E9Q6Z6e5c5a2YvYxJk1aVZJ1dC1W
-- =========================================================
insert into app_user (id,
                      email,
                      first_name,
                      last_name,
                      password_hash,
                      roles,
                      created_at,
                      state)
values (1, 'pawel.koncewicz@osatum.com', 'Paweł', 'Koncewicz',
        '$2a$10$te8ALtz9iCh/E3LkdUKHd.Pv5aHYFPVMBq4YUruahltttp93BxQ/W', 'ADMIN', current_timestamp, 'ACTIVE'),
       (2, 'malgorzata.lada@indexcopernicus.com', 'Małgorzata', 'Łada',
        '$2a$10$PbOIfwekfZzyMl81k5qXtuJLtrNZJi6p52L8kcm2RyzOoF8/..cpS', 'ADMIN', current_timestamp, 'ACTIVE'),
       (3, 'natalia.piechota@indexcopernicus.com', 'Natalia', 'Piechota',
        '$2a$10$PbOIfwekfZzyMl81k5qXtuJLtrNZJi6p52L8kcm2RyzOoF8/..cpS', 'ADMIN', current_timestamp, 'ACTIVE'),
       (4, 'katarzyna.ladosz@indexcopernicus.com', 'Katarzyna', 'Ładosz',
        '$2a$10$PbOIfwekfZzyMl81k5qXtuJLtrNZJi6p52L8kcm2RyzOoF8/..cpS', 'ADMIN', current_timestamp, 'ACTIVE');


-- =========================================================
-- INSTITUTION MEMBERSHIPS
-- =========================================================
insert into institution_membership (user_id,
                                    institution_id,
                                    role,
                                    login_allowed_from_any_ip,
                                    created_at,
                                    state)
values
    -- System operator institution
    (1, 1, 'INSTITUTION_ADMIN', true, current_timestamp, 'ACTIVE'),
    (2, 1, 'INSTITUTION_ADMIN', true, current_timestamp, 'ACTIVE'),
    (3, 1, 'INSTITUTION_ADMIN', true, current_timestamp, 'ACTIVE'),
    (4, 1, 'INSTITUTION_ADMIN', true, current_timestamp, 'ACTIVE');


-- =========================================================
-- INSTITUTION IP RULES
--
-- ip_from_long and ip_to_long are unsigned IPv4 numeric ranges.
-- =========================================================
insert into institution_ip_rule (institution_id,
                                 rule_type,
                                 rule_raw,
                                 ip_from_long,
                                 ip_to_long,
                                 priority,
                                 created_at,
                                 state)
values
    -- 1: SYSTEM OPERATOR
    (1, 'SINGLE_IP', '127.0.0.1', 2130706433, 2130706433, 1, current_timestamp, 'ACTIVE'),
    (1, 'CIDR', '10.0.1.0/24', 167772416, 167772671, 2, current_timestamp, 'ACTIVE'),
    (1, 'CIDR', '10.0.2.0/24', 167772672, 167772927, 3, current_timestamp, 'ACTIVE');


ALTER TABLE institution
    ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE app_user
    ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE institution_email_domain
    ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE institution_membership
    ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE institution_ip_rule
    ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE login_session
    ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE refresh_token
    ALTER COLUMN id RESTART WITH 1000;

-- Default e-mail templates migrated from the former V5–V7 migrations.
INSERT INTO email_template (name, email_type, language, subject, html_body, text_body, created_at, state)
VALUES ('Link aktywacyjny',
        'ACCOUNT_ACTIVATION_LINK',
        'pl',
        'Aktywacja konta WBN',
        '<p>Dzien dobry {{firstName}}</p><p>Aby aktywowac konto dla jednostki {{institutionName}}, otworz link:</p><p><a href="{{activationUrl}}">Aktywuj konto</a></p><p>Jezeli nie zakladales konta, zignoruj te wiadomosc.</p>',
        E'Dzien dobry {{firstName}}\n\nAby aktywowac konto dla jednostki {{institutionName}}, otworz link:\n{{activationUrl}}\n\nJezeli nie zakladales konta, zignoruj te wiadomosc.',
        CURRENT_TIMESTAMP,
        'ACTIVE'),
       ('Nowy link aktywacyjny',
        'ACTIVATION_LINK_REGENERATED',
        'pl',
        'Nowy link aktywacyjny WBN',
        '<p>Dzien dobry {{firstName}}</p><p>Poprzedni link aktywacyjny wygasl. Aby aktywowac konto dla jednostki {{institutionName}}, otworz nowy link:</p><p><a href="{{activationUrl}}">Aktywuj konto</a></p>',
        E'Dzien dobry {{firstName}}\n\nPoprzedni link aktywacyjny wygasl. Aby aktywowac konto dla jednostki {{institutionName}}, otworz nowy link:\n{{activationUrl}}',
        CURRENT_TIMESTAMP,
        'ACTIVE'),
       ('Konto aktywne',
        'ACCOUNT_ACTIVATED',
        'pl',
        'Konto WBN jest aktywne',
        '<p>Dzien dobry {{firstName}}</p><p>Twoje konto dla jednostki {{institutionName}} zostalo aktywowane.</p>',
        E'Dzien dobry {{firstName}}\n\nTwoje konto dla jednostki {{institutionName}} zostalo aktywowane.',
        CURRENT_TIMESTAMP,
        'ACTIVE'),
       ('Konto oczekuje na potwierdzenie',
        'ACCOUNT_WAITING_FOR_CONFIRMATION',
        'pl',
        'Konto WBN oczekuje na potwierdzenie',
        '<p>Dzien dobry {{firstName}}</p><p>Twoje konto dla jednostki {{institutionName}} zostalo aktywowane linkiem i oczekuje na potwierdzenie przez administratora jednostki.</p>',
        E'Dzien dobry {{firstName}}\n\nTwoje konto dla jednostki {{institutionName}} zostalo aktywowane linkiem i oczekuje na potwierdzenie przez administratora jednostki.',
        CURRENT_TIMESTAMP,
        'ACTIVE'),
       ('Reset hasla',
        'PASSWORD_RESET_LINK',
        'pl',
        'Reset hasla WBN',
        '<p>Dzien dobry {{firstName}}</p><p>Otrzymalismy prosbe o reset hasla dla konta {{email}}.</p><p><a href="{{passwordResetUrl}}">Ustaw nowe haslo</a></p><p>Link jest wazny przez 1 godzine. Jezeli nie zglaszales resetu hasla, zignoruj te wiadomosc.</p>',
        E'Dzien dobry {{firstName}}\n\nOtrzymalismy prosbe o reset hasla dla konta {{email}}.\nUstaw nowe haslo:\n{{passwordResetUrl}}\n\nLink jest wazny przez 1 godzine. Jezeli nie zglaszales resetu hasla, zignoruj te wiadomosc.',
        CURRENT_TIMESTAMP,
        'ACTIVE'),
       ('Potwierdzenie usuniecia danych',
        'USER_DATA_DELETION_CONFIRMATION',
        'pl',
        'Potwierdzenie usuniecia danych WBN',
        '<p>Dzien dobry {{firstName}}</p><p>Otrzymalismy prosbe o usuniecie danych konta {{email}} w jednostce {{institutionName}}.</p><p><a href="{{userDataDeletionUrl}}">Potwierdz usuniecie danych</a></p><p>Link jest wazny przez 1 godzine. Jezeli nie zglaszales usuniecia danych, zignoruj te wiadomosc.</p>',
        E'Dzien dobry {{firstName}}\n\nOtrzymalismy prosbe o usuniecie danych konta {{email}} w jednostce {{institutionName}}.\nPotwierdz usuniecie danych:\n{{userDataDeletionUrl}}\n\nLink jest wazny przez 1 godzine. Jezeli nie zglaszales usuniecia danych, zignoruj te wiadomosc.',
        CURRENT_TIMESTAMP,
        'ACTIVE');
