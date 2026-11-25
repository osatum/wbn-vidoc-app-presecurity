-- =========================================================
-- data.sql
-- Initial data for Spring Boot
--
-- Tables:
--   institution
--   institution_email_domain
--   app_user
--   institution_membership
--   institution_ip_rule
--
-- Notes:
--   1. This file assumes Hibernate/JPA creates the schema before data.sql.
--      Recommended property:
--          spring.jpa.defer-datasource-initialization=true
--
--   2. BaseEntity fields used in inserts:
--          id, created_at, state
--
--      Optional BaseEntity fields intentionally omitted:
--          edited_at, created_by, edited_by
--
--   3. Compatible with H2 and PostgreSQL syntax used by Spring Boot tests/dev.
-- =========================================================


-- =========================================================
-- INSTITUTIONS
-- =========================================================
insert into institution (
    id,
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
    state
) values
      (1,  'Index Copernicus', 'Centralny operator systemu dostępowego dla instytucji naukowych', 'support@nsao.example', 'SYSTEM_OPERATOR', 'AUTH_REQUIRED', false, false, false, false, current_timestamp, 'ACTIVE'),

      (2,  'Uniwersytet Warszawski', 'Największa uczelnia badawcza w Polsce', 'it@uw.edu.pl', 'RESEARCH_UNIT', 'AUTH_NOT_REQUIRED', true, false, false, true, current_timestamp, 'ACTIVE'),
      (3,  'Politechnika Warszawska', 'Wiodąca uczelnia techniczna w Polsce', 'admin@pw.edu.pl', 'RESEARCH_UNIT', 'AUTH_REQUIRED', true, false, false, true, current_timestamp, 'ACTIVE'),
      (4,  'Akademia Górniczo-Hutnicza', 'AGH University of Science and Technology', 'it@agh.edu.pl', 'RESEARCH_UNIT', 'AUTH_REQUIRED', true, false, false, true, current_timestamp, 'ACTIVE'),
      (5,  'Instytut Informatyki PAN', 'Badania podstawowe i stosowane w informatyce', 'office@iipan.pl', 'RESEARCH_UNIT', 'AUTH_REQUIRED', false, false, false, false, current_timestamp, 'ACTIVE'),
      (6,  'Instytut Fizyki PAN', 'Badania w zakresie fizyki teoretycznej i doświadczalnej', 'contact@ifpan.edu.pl', 'RESEARCH_UNIT', 'AUTH_REQUIRED', false, false, false, false, current_timestamp, 'ACTIVE'),
      (7,  'Narodowe Centrum Badań Jądrowych', 'Badania jądrowe i energetyka', 'admin@ncbj.gov.pl', 'RESEARCH_UNIT', 'AUTH_REQUIRED', false, false, false, false, current_timestamp, 'ACTIVE'),
      (8,  'CERN', 'European Organization for Nuclear Research', 'it.service@cern.ch', 'RESEARCH_UNIT', 'AUTH_REQUIRED', false, false, false, false, current_timestamp, 'ACTIVE'),
      (9,  'Max Planck Institute for Informatics', 'Badania w zakresie informatyki teoretycznej', 'contact@mpi-inf.mpg.de', 'RESEARCH_UNIT', 'AUTH_REQUIRED', false, false, false, false, current_timestamp, 'ACTIVE'),
      (10, 'Massachusetts Institute of Technology', 'MIT – research university in Cambridge', 'it@mit.edu', 'RESEARCH_UNIT', 'AUTH_REQUIRED', false, false, false, false, current_timestamp, 'ACTIVE'),

      (11, 'Operator systemu', 'Operator systemu', 'operator@indexcopernicus.com', 'SYSTEM_OPERATOR', 'AUTH_REQUIRED', false, false, false, false, current_timestamp, 'ACTIVE');


-- =========================================================
-- INSTITUTION EMAIL DOMAINS
-- =========================================================
insert into institution_email_domain (
    institution_id,
    domain,
    created_at,
    state
) values
      (2,  'uw.edu.pl', current_timestamp, 'ACTIVE'),
      (3,  'pw.edu.pl', current_timestamp, 'ACTIVE'),
      (4,  'agh.edu.pl', current_timestamp, 'ACTIVE'),
      (5,  'iipan.pl', current_timestamp, 'ACTIVE'),
      (6,  'ifpan.edu.pl', current_timestamp, 'ACTIVE'),
      (7,  'ncbj.gov.pl', current_timestamp, 'ACTIVE'),
      (8,  'cern.ch', current_timestamp, 'ACTIVE'),
      (9,  'mpg.de', current_timestamp, 'ACTIVE'),
      (10, 'mit.edu', current_timestamp, 'ACTIVE'),
      (11, 'indexcopernicus.com', current_timestamp, 'ACTIVE');


