"""
BPSync Database Configuration

PostgreSQL  → users, notifications  (SQLAlchemy ORM)
TimescaleDB → wristband_data, ecg_data, bp_readings  (raw SQL)
"""
from sqlalchemy import create_engine, text
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

from backend.config import settings

# --- PostgreSQL (user data) ---
pg_engine = create_engine(settings.database_url)
UserSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=pg_engine)
Base = declarative_base()


def get_db():
    db = UserSessionLocal()
    try:
        yield db
    finally:
        db.close()


def create_sensor_tables():
    """
    Create TimescaleDB time-series tables on startup (raw SQL, IF NOT EXISTS).
    Safe to run on every boot — existing data is never touched.
    """
    ddl = """
        CREATE EXTENSION IF NOT EXISTS timescaledb;

        CREATE TABLE IF NOT EXISTS wristband_data (
            time        TIMESTAMPTZ  NOT NULL,
            user_id     VARCHAR(36)  NOT NULL,
            ppg_ir      BIGINT,
            ppg_red     BIGINT,
            ax INT, ay INT, az INT,
            gx INT, gy INT, gz INT,
            temperature FLOAT,
            ep SMALLINT, qi_w SMALLINT, qi_c SMALLINT, qi SMALLINT,
            battery SMALLINT
        );
        SELECT create_hypertable('wristband_data', 'time', if_not_exists => TRUE);
        CREATE INDEX IF NOT EXISTS idx_wristband_user_time
            ON wristband_data (user_id, time DESC);

        CREATE TABLE IF NOT EXISTS ecg_data (
            time      TIMESTAMPTZ NOT NULL,
            user_id   VARCHAR(36) NOT NULL,
            ecg_value FLOAT
        );
        SELECT create_hypertable('ecg_data', 'time', if_not_exists => TRUE);
        CREATE INDEX IF NOT EXISTS idx_ecg_user_time
            ON ecg_data (user_id, time DESC);

        CREATE TABLE IF NOT EXISTS bp_readings (
            time       TIMESTAMPTZ  NOT NULL,
            user_id    VARCHAR(36)  NOT NULL,
            systolic   SMALLINT,
            diastolic  SMALLINT,
            heart_rate SMALLINT,
            spo2       SMALLINT,
            ptt        FLOAT,
            quality    SMALLINT,
            category   VARCHAR(30)
        );
        SELECT create_hypertable('bp_readings', 'time', if_not_exists => TRUE);
        CREATE INDEX IF NOT EXISTS idx_bp_user_time
            ON bp_readings (user_id, time DESC);
    """
    with ts_engine.connect() as conn:
        conn.execute(text(ddl))
        conn.commit()
    print("[DB] TimescaleDB sensor tables ready.")


def create_tables():
    """Create all ORM-managed tables (users, notifications) and sensor tables."""
    Base.metadata.create_all(bind=pg_engine)
    try:
        create_sensor_tables()
    except Exception as e:
        # Log but don't crash — sensor tables may already exist or extension unavailable
        print(f"[DB] Sensor table init warning: {e}")


# --- TimescaleDB (sensor time-series) ---
ts_engine = create_engine(settings.sensor_database_url)
SensorSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=ts_engine)


def get_sensor_db():
    db = SensorSessionLocal()
    try:
        yield db
    finally:
        db.close()
