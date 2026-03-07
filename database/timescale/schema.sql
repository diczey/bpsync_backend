CREATE EXTENSION IF NOT EXISTS timescaledb;

-- ─────────────────────────────────────────────
-- Raw wristband frames (10 Hz, firmware JSON)
-- Fields match firmware buildJSON() exactly
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS wristband_data (
    time        TIMESTAMPTZ  NOT NULL,
    user_id     VARCHAR(36)  NOT NULL,
    ppg_ir      BIGINT,
    ppg_red     BIGINT,
    ax          INT,
    ay          INT,
    az          INT,
    gx          INT,
    gy          INT,
    gz          INT,
    temperature FLOAT,
    ep          SMALLINT,
    qi_w        SMALLINT,
    qi_c        SMALLINT,
    qi          SMALLINT,
    battery     SMALLINT
);

SELECT create_hypertable('wristband_data', 'time', if_not_exists => TRUE);
CREATE INDEX IF NOT EXISTS idx_wristband_user_time ON wristband_data (user_id, time DESC);

-- ─────────────────────────────────────────────
-- Raw ECG frames (chest module)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecg_data (
    time      TIMESTAMPTZ NOT NULL,
    user_id   VARCHAR(36) NOT NULL,
    ecg_value FLOAT
);

SELECT create_hypertable('ecg_data', 'time', if_not_exists => TRUE);
CREATE INDEX IF NOT EXISTS idx_ecg_user_time ON ecg_data (user_id, time DESC);

-- ─────────────────────────────────────────────
-- Processed BP readings
-- Written by ble/data_manager.py after ML inference
-- (~every 10 seconds, 100-frame window)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bp_readings (
    time       TIMESTAMPTZ  NOT NULL,
    user_id    VARCHAR(36)  NOT NULL,
    systolic   SMALLINT,
    diastolic  SMALLINT,
    heart_rate SMALLINT,
    ptt        FLOAT,
    quality    SMALLINT,
    category   VARCHAR(30)
);

SELECT create_hypertable('bp_readings', 'time', if_not_exists => TRUE);
CREATE INDEX IF NOT EXISTS idx_bp_user_time ON bp_readings (user_id, time DESC);
