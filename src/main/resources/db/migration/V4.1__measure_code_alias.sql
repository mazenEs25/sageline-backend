-- Phase 004: Measure code alias mapping table

CREATE TABLE measure_code_alias (
    id BIGSERIAL PRIMARY KEY,
    poste_type VARCHAR(64) NOT NULL,
    source_code VARCHAR(64) NOT NULL,
    catalog_measure_code VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_measure_code_alias_poste_source UNIQUE (poste_type, source_code)
);

-- Seed BNFT aliases from Plan.md §9
INSERT INTO measure_code_alias (poste_type, source_code, catalog_measure_code, active, created_at, updated_at) VALUES
('TEST_FONCTIONNEL', 'MES_BNFT_PWR0_2G', 'PWR_2G_ANT0', TRUE, now(), now()),
('TEST_FONCTIONNEL', 'MES_BNFT_PWR1_2G', 'PWR_2G_ANT1', TRUE, now(), now()),
('TEST_FONCTIONNEL', 'MES_BNFT_PWR0_5G', 'PWR_5G_ANT0', TRUE, now(), now()),
('TEST_FONCTIONNEL', 'MES_BNFT_PWR1_5G', 'PWR_5G_ANT1', TRUE, now(), now()),
('TEST_FONCTIONNEL', 'MES_BNFT_PWR0_BT', 'PWR_BT_ANT0', TRUE, now(), now())
ON CONFLICT (poste_type, source_code) DO NOTHING;

-- TODO: Add BWC and BTF aliases during Phase 2 task execution (T044)
