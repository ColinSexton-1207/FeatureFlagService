CREATE TABLE flag_configurations (
    id                  UUID         PRIMARY KEY,
    flag_id             UUID         NOT NULL REFERENCES feature_flags (id),
    environment_id      UUID         NOT NULL REFERENCES environments (id),
    enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    rollout_percentage  SMALLINT     NOT NULL DEFAULT 0,
    allow_users         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    deny_users          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    version             BIGINT       NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(100) NOT NULL,
    CONSTRAINT uq_flag_configurations_flag_env UNIQUE (flag_id, environment_id),
    CONSTRAINT ck_flag_configurations_rollout CHECK (rollout_percentage BETWEEN 0 AND 100),
    CONSTRAINT ck_flag_configurations_allow_array CHECK (jsonb_typeof(allow_users) = 'array'),
    CONSTRAINT ck_flag_configurations_deny_array CHECK (jsonb_typeof(deny_users) = 'array')
);
