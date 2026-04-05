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
        conn.execute(text("ALTER TABLE wristband_data ALTER COLUMN user_id TYPE VARCHAR(255)"))
        conn.execute(text("ALTER TABLE ecg_data ALTER COLUMN user_id TYPE VARCHAR(255)"))
        conn.execute(text("ALTER TABLE bp_readings ALTER COLUMN user_id TYPE VARCHAR(255)"))
        conn.execute(text("ALTER TABLE bp_readings ADD COLUMN IF NOT EXISTS spo2 SMALLINT"))
        conn.commit()
    print("[DB] TimescaleDB sensor tables ready.")


def normalize_sensor_user_keys():
    """
    Consolidate sensor rows onto canonical email-based owner keys.

    Older app versions wrote Timescale rows with PostgreSQL user UUIDs. We now
    normalize those rows to the user's email so future reads and writes use one
    stable identifier across environments.
    """
    try:
        with pg_engine.connect() as pg_conn:
            users = pg_conn.execute(
                text("SELECT id, email FROM users WHERE email IS NOT NULL")
            ).fetchall()
    except Exception as e:
        print(f"[DB] Sensor owner normalization skipped (users query failed): {e}")
        return

    table_names = ("wristband_data", "ecg_data", "bp_readings")
    updates = 0

    try:
        with ts_engine.connect() as ts_conn:
            for user in users:
                normalized_email = (user.email or "").strip().lower()
                if not normalized_email:
                    continue

                for table_name in table_names:
                    result_by_id = ts_conn.execute(
                        text(f"""
                            UPDATE {table_name}
                            SET user_id = :normalized_email
                            WHERE user_id = :legacy_user_id
                        """),
                        {
                            "normalized_email": normalized_email,
                            "legacy_user_id": user.id,
                        },
                    )
                    result_by_email = ts_conn.execute(
                        text(f"""
                            UPDATE {table_name}
                            SET user_id = :normalized_email
                            WHERE LOWER(user_id) = :normalized_email
                              AND user_id <> :normalized_email
                        """),
                        {"normalized_email": normalized_email},
                    )
                    updates += (result_by_id.rowcount or 0) + (result_by_email.rowcount or 0)

            ts_conn.commit()
    except Exception as e:
        print(f"[DB] Sensor owner normalization skipped (sensor update failed): {e}")
        return

    print(f"[DB] Sensor owner normalization complete. Updated rows: {updates}")


def create_tables():
    """Create all ORM-managed tables (users, notifications) and sensor tables."""
    Base.metadata.create_all(bind=pg_engine)

    # Safe migrations for optional profile columns added after the first schema version.
    user_column_migrations = [
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS gender VARCHAR",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS weight VARCHAR",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS height VARCHAR",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS last_checkup_date VARCHAR",
    ]

    try:
        with pg_engine.connect() as conn:
            for statement in user_column_migrations:
                conn.execute(text(statement))
            conn.commit()
    except Exception as e:
        print(f"[DB] Migration warning (users table): {e}")

    try:
        create_sensor_tables()
    except Exception as e:
        # Log but don't crash — sensor tables may already exist or extension unavailable
        print(f"[DB] Sensor table init warning: {e}")

    try:
        normalize_sensor_user_keys()
    except Exception as e:
        print(f"[DB] Sensor owner normalization warning: {e}")


# --- TimescaleDB (sensor time-series) ---
ts_engine = create_engine(settings.sensor_database_url)
SensorSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=ts_engine)


def get_sensor_db():
    db = SensorSessionLocal()
    try:
        yield db
    finally:
        db.close()
