"""
Dashboard Router - Dashboard Summary
"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from backend.app.database import get_db
from backend.app.models.user import User
from backend.app.models.health_reading import HealthReading
from backend.app.schemas.dashboard import DashboardResponse, DashboardSummaryDto
from backend.app.utils.security import get_current_user
from backend.app.utils.mock_data import generate_dashboard_summary
from backend.app.config import settings

router = APIRouter()


@router.get("/summary", response_model=DashboardResponse)
async def get_dashboard_summary(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Dashboard özet verilerini getir
    
    - En son sağlık metrikleri
    - Genel sağlık durumu
    """
    if settings.use_mock_data:
        # Use mock data for development
        mock_summary = generate_dashboard_summary(current_user.id)
        return DashboardResponse(
            success=True,
            summary=DashboardSummaryDto(**mock_summary)
        )
    
    # Get latest reading from database
    latest_reading = db.query(HealthReading).filter(
        HealthReading.user_id == current_user.id
    ).order_by(HealthReading.timestamp.desc()).first()
    
    if not latest_reading:
        return DashboardResponse(
            success=False,
            message="Henüz sağlık verisi bulunmuyor"
        )
    
    # Determine health status based on readings
    health_status = "NORMAL"
    if latest_reading.systolic_bp and latest_reading.systolic_bp > 140:
        health_status = "HIGH"
    elif latest_reading.systolic_bp and latest_reading.systolic_bp < 90:
        health_status = "LOW"
    elif latest_reading.spo2 and latest_reading.spo2 < 95:
        health_status = "ELEVATED"
    
    return DashboardResponse(
        success=True,
        summary=DashboardSummaryDto(
            latest_heart_rate=latest_reading.heart_rate or 0,
            latest_systolic=latest_reading.systolic_bp or 0,
            latest_diastolic=latest_reading.diastolic_bp or 0,
            latest_spo2=latest_reading.spo2 or 0,
            latest_temperature=latest_reading.temperature or 0.0,
            health_status=health_status,
            last_updated=latest_reading.timestamp
        )
    )

