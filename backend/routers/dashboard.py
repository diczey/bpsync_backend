"""
Dashboard Router - Latest BP Summary from TimescaleDB
"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import text
from typing import Optional
from pydantic import BaseModel

from backend.database import get_sensor_db
from backend.models.user import User
from backend.utils.security import get_current_user

router = APIRouter()


class DashboardSummary(BaseModel):
    latest_systolic: int
    latest_diastolic: int
    latest_heart_rate: int
    latest_ptt: Optional[float] = None
    latest_quality: Optional[int] = None
    health_status: str
    category: str
    last_updated: str


class DashboardResponse(BaseModel):
    success: bool
    summary: Optional[DashboardSummary] = None
    message: Optional[str] = None


@router.get('/summary', response_model=DashboardResponse)
async def get_dashboard_summary(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    '''Latest BP reading for the current user. Used by mobile home screen.'''
    row = db.execute(
        text('''
            SELECT time, systolic, diastolic, heart_rate, ptt, quality, category
            FROM bp_readings
            WHERE user_id = :uid
            ORDER BY time DESC
            LIMIT 1
        '''),
        {'uid': current_user.id},
    ).fetchone()

    if not row:
        return DashboardResponse(success=False, message='No readings yet. Start a measurement.')

    health_status = 'NORMAL'
    if row.systolic and row.systolic > 140:
        health_status = 'HIGH'
    elif row.systolic and row.systolic < 90:
        health_status = 'LOW'

    return DashboardResponse(
        success=True,
        summary=DashboardSummary(
            latest_systolic=row.systolic or 0,
            latest_diastolic=row.diastolic or 0,
            latest_heart_rate=row.heart_rate or 0,
            latest_ptt=row.ptt,
            latest_quality=row.quality,
            health_status=health_status,
            category=row.category or 'Unknown',
            last_updated=str(row.time),
        ),
    )