-- =========================================================
-- USERS
--
-- password for test users: password
-- bcrypt example hash:
--   $2a$10$7EqJtq98hPqEX7fNZaFWoOeP9E9Q6Z6e5c5a2YvYxJk1aVZJ1dC1W
-- =========================================================
insert into app_user (
    id,
    email,
    first_name,
    last_name,
    password_hash,
    roles,
    created_at,
    state
) values
      (1,  'operator@indexcopernicus.com', 'Marek', 'Kowalski', '$2a$10$Y0Bgr2B2LaAqSp9Xzko/wOmuM1IJMIwMEn5kJ5vhC6yFu6Ym.YlHG', 'ADMIN', current_timestamp, 'ACTIVE'),
      (2,  'admin@uw.edu.pl', 'Anna', 'Nowak', '$2a$10$7EqJtq98hPqEX7fNZaFWoOeP9E9Q6Z6e5c5a2YvYxJk1aVZJ1dC1W', 'USER', current_timestamp, 'ACTIVE'),
      (3,  'user@uw.edu.pl', 'Piotr', 'Wiśniewski', '$2a$10$7EqJtq98hPqEX7fNZaFWoOeP9E9Q6Z6e5c5a2YvYxJk1aVZJ1dC1W', 'USER', current_timestamp, 'ACTIVE'),
      (4,  'viewer@uw.edu.pl', 'Katarzyna', 'Wójcik', '$2a$10$7EqJtq98hPqEX7fNZaFWoOeP9E9Q6Z6e5c5a2YvYxJk1aVZJ1dC1W', 'USER', current_timestamp, 'ACTIVE'),
      (5,  'admin@pw.edu.pl', 'Tomasz', 'Kaczmarek', '$2a$10$7EqJtq98hPqEX7fNZaFWoOeP9E9Q6Z6e5c5a2YvYxJk1aVZJ1dC1W', 'USER', current_timestamp, 'ACTIVE'),
      (6,  'user@pw.edu.pl', 'Magdalena', 'Zielińska', '$2a$10$7EqJtq98hPqEX7fNZaFWoOeP9E9Q6Z6e5c5a2YvYxJk1aVZJ1dC1W', 'USER', current_timestamp, 'ACTIVE'),
      (7,  'admin@agh.edu.pl', 'Jakub', 'Szymański', '$2a$10$7EqJtq98hPqEX7fNZaFWoOeP9E9Q6Z6e5c5a2YvYxJk1aVZJ1dC1W', 'USER', current_timestamp, 'ACTIVE'),
      (8,  'user@ifpan.edu.pl', 'Agnieszka', 'Lewandowska', '$2a$10$7EqJtq98hPqEX7fNZaFWoOeP9E9Q6Z6e5c5a2YvYxJk1aVZJ1dC1W', 'USER', current_timestamp, 'ACTIVE'),
      (9,  'pending@cern.ch', 'Marcin', 'Dąbrowski', '$2a$10$7EqJtq98hPqEX7fNZaFWoOeP9E9Q6Z6e5c5a2YvYxJk1aVZJ1dC1W', 'USER', current_timestamp, 'ACTIVE'),
      (10, 'viewer@mit.edu', 'Joanna', 'Kamińska', '$2a$10$7EqJtq98hPqEX7fNZaFWoOeP9E9Q6Z6e5c5a2YvYxJk1aVZJ1dC1W', 'USER', current_timestamp, 'ACTIVE'),
      (11, 'operator1@indexcopernicus.com', 'Robert', 'Mazur', '$2a$10$Y0Bgr2B2LaAqSp9Xzko/wOmuM1IJMIwMEn5kJ5vhC6yFu6Ym.YlHG', 'ADMIN', current_timestamp, 'ACTIVE');


-- =========================================================
-- INSTITUTION MEMBERSHIPS
-- =========================================================
insert into institution_membership (
    user_id,
    institution_id,
    role,
    login_allowed_from_any_ip,
    created_at,
    state
) values
      -- System operator institution
      (1,  1,  'INSTITUTION_ADMIN', true, current_timestamp, 'ACTIVE'),
      (1,  1,  'INSTITUTION_USER', true, current_timestamp, 'ACTIVE'),
      (11, 1,  'INSTITUTION_ADMIN', true, current_timestamp, 'ACTIVE'),

      -- UW
      (2,  2,  'INSTITUTION_ADMIN', true, current_timestamp, 'ACTIVE'),
      (3,  2,  'INSTITUTION_USER', false, current_timestamp, 'ACTIVE'),
      (4,  2,  'INSTITUTION_VIEWER', false, current_timestamp, 'ACTIVE'),
      (11, 2,  'INSTITUTION_VIEWER', true, current_timestamp, 'ACTIVE'),

      -- PW
      (5,  3,  'INSTITUTION_ADMIN', true, current_timestamp, 'ACTIVE'),
      (6,  3,  'INSTITUTION_USER', false, current_timestamp, 'ACTIVE'),

      -- AGH
      (7,  4,  'INSTITUTION_ADMIN', true, current_timestamp, 'ACTIVE'),

      -- IF PAN
      (8,  6,  'INSTITUTION_USER', false, current_timestamp, 'ACTIVE'),

      -- CERN
      (9,  8,  'INSTITUTION_PENDING', false, current_timestamp, 'ACTIVE'),

      -- MIT
      (10, 10, 'INSTITUTION_VIEWER', false, current_timestamp, 'ACTIVE');


