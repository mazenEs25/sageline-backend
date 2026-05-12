-- ============================================================================
-- TEST-ONLY baseline schema.
-- This file lives in src/test/resources and is NEVER deployed to production.
-- It creates the minimal set of core tables that are normally managed by
-- Hibernate ddl-auto (not by any production Flyway migration), so that
-- LegacyMeasureMigrationIntegrationTest can run the V0.x → V1.x → V2.x
-- migration sequence on a freshly-cleaned Testcontainer database.
-- ============================================================================

CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    keycloak_id VARCHAR(255),
    username    VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS production_lines (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(64)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS validation_zones (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    poste_type         VARCHAR(64),
    order_in_line      INTEGER      NOT NULL DEFAULT 1,
    requires_tool_check BOOLEAN     NOT NULL DEFAULT false,
    production_line_id BIGINT       REFERENCES production_lines(id),
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS validations (
    id                 BIGSERIAL PRIMARY KEY,
    ticket_code        VARCHAR(64)  NOT NULL,
    status             VARCHAR(64)  NOT NULL,
    priority           VARCHAR(32),
    validation_zone_id BIGINT       REFERENCES validation_zones(id),
    production_line_id BIGINT       REFERENCES production_lines(id),
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS validation_results (
    id              BIGSERIAL PRIMARY KEY,
    parameter       VARCHAR(255),
    measured_value  DOUBLE PRECISION,
    expected_value  DOUBLE PRECISION,
    conform         BOOLEAN,
    validation_id   BIGINT REFERENCES validations(id),
    created_at      TIMESTAMP
);
