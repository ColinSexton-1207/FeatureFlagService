CREATE TABLE feature_flags (
    id           UUID          PRIMARY KEY,
    project_id   UUID          NOT NULL REFERENCES projects (id),
    key          VARCHAR(100)  NOT NULL,
    name         VARCHAR(200)  NOT NULL,
    description  VARCHAR(1000),
    flag_type    VARCHAR(20)   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    created_by   VARCHAR(100)  NOT NULL,
    archived_at  TIMESTAMPTZ,
    -- The database, not an application pre-check, is the final guard against duplicate keys.
    CONSTRAINT uq_feature_flags_project_key UNIQUE (project_id, key),
    CONSTRAINT ck_feature_flags_type CHECK (flag_type IN ('BOOLEAN'))
);
