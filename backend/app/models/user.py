"""
User Database Model
"""
from sqlalchemy import Column, String, DateTime, Boolean
from sqlalchemy.orm import relationship
from datetime import datetime
import uuid

from backend.app.database import Base


class User(Base):
    """User model for authentication and profile"""
    __tablename__ = "users"
    
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    email = Column(String, unique=True, index=True, nullable=False)
    hashed_password = Column(String, nullable=False)
    name = Column(String, nullable=False)
    avatar_url = Column(String, nullable=True)
    date_of_birth = Column(String, nullable=True)
    blood_type = Column(String, nullable=True)
    emergency_contact = Column(String, nullable=True)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    # Relationships
    health_readings = relationship("HealthReading", back_populates="user")
    notifications = relationship("Notification", back_populates="user")

