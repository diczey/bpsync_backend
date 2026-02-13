CREATE TABLE IF NOT EXISTS patients (
    id SERIAL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    birth_date DATE,
    height_cm NUMERIC,
    weight_kg NUMERIC,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS devices (
    id SERIAL PRIMARY KEY,
    device_type VARCHAR(50) CHECK (device_type IN ('wristband', 'ecg')),
    serial_number VARCHAR(100),
    patient_id INT REFERENCES patients(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- BMI View
CREATE OR REPLACE VIEW patient_bmi AS
SELECT
    id,
    full_name,
    weight_kg / ((height_cm/100)^2) AS bmi
FROM patients;