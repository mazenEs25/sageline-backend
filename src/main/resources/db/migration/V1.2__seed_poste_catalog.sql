-- ============================================================================
-- V1.2__seed_poste_catalog.sql
-- Reference catalog seed derived from the three supervisor-provided logs:
--   - bnft-decoder-M393.txt           ⇒ TEST_FONCTIONNEL
--   - bwc-gateway-safran-wifi5g.log   ⇒ WIFI_CONDUIT
--   - btf-gateway-fb107-wifi7.log     ⇒ ACC
--
-- Idempotency:
--   - Active rows: protected by the partial unique index uk_poste_measure_catalog_active
--     and the ON CONFLICT clause (no-op on re-run while flagged active=true).
--   - Inactive rows (placeholder bounds, awaiting engineering review per Constitution
--     Principle I): inserted only WHERE NOT EXISTS so manual re-runs do not duplicate.
-- ============================================================================

-- ─── TEST_FONCTIONNEL (BNFT Decoder) ─────────────────────────────────────────
-- SOURCE: src/test/resources/fixtures/sagemcom-logs/bnft-decoder-M393.txt

INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('TEST_FONCTIONNEL', 'MES_BNFT_PWR0_2G', 'BNFT Power 2G Channel 0', 'POWER', 'dBm', -20.0, 10.0, true, 1, true, NOW(), NOW()),
    ('TEST_FONCTIONNEL', 'MES_BNFT_PWR1_2G', 'BNFT Power 2G Channel 1', 'POWER', 'dBm', -20.0, 10.0, true, 2, true, NOW(), NOW()),
    ('TEST_FONCTIONNEL', 'MES_BNFT_PWR0_5G', 'BNFT Power 5G Channel 0', 'POWER', 'dBm', -15.0, 15.0, true, 3, true, NOW(), NOW()),
    ('TEST_FONCTIONNEL', 'MES_BNFT_PWR1_5G', 'BNFT Power 5G Channel 1', 'POWER', 'dBm', -15.0, 15.0, true, 4, true, NOW(), NOW()),
    ('TEST_FONCTIONNEL', 'MES_BNFT_PWR0_BT', 'BNFT Power Bluetooth',    'POWER', 'dBm', -25.0,  5.0, true, 5, true, NOW(), NOW()),
    ('TEST_FONCTIONNEL', 'TEMPS_TEST',       'Test Duration',           'TIME',  's',    1.0, 60.0, true, 6, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;

-- ─── WIFI_CONDUIT (BWC WiFi Gateway) ─────────────────────────────────────────
-- SOURCE: src/test/resources/fixtures/sagemcom-logs/bwc-gateway-safran-wifi5g.log
-- The BWC log captures POWER_RMS_AVG_VSA1 across 4 antennas × 4 frequencies = 16
-- mandatory measures. Each row carries its antenna and frequency context columns.

INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     antenna, frequency_mhz,
     created_at, updated_at)
VALUES
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT1_5250', 'Power RMS Avg VSA1 ANT1 5250MHz', 'POWER', 'dBm', 13.5, 16.5, true,  1, true, 'ANT1', 5250, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT1_5500', 'Power RMS Avg VSA1 ANT1 5500MHz', 'POWER', 'dBm', 13.5, 16.5, true,  2, true, 'ANT1', 5500, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT1_5670', 'Power RMS Avg VSA1 ANT1 5670MHz', 'POWER', 'dBm', 13.5, 16.5, true,  3, true, 'ANT1', 5670, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT1_5755', 'Power RMS Avg VSA1 ANT1 5755MHz', 'POWER', 'dBm', 13.5, 16.5, true,  4, true, 'ANT1', 5755, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT2_5250', 'Power RMS Avg VSA1 ANT2 5250MHz', 'POWER', 'dBm', 13.5, 16.5, true,  5, true, 'ANT2', 5250, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT2_5500', 'Power RMS Avg VSA1 ANT2 5500MHz', 'POWER', 'dBm', 13.5, 16.5, true,  6, true, 'ANT2', 5500, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT2_5670', 'Power RMS Avg VSA1 ANT2 5670MHz', 'POWER', 'dBm', 13.5, 16.5, true,  7, true, 'ANT2', 5670, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT2_5755', 'Power RMS Avg VSA1 ANT2 5755MHz', 'POWER', 'dBm', 13.5, 16.5, true,  8, true, 'ANT2', 5755, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT3_5250', 'Power RMS Avg VSA1 ANT3 5250MHz', 'POWER', 'dBm', 13.5, 16.5, true,  9, true, 'ANT3', 5250, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT3_5500', 'Power RMS Avg VSA1 ANT3 5500MHz', 'POWER', 'dBm', 13.5, 16.5, true, 10, true, 'ANT3', 5500, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT3_5670', 'Power RMS Avg VSA1 ANT3 5670MHz', 'POWER', 'dBm', 13.5, 16.5, true, 11, true, 'ANT3', 5670, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT3_5755', 'Power RMS Avg VSA1 ANT3 5755MHz', 'POWER', 'dBm', 13.5, 16.5, true, 12, true, 'ANT3', 5755, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT4_5250', 'Power RMS Avg VSA1 ANT4 5250MHz', 'POWER', 'dBm', 13.5, 16.5, true, 13, true, 'ANT4', 5250, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT4_5500', 'Power RMS Avg VSA1 ANT4 5500MHz', 'POWER', 'dBm', 13.5, 16.5, true, 14, true, 'ANT4', 5500, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT4_5670', 'Power RMS Avg VSA1 ANT4 5670MHz', 'POWER', 'dBm', 13.5, 16.5, true, 15, true, 'ANT4', 5670, NOW(), NOW()),
    ('WIFI_CONDUIT', 'POWER_RMS_AVG_VSA1_ANT4_5755', 'Power RMS Avg VSA1 ANT4 5755MHz', 'POWER', 'dBm', 13.5, 16.5, true, 16, true, 'ANT4', 5755, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;

-- Inactive seed rows (Q3 contingency: log shows code but no explicit [min, max]).
-- Placeholder bounds; awaiting engineering review before activation.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
SELECT 'WIFI_CONDUIT', 'PER_AGGREGATE',     'Packet Error Rate (aggregate)',     'PER',  '%',   0.0, 10.0, false, 17, false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM poste_measure_catalog WHERE poste_type = 'WIFI_CONDUIT' AND measure_code = 'PER_AGGREGATE');
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
SELECT 'WIFI_CONDUIT', 'RSSI_AGGREGATE',    'RSSI (aggregate)',                   'RSSI', 'dBm', -90.0, -30.0, false, 18, false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM poste_measure_catalog WHERE poste_type = 'WIFI_CONDUIT' AND measure_code = 'RSSI_AGGREGATE');

