"""
User Database Model
"""
from sqlalchemy import Column, String, DateTime, Boolean
from sqlalchemy.orm import relationship
from datetime import datetime
import uuid

from backend.database import Base


class User(Base):
    """User model for authentication and profile"""
    __tablename__ = "users"
    
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    email = Column(String, unique=True, index=True, nullable=False)
    hashed_password = Column(String, nullable=False)
    name = Column(String, nullable=False)
    avatar_url = Column(String, nullable=True)
    date_of_birth = Column(String, nullable=True)
    gender = Column(String, nullable=True)
    weight = Column(String, nullable=True)  # Stored as string or float depending on preference, string matches others
    height = Column(String, nullable=True)
    blood_type = Column(String, nullable=True)
    emergency_contact = Column(String, nullable=True)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    @property
    def age(self) -> int:
        """Calculate age from date_of_birth string (YYYY-MM-DD). Default to 40 if not set."""
        if not self.date_of_birth:
            return 40
        try:
            # Assuming 'YYYY-MM-DD' format, take the first 4 chars
            birth_year = int(self.date_of_birth.split('-')[0])
            current_year = datetime.utcnow().year
            calculated_age = current_year - birth_year
            return max(18, min(120, calculated_age))  # Keep it in reasonable bounds
        except (ValueError, TypeError, IndexError):
            return 40
            
    # Relationships
    notifications = relationship("Notification", back_populates="user")

