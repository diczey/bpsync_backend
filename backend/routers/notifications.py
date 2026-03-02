"""
Notifications Router - User Notifications Management
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import Optional

from backend.app.database import get_db
from backend.app.models.user import User
from backend.app.models.notification import Notification
from backend.app.schemas.notifications import NotificationsResponse, NotificationDto
from backend.app.utils.security import get_current_user
from backend.app.utils.mock_data import generate_notifications
from backend.app.config import settings

router = APIRouter()


@router.get("", response_model=NotificationsResponse)
async def get_notifications(
    limit: Optional[int] = 20,
    unread_only: bool = False,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Bildirimleri listele
    
    - limit: Maksimum bildirim sayısı
    - unread_only: Sadece okunmamış bildirimler
    """
    if settings.use_mock_data:
        mock_notifications = generate_notifications(current_user.id, count=limit or 20)
        
        if unread_only:
            mock_notifications = [n for n in mock_notifications if not n["is_read"]]
        
        unread_count = sum(1 for n in mock_notifications if not n["is_read"])
        
        return NotificationsResponse(
            success=True,
            notifications=[NotificationDto(**n) for n in mock_notifications],
            unread_count=unread_count
        )
    
    # Build query
    query = db.query(Notification).filter(Notification.user_id == current_user.id)
    
    if unread_only:
        query = query.filter(Notification.is_read == False)
    
    query = query.order_by(Notification.timestamp.desc())
    
    if limit:
        query = query.limit(limit)
    
    notifications = query.all()
    
    # Count unread
    unread_count = db.query(Notification).filter(
        Notification.user_id == current_user.id,
        Notification.is_read == False
    ).count()
    
    return NotificationsResponse(
        success=True,
        notifications=[NotificationDto(
            id=n.id,
            title=n.title,
            message=n.message,
            type=n.type,
            timestamp=n.timestamp,
            is_read=n.is_read
        ) for n in notifications],
        unread_count=unread_count
    )


@router.put("/{notification_id}/read")
async def mark_notification_as_read(
    notification_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Bildirimi okundu olarak işaretle
    """
    if settings.use_mock_data:
        return {"success": True, "message": "Bildirim okundu olarak işaretlendi"}
    
    notification = db.query(Notification).filter(
        Notification.id == notification_id,
        Notification.user_id == current_user.id
    ).first()
    
    if not notification:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Bildirim bulunamadı"
        )
    
    notification.is_read = True
    db.commit()
    
    return {"success": True, "message": "Bildirim okundu olarak işaretlendi"}


@router.put("/read-all")
async def mark_all_notifications_as_read(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Tüm bildirimleri okundu olarak işaretle
    """
    if settings.use_mock_data:
        return {"success": True, "message": "Tüm bildirimler okundu olarak işaretlendi"}
    
    db.query(Notification).filter(
        Notification.user_id == current_user.id,
        Notification.is_read == False
    ).update({"is_read": True})
    db.commit()
    
    return {"success": True, "message": "Tüm bildirimler okundu olarak işaretlendi"}


@router.delete("/{notification_id}")
async def delete_notification(
    notification_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Bildirimi sil
    """
    if settings.use_mock_data:
        return {"success": True, "message": "Bildirim silindi"}
    
    notification = db.query(Notification).filter(
        Notification.id == notification_id,
        Notification.user_id == current_user.id
    ).first()
    
    if not notification:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Bildirim bulunamadı"
        )
    
    db.delete(notification)
    db.commit()
    
    return {"success": True, "message": "Bildirim silindi"}

