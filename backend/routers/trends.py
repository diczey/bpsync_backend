"""
Trends Router - Trend Analysis Data
"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from typing import Optional

from backend.app.database import get_db
from backend.app.models.user import User
from backend.app.schemas.trends import TrendResponse, TrendDataDto, TrendDataPoint
from backend.app.utils.security import get_current_user
from backend.app.utils.mock_data import generate_trend_data
from backend.app.config import settings

router = APIRouter()


@router.get("", response_model=TrendResponse)
async def get_trend_data(
    type: str = "heart_rate",
    period: str = "week",
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Trend verilerini getir
    
    - type: heart_rate, systolic, diastolic, spo2, temperature
    - period: day, week, month
    """
    # Map period to days
    period_days = {
        "day": 1,
        "week": 7,
        "month": 30
    }
    days = period_days.get(period, 7)
    
    if settings.use_mock_data:
        mock_trend = generate_trend_data(type, days=days)
        return TrendResponse(
            success=True,
            trends=[TrendDataDto(
                type=mock_trend["type"],
                data_points=[TrendDataPoint(**dp) for dp in mock_trend["data_points"]],
                average=mock_trend["average"],
                min=mock_trend["min"],
                max=mock_trend["max"]
            )]
        )
    
    # TODO: Implement actual trend calculation from database
    return TrendResponse(
        success=True,
        trends=[],
        message="Trend verisi bulunamadı"
    )


@router.get("/ecg", response_model=TrendResponse)
async def get_ecg_trends(
    start_date: Optional[int] = None,
    end_date: Optional[int] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    ECG trend verilerini getir
    """
    if settings.use_mock_data:
        mock_trend = generate_trend_data("ecg", days=7)
        return TrendResponse(
            success=True,
            trends=[TrendDataDto(
                type="ecg",
                data_points=[TrendDataPoint(**dp) for dp in mock_trend["data_points"]],
                average=mock_trend["average"],
                min=mock_trend["min"],
                max=mock_trend["max"]
            )]
        )
    
    return TrendResponse(
        success=True,
        trends=[],
        message="ECG trend verisi bulunamadı"
    )


@router.get("/ppg", response_model=TrendResponse)
async def get_ppg_trends(
    start_date: Optional[int] = None,
    end_date: Optional[int] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    PPG trend verilerini getir
    """
    if settings.use_mock_data:
        mock_trend = generate_trend_data("ppg", days=7)
        return TrendResponse(
            success=True,
            trends=[TrendDataDto(
                type="ppg",
                data_points=[TrendDataPoint(**dp) for dp in mock_trend["data_points"]],
                average=mock_trend["average"],
                min=mock_trend["min"],
                max=mock_trend["max"]
            )]
        )
    
    return TrendResponse(
        success=True,
        trends=[],
        message="PPG trend verisi bulunamadı"
    )

