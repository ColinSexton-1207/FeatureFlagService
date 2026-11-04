-- Projects exist only so flags and environments have a stable scope.
-- v1 seeds exactly one project; there is no project provisioning API.
CREATE TABLE projects (
    id          UUID         PRIMARY KEY,
    key         VARCHAR(64)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_projects_key UNIQUE (key)
);

CREATE TABLE environments (
    id          UUID         PRIMARY KEY,
    project_id  UUID         NOT NULL REFERENCES projects (id),
    key         VARCHAR(32)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    sort_order  SMALLINT     NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_environments_project_key UNIQUE (project_id, key)
);

-- Seed data lives in a versioned migration, so it runs exactly once per database.
INSERT INTO projects (id, key, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default Project');

INSERT INTO environments (id, project_id, key, name, sort_order) VALUES
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', 'dev',  'Development', 1),
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000001', 'qa',   'QA',          2),
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000001', 'uat',  'UAT',         3),
    ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000001', 'prod', 'Production',  4);
