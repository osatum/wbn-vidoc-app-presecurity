CREATE TABLE decision_analytics_state (
    id integer PRIMARY KEY CHECK (id = 1),
    status varchar(16) NOT NULL CHECK (status IN ('NOT_READY', 'BUILDING', 'READY', 'FAILED')),
    import_id varchar(36),
    ready_years varchar(256) NOT NULL DEFAULT '',
    source_revision bigint NOT NULL DEFAULT 0,
    updated_at timestamp with time zone NOT NULL,
    CHECK (status <> 'READY' OR import_id IS NOT NULL)
);

INSERT INTO decision_analytics_state(id, status, ready_years, updated_at)
VALUES (1, 'NOT_READY', '', CURRENT_TIMESTAMP);

CREATE TABLE decision_time_axis (
    survey_year integer NOT NULL CHECK (survey_year > 0),
    group_id bigint NOT NULL CHECK (group_id > 0),
    metric varchar(32) NOT NULL CHECK (metric IN ('INITIAL_ASSESSMENT', 'REVIEW', 'PUBLICATION')),
    minimum_days double precision NOT NULL CHECK (minimum_days >= 0),
    maximum_days double precision NOT NULL CHECK (maximum_days > minimum_days),
    import_id varchar(36) NOT NULL,
    PRIMARY KEY (survey_year, group_id, metric)
);

CREATE INDEX idx_questionnaire_decision_year ON questionnaire(questionnaire_year, journal_id, state);
CREATE INDEX idx_rating_decision_year ON journal_rating(rating_year, rating_code, journal_id, state);
