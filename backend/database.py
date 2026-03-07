"""
BPSync Database Configuration

PostgreSQL  → users, notifications  (SQLAlchemy ORM)
TimescaleDB → wristband_data, ecg_data, bp_readings  (raw SQL)
"""
from sqlalchemy import create_engine
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


def create_tables():
    """Create all ORM-managed tables (users, notifications)."""
    Base.metadata.create_all(bind=pg_engine)


# --- TimescaleDB (sensor time-series) ---
ts_engine = create_engine(settings.sensor_database_url)
SensorSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=ts_engine)


def get_sensor_db():
    db = SensorSessionLocal()
    try:
        yield db
    finally:
        db.close()
