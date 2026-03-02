"""
Notification Database Model
"""
from sqlalchemy import Column, String, BigInteger, Boolean, ForeignKey
from sqlalchemy.orm import relationship
import uuid

from backend.app.database import Base


class Notification(Base):
    """Notification model for user alerts"""
    __tablename__ = "notifications"
    
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String, ForeignKey("users.id"), nullable=False)
    title = Column(String, nullable=False)
    message = Column(String, nullable=False)
    type = Column(String, nullable=False)  # "alert", "reminder", "achievement", "info"
    timestamp = Column(BigInteger, nullable=False)  # Unix timestamp in milliseconds
    is_read = Column(Boolean, default=False)
    
    # Relationships
    user = relationship("User", back_populates="notifications")

