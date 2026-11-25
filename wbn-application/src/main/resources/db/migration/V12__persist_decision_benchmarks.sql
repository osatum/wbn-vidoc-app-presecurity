-- Stare osie nie stanowią kompletnej generacji utrwalonych benchmarków.
UPDATE decision_analytics_state SET status = 'NOT_READY', ready_years = '', updated_at = CURRENT_TIMESTAMP;

CREATE TABLE decision_analytics_generation (
    import_id varchar(36) PRIMARY KEY,
    source_years varchar(256) NOT NULL,
    benchmark_years varchar(256) NOT NULL,
    source_revision bigint,
    created_at timestamp with time zone NOT NULL,
    prepared_at timestamp with time zone,
    published_at timestamp with time zone
);

CREATE TABLE decision_analytics_year (
    import_id varchar(36) NOT NULL REFERENCES decision_analytics_generation(import_id) ON DELETE CASCADE,
    survey_year integer NOT NULL CHECK (survey_year > 0),
    input_count integer NOT NULL CHECK (input_count >= 0),
    population_count integer NOT NULL CHECK (population_count >= 0),
    available_count integer NOT NULL CHECK (available_count >= 0),
    axis_count integer NOT NULL CHECK (axis_count >= 0),
    PRIMARY KEY (import_id, survey_year)
);

-- Wyłącznie projekcja potrzebna do obliczeń i kontroli; bez list osób i danych sesji.
CREATE TABLE decision_benchmark_input (
    import_id varchar(36) NOT NULL,
    survey_year integer NOT NULL,
    journal_id bigint NOT NULL CHECK (journal_id > 0),
    included boolean NOT NULL,
    input_json text NOT NULL,
    PRIMARY KEY (import_id, survey_year, journal_id),
    FOREIGN KEY (import_id, survey_year) REFERENCES decision_analytics_year(import_id, survey_year) ON DELETE CASCADE
);

CREATE TABLE decision_benchmark_result (
    import_id varchar(36) NOT NULL,
    survey_year integer NOT NULL,
    journal_id bigint NOT NULL,
    metric varchar(32) NOT NULL CHECK (metric IN (
        'ICV', 'INITIAL_ASSESSMENT', 'REVIEW', 'PUBLICATION', 'REJECTION_RATE',
        'FOREIGN_AUTHORS', 'FOREIGN_REVIEWERS', 'FOREIGN_COUNCIL',
        'SUBMISSION_FEE', 'PUBLICATION_FEE', 'OPEN_ACCESS_FEE', 'RAPID_REVIEW_FEE', 'OTHER_FEE')),
    group_id bigint,
    group_size integer NOT NULL CHECK (group_size >= 0),
    valid_count integer NOT NULL CHECK (valid_count >= 0),
    own_value double precision,
    unavailable_reason varchar(64),
    result_json text NOT NULL,
    PRIMARY KEY (import_id, survey_year, journal_id, metric),
    FOREIGN KEY (import_id, survey_year, journal_id)
        REFERENCES decision_benchmark_input(import_id, survey_year, journal_id) ON DELETE CASCADE
);
