CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Wristband Data
CREATE TABLE IF NOT EXISTS wristband_data (
    time TIMESTAMPTZ NOT NULL,
    patient_id INT,
    heart_rate INT,
    spo2 NUMERIC,
    movement NUMERIC
);

SELECT create_hypertable('wristband_data', 'time', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_wristband_patient_time
ON wristband_data (patient_id, time DESC);

-- ECG Processed Data
CREATE TABLE IF NOT EXISTS ecg_data (
    time TIMESTAMPTZ NOT NULL,
    patient_id INT,
    ecg_value NUMERIC
);

SELECT create_hypertable('ecg_data', 'time', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_ecg_patient_time
ON ecg_data (patient_id, time DESC);