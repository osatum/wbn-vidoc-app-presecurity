ALTER TABLE institution ADD COLUMN operating_profile varchar(32);

ALTER TABLE institution ADD CONSTRAINT chk_institution_operating_profile
    CHECK (operating_profile IS NULL OR
           (institution_type = 'RESEARCH_UNIT' AND operating_profile IN ('BASIC', 'ADVANCED', 'BOTH')));
