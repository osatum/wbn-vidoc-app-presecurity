CREATE TABLE navigation_audit_visit (
    id bigserial PRIMARY KEY,
    login_session_id bigint REFERENCES login_session(id) ON DELETE SET NULL,
    actor_type varchar(32) NOT NULL,
    user_id bigint REFERENCES app_user(id),
    institution_id bigint REFERENCES institution(id),
    started_at timestamptz NOT NULL,
    last_activity_at timestamptz NOT NULL,
    ended_at timestamptz,
    end_reason varchar(32),
    created_at timestamptz,
    edited_at timestamptz,
    created_by varchar(255),
    edited_by varchar(255),
    state varchar(255) NOT NULL DEFAULT 'ACTIVE',
    CHECK (last_activity_at >= started_at),
    CHECK (ended_at IS NULL OR ended_at = last_activity_at)
);

CREATE UNIQUE INDEX idx_navigation_audit_visit_open
    ON navigation_audit_visit(login_session_id) WHERE ended_at IS NULL AND login_session_id IS NOT NULL;
CREATE INDEX idx_navigation_audit_visit_started ON navigation_audit_visit(started_at, id);
CREATE INDEX idx_navigation_audit_visit_idle ON navigation_audit_visit(last_activity_at) WHERE ended_at IS NULL;
CREATE INDEX idx_navigation_audit_visit_user ON navigation_audit_visit(user_id);
CREATE INDEX idx_navigation_audit_visit_institution ON navigation_audit_visit(institution_id);

CREATE TABLE navigation_audit_visit_activity (
    login_session_id bigint NOT NULL REFERENCES login_session(id) ON DELETE CASCADE,
    event_id uuid NOT NULL,
    received_at timestamptz NOT NULL,
    PRIMARY KEY (login_session_id, event_id)
);
CREATE INDEX idx_navigation_audit_visit_activity_received ON navigation_audit_visit_activity(received_at);
