"""
BPSync Database Configuration
"""
from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

from backend.app.config import settings

# ---- User DB (Postgres) ----
user_engine = create_engine(settings.database_url)
UserSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=user_engine)
Base = declarative_base()

# ---- Sensor DB (Timescale) ----
sensor_engine = create_engine(settings.sensor_database_url)
SensorSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=sensor_engine)


def get_db():
    db = UserSessionLocal()
    try:
        yield db
    finally:
        db.close()

def create_tables():
    Base.metadata.create_all(bind=user_engine)

# ---- Sensor DB (Timescale) ----
sensor_engine = create_engine(settings.sensor_database_url)
SensorSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=sensor_engine)

def get_sensor_db():
    db = SensorSessionLocal()
    try:
        yield db
    finally:
        db.close()