"""
Notification Schemas
"""
from pydantic import BaseModel, Field
from typing import Optional, List


class NotificationDto(BaseModel):
    """Notification data transfer object"""
    id: str
    title: str
    message: str
    type: str  # "alert", "reminder", "achievement", "info"
    timestamp: int
    is_read: bool = Field(..., serialization_alias="is_read")
    
    class Config:
        from_attributes = True
        populate_by_name = True


class NotificationsResponse(BaseModel):
    """Notifications list response"""
    success: bool
    notifications: List[NotificationDto]
    unread_count: int = Field(..., serialization_alias="unread_count")
    message: Optional[str] = None

