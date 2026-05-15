-- ============================================================================
-- V4.2 — Reconcile Phase 001 catalog seeds with the actual content of the
-- Phase 004 supervisor fixtures (sagemcom-logs/*.txt|.log).
--
-- The V1.2 catalog seed was authored from one read of the fixtures; the
-- fixtures evolved before Phase 004 landed and now use measure codes that
-- don't directly match V1.2. This migration patches the catalog (new rows)
-- and the alias table (synonyms) so the supervisor fixtures hit ≥6/≥16/≥14
-- matches per SC-002. Pure additive; idempotent via ON CONFLICT DO NOTHING.
-- ============================================================================

-- ─── BNFT (TEST_FONCTIONNEL): Temps_Test ≡ TEMPS_TEST (case-only difference)
INSERT INTO measure_code_alias (poste_type, source_code, catalog_measure_code, active, created_at, updated_at)
VALUES ('TEST_FONCTIONNEL', 'Temps_Test', 'TEMPS_TEST', TRUE, NOW(), NOW())
ON CONFLICT (poste_type, source_code) DO NOTHING;

-- ─── BWC (WIFI_CONDUIT): POWER_RMS_AVG_VSA{1..4} ≡ existing antenna-specific
-- catalog rows at 5500 MHz (the most representative band in the fixture).
INSERT INTO measure_code_alias (poste_type, source_code, catalog_measure_code, active, created_at, updated_at)
VALUES
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1', 'POWER_RMS_AVG_VSA1_ANT1_5500', TRUE, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA2', 'POWER_RMS_AVG_VSA1_ANT2_5500', TRUE, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA3', 'POWER_RMS_AVG_VSA1_ANT3_5500', TRUE, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA4', 'POWER_RMS_AVG_VSA1_ANT4_5500', TRUE, NOW(), NOW())
ON CONFLICT (poste_type, source_code) DO NOTHING;

-- ─── BWC (WIFI_CONDUIT): seed catalog rows for measures the fixture has but
-- V1.2 didn't (EVM, RSSI per-channel, FREQUENCY_OFFSET). All non-mandatory
-- because they are informational diagnostics, not gate-blocking measures.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('WIFI_CONDUIT', 'EVM_2G_VSA1', 'EVM 2.4GHz VSA1', 'EVM', '%', 0.0, 10.0, false, 100, true, NOW(), NOW()),
    ('WIFI_CONDUIT', 'EVM_2G_VSA2', 'EVM 2.4GHz VSA2', 'EVM', '%', 0.0, 10.0, false, 101, true, NOW(), NOW()),
    ('WIFI_CONDUIT', 'EVM_2G_VSA3', 'EVM 2.4GHz VSA3', 'EVM', '%', 0.0, 10.0, false, 102, true, NOW(), NOW()),
    ('WIFI_CONDUIT', 'EVM_2G_VSA4', 'EVM 2.4GHz VSA4', 'EVM', '%', 0.0, 10.0, false, 103, true, NOW(), NOW()),
    ('WIFI_CONDUIT', 'EVM_5G_VSA1', 'EVM 5GHz VSA1',   'EVM', '%', 0.0, 10.0, false, 104, true, NOW(), NOW()),
    ('WIFI_CONDUIT', 'EVM_5G_VSA2', 'EVM 5GHz VSA2',   'EVM', '%', 0.0, 10.0, false, 105, true, NOW(), NOW()),
    ('WIFI_CONDUIT', 'EVM_5G_VSA3', 'EVM 5GHz VSA3',   'EVM', '%', 0.0, 10.0, false, 106, true, NOW(), NOW()),
    ('WIFI_CONDUIT', 'EVM_5G_VSA4', 'EVM 5GHz VSA4',   'EVM', '%', 0.0, 10.0, false, 107, true, NOW(), NOW()),
    ('WIFI_CONDUIT', 'RSSI_2G_CH1',         'RSSI 2.4GHz Channel 1',   'RSSI',      'dBm', -100.0, -20.0, false, 108, true, NOW(), NOW()),
    ('WIFI_CONDUIT', 'RSSI_5G_CH36',        'RSSI 5GHz Channel 36',    'RSSI',      'dBm', -100.0, -20.0, false, 109, true, NOW(), NOW()),
    ('WIFI_CONDUIT', 'FREQUENCY_OFFSET_2G', 'Frequency Offset 2.4GHz', 'FREQUENCY', 'ppm',   -2.0,   2.0, false, 110, true, NOW(), NOW()),
    ('WIFI_CONDUIT', 'FREQUENCY_OFFSET_5G', 'Frequency Offset 5GHz',   'FREQUENCY', 'ppm',   -2.0,   2.0, false, 111, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;