-- =========================================================
-- INSTITUTION IP RULES
--
-- ip_from_long and ip_to_long are unsigned IPv4 numeric ranges.
-- =========================================================
insert into institution_ip_rule (
    institution_id,
    rule_type,
    rule_raw,
    ip_from_long,
    ip_to_long,
    priority,
    created_at,
    state
) values
      -- 1: SYSTEM OPERATOR
      (1,  'SINGLE_IP', '127.0.0.1', 2130706433, 2130706433, 1, current_timestamp, 'ACTIVE'),
      (1,  'CIDR', '10.0.1.0/24', 167772416, 167772671, 2, current_timestamp, 'ACTIVE'),
      (1,  'CIDR', '10.0.2.0/24', 167772672, 167772927, 3, current_timestamp, 'ACTIVE'),

      -- 2: UW
      (2,  'SINGLE_IP', '127.0.0.1', 2130706433, 2130706433, 1, current_timestamp, 'ACTIVE'),
      (2,  'CIDR', '192.168.2.0/24', 3232236032, 3232236287, 2, current_timestamp, 'ACTIVE'),
      (2,  'CIDR', '192.168.3.0/24', 3232236288, 3232236543, 3, current_timestamp, 'ACTIVE'),

      -- 3: PW
      (3,  'SINGLE_IP', '127.0.0.2', 2130706434, 2130706434, 1, current_timestamp, 'ACTIVE'),
      (3,  'CIDR', '172.16.2.0/24', 2886729984, 2886730239, 2, current_timestamp, 'ACTIVE'),
      (3,  'CIDR', '172.16.3.0/24', 2886730240, 2886730495, 3, current_timestamp, 'ACTIVE'),

      -- 4: AGH
      (4,  'CIDR', '10.10.0.0/24', 168427520, 168427775, 1, current_timestamp, 'ACTIVE'),
      (4,  'CIDR', '10.10.1.0/24', 168427776, 168428031, 2, current_timestamp, 'ACTIVE'),
      (4,  'CIDR', '10.10.2.0/24', 168428032, 168428287, 3, current_timestamp, 'ACTIVE'),

      -- 5: IIPAN
      (5,  'CIDR', '192.168.10.0/24', 3232238080, 3232238335, 1, current_timestamp, 'ACTIVE'),
      (5,  'CIDR', '192.168.11.0/24', 3232238336, 3232238591, 2, current_timestamp, 'ACTIVE'),
      (5,  'CIDR', '192.168.12.0/24', 3232238592, 3232238847, 3, current_timestamp, 'ACTIVE'),

      -- 6: IF PAN
      (6,  'CIDR', '10.20.0.0/16', 169082880, 169148415, 1, current_timestamp, 'ACTIVE'),
      (6,  'CIDR', '10.21.0.0/16', 169148416, 169213951, 2, current_timestamp, 'ACTIVE'),
      (6,  'CIDR', '10.22.0.0/16', 169213952, 169279487, 3, current_timestamp, 'ACTIVE'),

      -- 7: NCBJ
      (7,  'CIDR', '172.16.5.0/24', 2886731008, 2886731263, 1, current_timestamp, 'ACTIVE'),
      (7,  'CIDR', '172.16.6.0/24', 2886731264, 2886731519, 2, current_timestamp, 'ACTIVE'),
      (7,  'CIDR', '172.16.7.0/24', 2886731520, 2886731775, 3, current_timestamp, 'ACTIVE'),

      -- 8: CERN
      (8,  'CIDR', '188.184.0.0/16', 3166175232, 3166240767, 1, current_timestamp, 'ACTIVE'),
      (8,  'CIDR', '188.185.0.0/16', 3166240768, 3166306303, 2, current_timestamp, 'ACTIVE'),
      (8,  'CIDR', '188.186.0.0/16', 3166306304, 3166371839, 3, current_timestamp, 'ACTIVE'),

      -- 9: MPI
      (9,  'CIDR', '141.84.0.0/16', 2371096576, 2371162111, 1, current_timestamp, 'ACTIVE'),
      (9,  'CIDR', '141.85.0.0/16', 2371162112, 2371227647, 2, current_timestamp, 'ACTIVE'),
      (9,  'CIDR', '141.86.0.0/16', 2371227648, 2371293183, 3, current_timestamp, 'ACTIVE'),

      -- 10: MIT
      (10, 'CIDR', '18.0.0.0/8', 301989888, 318767103, 1, current_timestamp, 'ACTIVE'),
      (10, 'CIDR', '18.128.0.0/9', 310378496, 318767103, 2, current_timestamp, 'ACTIVE'),
      (10, 'CIDR', '18.192.0.0/10', 314572800, 318767103, 3, current_timestamp, 'ACTIVE'),

      -- 11: SYSTEM OPERATOR
      (11, 'SINGLE_IP', '127.0.0.1', 2130706433, 2130706433, 1, current_timestamp, 'ACTIVE');


ALTER TABLE institution ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE app_user ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE institution_email_domain ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE institution_membership ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE institution_ip_rule ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE login_session ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE refresh_token ALTER COLUMN id RESTART WITH 1000;
