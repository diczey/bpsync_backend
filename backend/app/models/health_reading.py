"""
Health Reading Database Model
"""
from sqlalchemy import Column, String, Integer, Float, BigInteger, ForeignKey, JSON
from sqlalchemy.orm import relationship
from datetime import datetime
import uuid

from backend.app.database import Base


class HealthReading(Base):
    """Health reading model for storing sensor data"""
    __tablename__ = "health_readings"
    
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String, ForeignKey("users.id"), nullable=False)
    timestamp = Column(BigInteger, nullable=False)  # Unix timestamp in milliseconds
    
    # Vital signs
    heart_rate = Column(Integer, nullable=True)  # BPM
    systolic_bp = Column(Integer, nullable=True)  # mmHg
    diastolic_bp = Column(Integer, nullable=True)  # mmHg
    spo2 = Column(Integer, nullable=True)  # Percentage
    temperature = Column(Float, nullable=True)  # Celsius
    
    # Raw sensor data (stored as JSON arrays)
    ecg_data = Column(JSON, nullable=True)  # List of float values
    ppg_data = Column(JSON, nullable=True)  # List of float values
    
    # Calculated values
    ptt = Column(Float, nullable=True)  # Pulse Transit Time in ms
    
    # Quality indicators
    quality_score = Column(Float, nullable=True)  # 0-100 quality score
    
    # Relationships
    user = relationship("User", back_populates="health_readings")