-- ─── ACC (BTF Voice Gateway) ─────────────────────────────────────────────────
-- SOURCE: src/test/resources/fixtures/sagemcom-logs/btf-gateway-fb107-wifi7.log

INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('ACC', 'M_FXS_TRANS_FXS1_1000HZ', 'FXS Transmission FXS1 1kHz',  'POWER',       'dBm', -10.0, 10.0, true,  1, true, NOW(), NOW()),
    ('ACC', 'M_FXS_TRANS_FXS2_1000HZ', 'FXS Transmission FXS2 1kHz',  'POWER',       'dBm', -10.0, 10.0, true,  2, true, NOW(), NOW()),
    ('ACC', 'M_FXS_REC_FXS1_1000HZ',   'FXS Reception FXS1 1kHz',     'POWER',       'dBm', -15.0,  0.0, true,  3, true, NOW(), NOW()),
    ('ACC', 'M_FXS_REC_FXS2_1000HZ',   'FXS Reception FXS2 1kHz',     'POWER',       'dBm', -15.0,  0.0, true,  4, true, NOW(), NOW()),
    ('ACC', 'THD_VOICE_PATH',          'THD Voice Path',              'PER',         '%',     0.0,  5.0, true,  5, true, NOW(), NOW()),
    ('ACC', 'SNR_VOICE_UPLINK',        'SNR Voice Uplink',            'POWER',       'dB',   10.0, 40.0, true,  6, true, NOW(), NOW()),
    ('ACC', 'SNR_VOICE_DOWNLINK',      'SNR Voice Downlink',          'POWER',       'dB',   10.0, 40.0, true,  7, true, NOW(), NOW()),
    ('ACC', 'DELAY_VOICE',             'Voice Delay',                 'TIME',        'ms',    0.0, 150.0, true, 8, true, NOW(), NOW()),
    ('ACC', 'CODEC_LATENCY_G711',      'Codec Latency G711',          'TIME',        'ms',    0.0, 50.0, true,  9, true, NOW(), NOW()),
    ('ACC', 'WIFI7_THROUGHPUT',        'WiFi7 Throughput',            'OTHER',       'Mbps', 100.0, 5000.0, true, 10, true, NOW(), NOW()),
    ('ACC', 'WIFI7_LATENCY',           'WiFi7 Latency',               'TIME',        'ms',    0.0, 50.0, true, 11, true, NOW(), NOW()),
    ('ACC', 'WIFI7_RSSI',              'WiFi7 RSSI',                  'RSSI',        'dBm', -80.0, -30.0, true, 12, true, NOW(), NOW()),
    ('ACC', 'TEMP_CPU',                'CPU Temperature',             'TEMPERATURE', '°C',    0.0, 85.0, true, 13, true, NOW(), NOW()),
    ('ACC', 'CONSUMPTION_TOTAL',       'Total Consumption',           'CURRENT',     'mA',    0.0, 2000.0, true, 14, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;

-- Inactive seed rows for ACC (no explicit bounds in source log).
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
SELECT 'ACC', 'JITTER_VOICE',     'Voice Jitter',         'TIME',        'ms', 0.0, 20.0, false, 15, false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM poste_measure_catalog WHERE poste_type = 'ACC' AND measure_code = 'JITTER_VOICE');
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
SELECT 'ACC', 'TEMP_RF_PA',       'RF PA Temperature',    'TEMPERATURE', '°C', 0.0, 95.0, false, 16, false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM poste_measure_catalog WHERE poste_type = 'ACC' AND measure_code = 'TEMP_RF_PA');
