CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Raw wrist frames received through the phone-mediated dual-device flow.
-- Stores both the latest values used by legacy consumers and the original
-- batch metadata produced by the new firmware/backend sync path.
CREATE TABLE IF NOT EXISTS wristband_data (
    time                TIMESTAMPTZ  NOT NULL,
    user_id             VARCHAR(255) NOT NULL,
    device_timestamp_ms BIGINT,
    received_at_ms      BIGINT,
    frame_seq           INTEGER,
    chest_seq           INTEGER,
    frame_mode          VARCHAR(20),
    ppg_ir              BIGINT,
    ppg_red             BIGINT,
    ppg_ir_batch        JSONB,
    ppg_red_batch       JSONB,
    ax                  INT,
    ay                  INT,
    az                  INT,
    gx                  INT,
    gy                  INT,
    gz                  INT,
    temperature         FLOAT,
    ep                  SMALLINT,
    qi_w                SMALLINT,
    qi_c                SMALLINT,
    qi                  SMALLINT,
    battery             SMALLINT
);

SELECT create_hypertable('wristband_data', 'time', if_not_exists => TRUE);
CREATE INDEX IF NOT EXISTS idx_wristband_user_time ON wristband_data (user_id, time DESC);
CREATE INDEX IF NOT EXISTS idx_wristband_user_seq ON wristband_data (user_id, frame_seq DESC);

-- Raw ECG samples expanded from chest frames.
CREATE TABLE IF NOT EXISTS ecg_data (
    time                TIMESTAMPTZ  NOT NULL,
    user_id             VARCHAR(255) NOT NULL,
    device_timestamp_ms BIGINT,
    received_at_ms      BIGINT,
    frame_seq           INTEGER,
    sample_index        SMALLINT,
    ecg_value           FLOAT,
    ep                  SMALLINT,
    qi_c                SMALLINT
);

SELECT create_hypertable('ecg_data', 'time', if_not_exists => TRUE);
CREATE INDEX IF NOT EXISTS idx_ecg_user_time ON ecg_data (user_id, time DESC);
CREATE INDEX IF NOT EXISTS idx_ecg_user_seq ON ecg_data (user_id, frame_seq DESC);

-- Processed BP readings written after the synchronized dual-device window is
-- inferred by the backend model.
CREATE TABLE IF NOT EXISTS bp_readings (
    time             TIMESTAMPTZ  NOT NULL,
    user_id          VARCHAR(255) NOT NULL,
    systolic         SMALLINT,
    diastolic        SMALLINT,
    heart_rate       SMALLINT,
    spo2             SMALLINT,
    ptt              FLOAT,
    quality          SMALLINT,
    category         VARCHAR(30),
    model_name       VARCHAR(50),
    stream_mode      VARCHAR(20),
    window_frames    SMALLINT,
    source_seq_start INTEGER,
    source_seq_end   INTEGER
);

SELECT create_hypertable('bp_readings', 'time', if_not_exists => TRUE);
CREATE INDEX IF NOT EXISTS idx_bp_user_time ON bp_readings (user_id, time DESC);
