-- ============================================================================
-- V5.1__seed_remaining_poste_catalogs.sql
-- Seed PosteMeasureCatalog entries for every PosteType that was not covered
-- by V1.2 (which only seeded ACC, TEST_FONCTIONNEL, WIFI_CONDUIT).
--
-- Grouped by production area:
--   BBS CMS ........... WIFI_RY, BANC_RX_TX, BANC_SENSI, BANC_TT, BANC_TX
--   BBS Intégration ... AQ_LIGNE, TELECHARGEMENT, BANC_NFT, BANC_NFT_BOUTON,
--                       TEST_BOUTON, TEST_VISION, TEST_DOCSIS, TEST_SYNCHRO_GPON
--   AVS CMS ........... BANC_AUDIO_VIDEO, BANC_WIFI_CONDUIT
--   AVS Intégration ... BPO, FSOS, BANC_ETANCHEITE, BANC_ACOUSTIQUE
--
-- Idempotency: ON CONFLICT on the partial unique index (poste_type, measure_code)
-- WHERE active = true → no-op on re-run.
-- ============================================================================


-- ═══════════════════════════════════════════════════════════════════════════
-- BBS CMS
-- ═══════════════════════════════════════════════════════════════════════════

-- ─── WIFI_RY ─────────────────────────────────────────────────────────────
-- WiFi RY (Rendement/Yield) – tests WiFi radio yield on 2.4 GHz & 5 GHz bands.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     antenna, frequency_mhz, created_at, updated_at)
VALUES
    ('WIFI_RY', 'PWR_RY_ANT1_2400',  'Power RY ANT1 2400MHz',  'POWER', 'dBm', 10.0, 18.0, true, 1, true, 'ANT1', 2400, NOW(), NOW()),
    ('WIFI_RY', 'PWR_RY_ANT2_2400',  'Power RY ANT2 2400MHz',  'POWER', 'dBm', 10.0, 18.0, true, 2, true, 'ANT2', 2400, NOW(), NOW()),
    ('WIFI_RY', 'PWR_RY_ANT1_5500',  'Power RY ANT1 5500MHz',  'POWER', 'dBm', 12.0, 19.0, true, 3, true, 'ANT1', 5500, NOW(), NOW()),
    ('WIFI_RY', 'PWR_RY_ANT2_5500',  'Power RY ANT2 5500MHz',  'POWER', 'dBm', 12.0, 19.0, true, 4, true, 'ANT2', 5500, NOW(), NOW()),
    ('WIFI_RY', 'EVM_RY_2G',         'EVM RY 2.4GHz',          'EVM',   'dB',  -35.0, -15.0, true, 5, true, NULL, 2400, NOW(), NOW()),
    ('WIFI_RY', 'EVM_RY_5G',         'EVM RY 5GHz',            'EVM',   'dB',  -35.0, -15.0, true, 6, true, NULL, 5500, NOW(), NOW()),
    ('WIFI_RY', 'FREQ_OFFSET_RY',    'Frequency Offset RY',    'FREQUENCY', 'kHz', -20.0, 20.0, true, 7, true, NULL, NULL, NOW(), NOW()),
    ('WIFI_RY', 'TEMPS_TEST_RY',     'Test Duration RY',        'TIME',  's',   1.0,  120.0, false, 8, true, NULL, NULL, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── BANC_RX_TX ──────────────────────────────────────────────────────────
-- Combined Receiver / Transmitter bench – measures both TX power and RX sensitivity.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     antenna, frequency_mhz, created_at, updated_at)
VALUES
    ('BANC_RX_TX', 'TX_POWER_2G',      'TX Power 2.4GHz',          'POWER', 'dBm', 10.0, 20.0, true, 1, true, NULL, 2400, NOW(), NOW()),
    ('BANC_RX_TX', 'TX_POWER_5G',      'TX Power 5GHz',            'POWER', 'dBm', 12.0, 22.0, true, 2, true, NULL, 5500, NOW(), NOW()),
    ('BANC_RX_TX', 'RX_SENSI_2G',      'RX Sensitivity 2.4GHz',    'POWER', 'dBm', -95.0, -70.0, true, 3, true, NULL, 2400, NOW(), NOW()),
    ('BANC_RX_TX', 'RX_SENSI_5G',      'RX Sensitivity 5GHz',      'POWER', 'dBm', -90.0, -65.0, true, 4, true, NULL, 5500, NOW(), NOW()),
    ('BANC_RX_TX', 'TX_EVM_2G',        'TX EVM 2.4GHz',            'EVM',   'dB',  -40.0, -20.0, true, 5, true, NULL, 2400, NOW(), NOW()),
    ('BANC_RX_TX', 'TX_EVM_5G',        'TX EVM 5GHz',              'EVM',   'dB',  -40.0, -20.0, true, 6, true, NULL, 5500, NOW(), NOW()),
    ('BANC_RX_TX', 'TEMPS_TEST_RXTX',  'Test Duration RX/TX',      'TIME',  's',   1.0,  180.0, false, 7, true, NULL, NULL, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── BANC_SENSI ──────────────────────────────────────────────────────────
-- Sensitivity bench – focused on receiver sensitivity thresholds per band.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     antenna, frequency_mhz, created_at, updated_at)
VALUES
    ('BANC_SENSI', 'SENSI_2G_CH1',    'Sensitivity 2.4GHz CH1',   'POWER', 'dBm', -100.0, -72.0, true, 1, true, NULL, 2412, NOW(), NOW()),
    ('BANC_SENSI', 'SENSI_2G_CH6',    'Sensitivity 2.4GHz CH6',   'POWER', 'dBm', -100.0, -72.0, true, 2, true, NULL, 2437, NOW(), NOW()),
    ('BANC_SENSI', 'SENSI_2G_CH11',   'Sensitivity 2.4GHz CH11',  'POWER', 'dBm', -100.0, -72.0, true, 3, true, NULL, 2462, NOW(), NOW()),
    ('BANC_SENSI', 'SENSI_5G_CH36',   'Sensitivity 5GHz CH36',    'POWER', 'dBm', -95.0, -68.0, true, 4, true, NULL, 5180, NOW(), NOW()),
    ('BANC_SENSI', 'SENSI_5G_CH100',  'Sensitivity 5GHz CH100',   'POWER', 'dBm', -95.0, -68.0, true, 5, true, NULL, 5500, NOW(), NOW()),
    ('BANC_SENSI', 'SENSI_5G_CH149',  'Sensitivity 5GHz CH149',   'POWER', 'dBm', -95.0, -68.0, true, 6, true, NULL, 5745, NOW(), NOW()),
    ('BANC_SENSI', 'TEMPS_TEST_SENSI','Test Duration Sensitivity', 'TIME',  's',   1.0,  90.0,  false, 7, true, NULL, NULL, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── BANC_TT ─────────────────────────────────────────────────────────────
-- TT (Throughput Test) bench – measures data throughput and packet rates.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('BANC_TT', 'THROUGHPUT_DL_2G',  'Download Throughput 2.4GHz',   'OTHER',  'Mbps', 50.0,  600.0,  true, 1, true, NOW(), NOW()),
    ('BANC_TT', 'THROUGHPUT_UL_2G',  'Upload Throughput 2.4GHz',     'OTHER',  'Mbps', 20.0,  300.0,  true, 2, true, NOW(), NOW()),
    ('BANC_TT', 'THROUGHPUT_DL_5G',  'Download Throughput 5GHz',     'OTHER',  'Mbps', 200.0, 2400.0, true, 3, true, NOW(), NOW()),
    ('BANC_TT', 'THROUGHPUT_UL_5G',  'Upload Throughput 5GHz',       'OTHER',  'Mbps', 80.0,  1200.0, true, 4, true, NOW(), NOW()),
    ('BANC_TT', 'PER_TT_2G',        'Packet Error Rate 2.4GHz',     'PER',    '%',    0.0,   5.0,    true, 5, true, NOW(), NOW()),
    ('BANC_TT', 'PER_TT_5G',        'Packet Error Rate 5GHz',       'PER',    '%',    0.0,   5.0,    true, 6, true, NOW(), NOW()),
    ('BANC_TT', 'LATENCY_TT',       'Network Latency',              'TIME',   'ms',   0.0,   50.0,   true, 7, true, NOW(), NOW()),
    ('BANC_TT', 'TEMPS_TEST_TT',    'Test Duration TT',             'TIME',   's',    1.0,   300.0,  false, 8, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── BANC_TX ─────────────────────────────────────────────────────────────
-- TX-only bench – transmitter power and spectral quality.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     antenna, frequency_mhz, created_at, updated_at)
VALUES
    ('BANC_TX', 'TX_PWR_ANT1_2G',    'TX Power ANT1 2.4GHz',     'POWER', 'dBm', 11.0, 19.0, true, 1, true, 'ANT1', 2400, NOW(), NOW()),
    ('BANC_TX', 'TX_PWR_ANT2_2G',    'TX Power ANT2 2.4GHz',     'POWER', 'dBm', 11.0, 19.0, true, 2, true, 'ANT2', 2400, NOW(), NOW()),
    ('BANC_TX', 'TX_PWR_ANT1_5G',    'TX Power ANT1 5GHz',       'POWER', 'dBm', 13.0, 20.0, true, 3, true, 'ANT1', 5500, NOW(), NOW()),
    ('BANC_TX', 'TX_PWR_ANT2_5G',    'TX Power ANT2 5GHz',       'POWER', 'dBm', 13.0, 20.0, true, 4, true, 'ANT2', 5500, NOW(), NOW()),
    ('BANC_TX', 'TX_SPECTRUM_MASK',   'Spectrum Mask Compliance',  'OTHER', 'dB',  0.0,  5.0,  true, 5, true, NULL,   NULL, NOW(), NOW()),
    ('BANC_TX', 'TX_FREQ_OFFSET',     'TX Frequency Offset',       'FREQUENCY', 'kHz', -25.0, 25.0, true, 6, true, NULL, NULL, NOW(), NOW()),
    ('BANC_TX', 'TEMPS_TEST_TX',      'Test Duration TX',          'TIME',  's',   1.0, 120.0, false, 7, true, NULL, NULL, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════════
-- BBS INTÉGRATION
-- ═══════════════════════════════════════════════════════════════════════════

-- ─── AQ_LIGNE ────────────────────────────────────────────────────────────
-- Line Quality Assurance – end-of-line aggregate checks.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('AQ_LIGNE', 'CURRENT_IDLE',      'Idle Current Consumption',     'CURRENT',     'mA',  0.0,   800.0,  true, 1, true, NOW(), NOW()),
    ('AQ_LIGNE', 'CURRENT_MAX_LOAD',  'Max Load Current',             'CURRENT',     'mA',  0.0,   2500.0, true, 2, true, NOW(), NOW()),
    ('AQ_LIGNE', 'VOLTAGE_ALIM',      'Supply Voltage',               'VOLTAGE',     'V',   11.0,  13.0,   true, 3, true, NOW(), NOW()),
    ('AQ_LIGNE', 'TEMP_BOARD',        'Board Temperature',            'TEMPERATURE', '°C',  0.0,   75.0,   true, 4, true, NOW(), NOW()),
    ('AQ_LIGNE', 'LED_STATUS',        'LED Status Check',             'OTHER',       'bool', 0.0,  1.0,    true, 5, true, NOW(), NOW()),
    ('AQ_LIGNE', 'TEMPS_TEST_AQ',     'Test Duration AQ Ligne',       'TIME',        's',   1.0,   60.0,   false, 6, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── TELECHARGEMENT ──────────────────────────────────────────────────────
-- Firmware download / provisioning station.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('TELECHARGEMENT', 'FW_DOWNLOAD_TIME',  'Firmware Download Time',      'TIME',    's',    1.0,  300.0, true, 1, true, NOW(), NOW()),
    ('TELECHARGEMENT', 'FW_VERIFY_CRC',     'Firmware CRC Verification',   'OTHER',   'bool', 0.0,  1.0,   true, 2, true, NOW(), NOW()),
    ('TELECHARGEMENT', 'FW_FLASH_TIME',     'Flash Write Time',            'TIME',    's',    1.0,  120.0, true, 3, true, NOW(), NOW()),
    ('TELECHARGEMENT', 'BOOT_TIME',         'Boot Time After Flash',       'TIME',    's',    1.0,  180.0, true, 4, true, NOW(), NOW()),
    ('TELECHARGEMENT', 'FW_VERSION_CHECK',  'Firmware Version Match',      'OTHER',   'bool', 0.0,  1.0,   true, 5, true, NOW(), NOW()),
    ('TELECHARGEMENT', 'TEMPS_TEST_DL',     'Test Duration Download',      'TIME',    's',    1.0,  600.0, false, 6, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── BANC_NFT ────────────────────────────────────────────────────────────
-- Non-Functional Test bench – general hardware validation.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('BANC_NFT', 'NFT_USB_CHECK',     'USB Port Continuity',       'OTHER',       'bool', 0.0,  1.0,    true, 1, true, NOW(), NOW()),
    ('BANC_NFT', 'NFT_ETH_LINK',      'Ethernet Link Speed',       'OTHER',       'Mbps', 100.0, 2500.0, true, 2, true, NOW(), NOW()),
    ('BANC_NFT', 'NFT_LED_RGB',       'LED RGB Validation',        'OTHER',       'bool', 0.0,  1.0,    true, 3, true, NOW(), NOW()),
    ('BANC_NFT', 'NFT_CURRENT_IDLE',  'Idle Current NFT',          'CURRENT',     'mA',  0.0,   700.0,  true, 4, true, NOW(), NOW()),
    ('BANC_NFT', 'NFT_TEMP_CPU',      'CPU Temperature NFT',       'TEMPERATURE', '°C',  0.0,   90.0,   true, 5, true, NOW(), NOW()),
    ('BANC_NFT', 'NFT_RESET_COUNT',   'Reset Cycle Validation',    'OTHER',       'count', 0.0, 5.0,    true, 6, true, NOW(), NOW()),
    ('BANC_NFT', 'TEMPS_TEST_NFT',    'Test Duration NFT',         'TIME',        's',   1.0,   180.0,  false, 7, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── BANC_NFT_BOUTON ────────────────────────────────────────────────────
-- NFT bench with button / tactile switch validation.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('BANC_NFT_BOUTON', 'BTN_WPS_PRESS',     'WPS Button Press Force',     'OTHER',   'gf',   50.0,  350.0, true, 1, true, NOW(), NOW()),
    ('BANC_NFT_BOUTON', 'BTN_RESET_PRESS',   'Reset Button Press Force',   'OTHER',   'gf',   50.0,  350.0, true, 2, true, NOW(), NOW()),
    ('BANC_NFT_BOUTON', 'BTN_WPS_RESPONSE',  'WPS Button Response Time',   'TIME',    'ms',   0.0,   500.0, true, 3, true, NOW(), NOW()),
    ('BANC_NFT_BOUTON', 'BTN_RESET_RESPONSE','Reset Button Response Time',  'TIME',    'ms',   0.0,   500.0, true, 4, true, NOW(), NOW()),
    ('BANC_NFT_BOUTON', 'BTN_LED_FEEDBACK',  'Button LED Feedback Check',  'OTHER',   'bool', 0.0,   1.0,   true, 5, true, NOW(), NOW()),
    ('BANC_NFT_BOUTON', 'TEMPS_TEST_NFTB',   'Test Duration NFT Bouton',   'TIME',    's',    1.0,   60.0,  false, 6, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── TEST_BOUTON ─────────────────────────────────────────────────────────
-- Standalone button / switch test station.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('TEST_BOUTON', 'BTN_TACTILE_FORCE',  'Tactile Switch Force',       'OTHER', 'gf',   40.0,  300.0, true, 1, true, NOW(), NOW()),
    ('TEST_BOUTON', 'BTN_TRAVEL_DIST',    'Button Travel Distance',     'OTHER', 'mm',   0.1,   3.0,   true, 2, true, NOW(), NOW()),
    ('TEST_BOUTON', 'BTN_DEBOUNCE_TIME',  'Debounce Time',              'TIME',  'ms',   0.0,   50.0,  true, 3, true, NOW(), NOW()),
    ('TEST_BOUTON', 'BTN_LIFECYCLE',      'Button Lifecycle Press Count','OTHER', 'count',1.0,   10.0,  true, 4, true, NOW(), NOW()),
    ('TEST_BOUTON', 'TEMPS_TEST_BTN',     'Test Duration Bouton',       'TIME',  's',    1.0,   45.0,  false, 5, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── TEST_VISION ─────────────────────────────────────────────────────────
-- Visual / optical inspection station (automated or camera-based).
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('TEST_VISION', 'VIS_LABEL_PRESENT',  'Label Presence Check',       'OTHER', 'bool', 0.0,  1.0,   true, 1, true, NOW(), NOW()),
    ('TEST_VISION', 'VIS_BARCODE_READ',   'Barcode Readability',        'OTHER', 'bool', 0.0,  1.0,   true, 2, true, NOW(), NOW()),
    ('TEST_VISION', 'VIS_LED_ALIGNMENT',  'LED Alignment Score',        'OTHER', '%',    80.0, 100.0, true, 3, true, NOW(), NOW()),
    ('TEST_VISION', 'VIS_COSMETIC_SCORE', 'Cosmetic Inspection Score',  'OTHER', '%',    90.0, 100.0, true, 4, true, NOW(), NOW()),
    ('TEST_VISION', 'VIS_PCB_SOLDER',     'PCB Solder Joint Quality',   'OTHER', '%',    85.0, 100.0, true, 5, true, NOW(), NOW()),
    ('TEST_VISION', 'TEMPS_TEST_VISION',  'Test Duration Vision',       'TIME',  's',    1.0,  30.0,  false, 6, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── TEST_DOCSIS ─────────────────────────────────────────────────────────
-- DOCSIS protocol conformance testing.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('TEST_DOCSIS', 'DOCSIS_DS_POWER',     'Downstream Power Level',       'POWER', 'dBmV', -15.0, 15.0,  true, 1, true, NOW(), NOW()),
    ('TEST_DOCSIS', 'DOCSIS_US_POWER',     'Upstream Power Level',         'POWER', 'dBmV', 35.0,  55.0,  true, 2, true, NOW(), NOW()),
    ('TEST_DOCSIS', 'DOCSIS_SNR_DS',       'Downstream SNR',               'POWER', 'dB',   25.0,  45.0,  true, 3, true, NOW(), NOW()),
    ('TEST_DOCSIS', 'DOCSIS_DS_THROUGHPUT','Downstream Throughput',         'OTHER', 'Mbps', 100.0, 3000.0,true, 4, true, NOW(), NOW()),
    ('TEST_DOCSIS', 'DOCSIS_US_THROUGHPUT','Upstream Throughput',           'OTHER', 'Mbps', 30.0,  600.0, true, 5, true, NOW(), NOW()),
    ('TEST_DOCSIS', 'DOCSIS_REG_TIME',     'DOCSIS Registration Time',     'TIME',  's',    1.0,   60.0,  true, 6, true, NOW(), NOW()),
    ('TEST_DOCSIS', 'TEMPS_TEST_DOCSIS',   'Test Duration DOCSIS',         'TIME',  's',    1.0,   300.0, false, 7, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── TEST_SYNCHRO_GPON ──────────────────────────────────────────────────
-- GPON synchronization and optical power testing.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('TEST_SYNCHRO_GPON', 'GPON_TX_OPTICAL',    'GPON TX Optical Power',        'POWER', 'dBm',  0.5,   5.0,   true, 1, true, NOW(), NOW()),
    ('TEST_SYNCHRO_GPON', 'GPON_RX_OPTICAL',    'GPON RX Optical Power',        'POWER', 'dBm', -28.0, -8.0,   true, 2, true, NOW(), NOW()),
    ('TEST_SYNCHRO_GPON', 'GPON_SYNC_TIME',     'GPON Sync Time',               'TIME',  's',    0.5,   30.0,  true, 3, true, NOW(), NOW()),
    ('TEST_SYNCHRO_GPON', 'GPON_ONU_REG_TIME',  'ONU Registration Time',        'TIME',  's',    1.0,   45.0,  true, 4, true, NOW(), NOW()),
    ('TEST_SYNCHRO_GPON', 'GPON_DS_THROUGHPUT', 'GPON Downstream Throughput',    'OTHER', 'Mbps', 500.0, 2500.0,true, 5, true, NOW(), NOW()),
    ('TEST_SYNCHRO_GPON', 'GPON_BER',           'Bit Error Rate',               'PER',   'ratio',0.0,   0.001, true, 6, true, NOW(), NOW()),
    ('TEST_SYNCHRO_GPON', 'TEMPS_TEST_GPON',    'Test Duration GPON',           'TIME',  's',    1.0,   120.0, false, 7, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════════
-- AVS CMS
-- ═══════════════════════════════════════════════════════════════════════════

-- ─── BANC_AUDIO_VIDEO ────────────────────────────────────────────────────
-- Audio/Video quality bench for set-top boxes / media devices.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('BANC_AUDIO_VIDEO', 'AV_HDMI_SIGNAL',    'HDMI Signal Level',          'VOLTAGE', 'mV',  200.0, 800.0,  true, 1, true, NOW(), NOW()),
    ('BANC_AUDIO_VIDEO', 'AV_VIDEO_RES',      'Video Resolution Check',     'OTHER',   'bool', 0.0,  1.0,    true, 2, true, NOW(), NOW()),
    ('BANC_AUDIO_VIDEO', 'AV_AUDIO_THD',      'Audio THD',                  'PER',     '%',    0.0,  1.0,    true, 3, true, NOW(), NOW()),
    ('BANC_AUDIO_VIDEO', 'AV_AUDIO_SNR',      'Audio SNR',                  'POWER',   'dB',   60.0, 100.0,  true, 4, true, NOW(), NOW()),
    ('BANC_AUDIO_VIDEO', 'AV_AUDIO_LEVEL_L',  'Audio Level Left Channel',   'POWER',   'dBFS',-20.0, 0.0,    true, 5, true, NOW(), NOW()),
    ('BANC_AUDIO_VIDEO', 'AV_AUDIO_LEVEL_R',  'Audio Level Right Channel',  'POWER',   'dBFS',-20.0, 0.0,    true, 6, true, NOW(), NOW()),
    ('BANC_AUDIO_VIDEO', 'AV_SYNC_OFFSET',    'AV Sync Offset',            'TIME',    'ms',   -30.0, 30.0,  true, 7, true, NOW(), NOW()),
    ('BANC_AUDIO_VIDEO', 'TEMPS_TEST_AV',     'Test Duration Audio/Video',  'TIME',    's',    1.0,  120.0,  false, 8, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── BANC_WIFI_CONDUIT (AVS variant) ────────────────────────────────────
-- WiFi conducted test bench for AVS products (different product line, same concept).
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     antenna, frequency_mhz, created_at, updated_at)
VALUES
    ('BANC_WIFI_CONDUIT', 'BWC_AVS_PWR_ANT1_2G', 'Power AVS ANT1 2.4GHz',  'POWER', 'dBm', 10.0, 18.0, true, 1, true, 'ANT1', 2400, NOW(), NOW()),
    ('BANC_WIFI_CONDUIT', 'BWC_AVS_PWR_ANT2_2G', 'Power AVS ANT2 2.4GHz',  'POWER', 'dBm', 10.0, 18.0, true, 2, true, 'ANT2', 2400, NOW(), NOW()),
    ('BANC_WIFI_CONDUIT', 'BWC_AVS_PWR_ANT1_5G', 'Power AVS ANT1 5GHz',    'POWER', 'dBm', 12.0, 19.0, true, 3, true, 'ANT1', 5500, NOW(), NOW()),
    ('BANC_WIFI_CONDUIT', 'BWC_AVS_PWR_ANT2_5G', 'Power AVS ANT2 5GHz',    'POWER', 'dBm', 12.0, 19.0, true, 4, true, 'ANT2', 5500, NOW(), NOW()),
    ('BANC_WIFI_CONDUIT', 'BWC_AVS_EVM_2G',      'EVM AVS 2.4GHz',         'EVM',   'dB',  -38.0, -18.0, true, 5, true, NULL, 2400, NOW(), NOW()),
    ('BANC_WIFI_CONDUIT', 'BWC_AVS_EVM_5G',      'EVM AVS 5GHz',           'EVM',   'dB',  -38.0, -18.0, true, 6, true, NULL, 5500, NOW(), NOW()),
    ('BANC_WIFI_CONDUIT', 'BWC_AVS_RSSI_2G',     'RSSI AVS 2.4GHz',        'RSSI',  'dBm', -80.0, -30.0, true, 7, true, NULL, 2400, NOW(), NOW()),
    ('BANC_WIFI_CONDUIT', 'BWC_AVS_RSSI_5G',     'RSSI AVS 5GHz',          'RSSI',  'dBm', -80.0, -30.0, true, 8, true, NULL, 5500, NOW(), NOW()),
    ('BANC_WIFI_CONDUIT', 'TEMPS_TEST_BWC_AVS',  'Test Duration BWC AVS',   'TIME',  's',   1.0,  180.0, false, 9, true, NULL, NULL, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════════
-- AVS INTÉGRATION
-- ═══════════════════════════════════════════════════════════════════════════

-- ─── BPO ─────────────────────────────────────────────────────────────────
-- BPO (Banc de Production Optique) – optical production bench.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('BPO', 'BPO_OPTICAL_TX',       'Optical TX Power',           'POWER',   'dBm',  -1.0,  4.0,    true, 1, true, NOW(), NOW()),
    ('BPO', 'BPO_OPTICAL_RX',       'Optical RX Sensitivity',     'POWER',   'dBm',  -25.0, -8.0,   true, 2, true, NOW(), NOW()),
    ('BPO', 'BPO_EXTINCTION_RATIO', 'Extinction Ratio',           'POWER',   'dB',    8.0,  15.0,   true, 3, true, NOW(), NOW()),
    ('BPO', 'BPO_WAVELENGTH',       'TX Wavelength',              'OTHER',   'nm',    1300.0, 1330.0, true, 4, true, NOW(), NOW()),
    ('BPO', 'BPO_BER',              'Bit Error Rate',             'PER',     'ratio', 0.0,   0.001, true, 5, true, NOW(), NOW()),
    ('BPO', 'TEMPS_TEST_BPO',       'Test Duration BPO',          'TIME',    's',     1.0,   120.0, false, 6, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── FSOS ────────────────────────────────────────────────────────────────
-- FSOS (Functional System Operating Software) – system-level functional checks.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('FSOS', 'FSOS_BOOT_TIME',      'System Boot Time',          'TIME',        's',    1.0,  120.0, true, 1, true, NOW(), NOW()),
    ('FSOS', 'FSOS_CPU_LOAD',       'CPU Load Under Test',       'PER',         '%',    0.0,  90.0,  true, 2, true, NOW(), NOW()),
    ('FSOS', 'FSOS_RAM_USAGE',      'RAM Usage Under Test',      'PER',         '%',    0.0,  85.0,  true, 3, true, NOW(), NOW()),
    ('FSOS', 'FSOS_FLASH_INTEGRITY','Flash Integrity Check',     'OTHER',       'bool', 0.0,  1.0,   true, 4, true, NOW(), NOW()),
    ('FSOS', 'FSOS_SERVICE_UP',     'Service Startup Check',     'OTHER',       'bool', 0.0,  1.0,   true, 5, true, NOW(), NOW()),
    ('FSOS', 'FSOS_TEMP_SOC',       'SoC Temperature',           'TEMPERATURE', '°C',   0.0,  85.0,  true, 6, true, NOW(), NOW()),
    ('FSOS', 'TEMPS_TEST_FSOS',     'Test Duration FSOS',        'TIME',        's',    1.0,  180.0, false, 7, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── BANC_ETANCHEITE ────────────────────────────────────────────────────
-- Sealing / waterproofness bench – enclosure IP rating validation.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('BANC_ETANCHEITE', 'SEAL_PRESSURE',      'Sealing Test Pressure',       'OTHER',       'mbar', 10.0,  100.0,  true, 1, true, NOW(), NOW()),
    ('BANC_ETANCHEITE', 'SEAL_LEAK_RATE',     'Leak Rate',                   'OTHER',       'mbar/min', 0.0, 2.0,  true, 2, true, NOW(), NOW()),
    ('BANC_ETANCHEITE', 'SEAL_HOLD_TIME',     'Pressure Hold Time',          'TIME',        's',    10.0,  120.0,  true, 3, true, NOW(), NOW()),
    ('BANC_ETANCHEITE', 'SEAL_TEMP_AMBIENT',  'Ambient Temperature',         'TEMPERATURE', '°C',   15.0,  35.0,   true, 4, true, NOW(), NOW()),
    ('BANC_ETANCHEITE', 'SEAL_RESULT',        'Sealing Pass/Fail',           'OTHER',       'bool', 0.0,   1.0,    true, 5, true, NOW(), NOW()),
    ('BANC_ETANCHEITE', 'TEMPS_TEST_SEAL',    'Test Duration Sealing',       'TIME',        's',    1.0,   300.0,  false, 6, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;


-- ─── BANC_ACOUSTIQUE ────────────────────────────────────────────────────
-- Acoustic bench – speaker/microphone quality testing.
INSERT INTO poste_measure_catalog
    (poste_type, measure_code, measure_label, category, default_unit,
     default_lower_bound, default_upper_bound, mandatory, display_order, active,
     created_at, updated_at)
VALUES
    ('BANC_ACOUSTIQUE', 'ACO_SPK_LEVEL_1K',  'Speaker Level 1kHz',         'POWER', 'dBSPL', 70.0,  95.0,  true, 1, true, NOW(), NOW()),
    ('BANC_ACOUSTIQUE', 'ACO_SPK_THD',       'Speaker THD',                'PER',   '%',     0.0,   3.0,   true, 2, true, NOW(), NOW()),
    ('BANC_ACOUSTIQUE', 'ACO_MIC_SENSI',     'Microphone Sensitivity',     'POWER', 'dBV',  -50.0, -25.0,  true, 3, true, NOW(), NOW()),
    ('BANC_ACOUSTIQUE', 'ACO_MIC_SNR',       'Microphone SNR',             'POWER', 'dB',    55.0,  80.0,  true, 4, true, NOW(), NOW()),
    ('BANC_ACOUSTIQUE', 'ACO_FREQ_RESP_LOW', 'Freq Response Low (300Hz)',  'POWER', 'dB',   -3.0,   3.0,  true, 5, true, NOW(), NOW()),
    ('BANC_ACOUSTIQUE', 'ACO_FREQ_RESP_HIGH','Freq Response High (8kHz)',  'POWER', 'dB',   -5.0,   3.0,  true, 6, true, NOW(), NOW()),
    ('BANC_ACOUSTIQUE', 'ACO_ECHO_CANCEL',   'Echo Cancellation dB',       'POWER', 'dB',    30.0,  65.0,  true, 7, true, NOW(), NOW()),
    ('BANC_ACOUSTIQUE', 'TEMPS_TEST_ACO',    'Test Duration Acoustic',     'TIME',  's',     1.0,  120.0,  false, 8, true, NOW(), NOW())
ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;

-- ============================================================================
-- Summary: 19 PosteTypes seeded (total ~130 catalog rows)
-- ============================================================================
