"""
Reports Router - Weekly and Monthly Reports
"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.user import User
from app.schemas.reports import (
    WeeklyReportResponse, 
    WeeklyReportDto, 
    DailySummaryDto
)
from app.utils.security import get_current_user
from app.utils.mock_data import generate_weekly_report
from app.config import settings

router = APIRouter()


@router.get("/weekly", response_model=WeeklyReportResponse)
async def get_weekly_report(
    week_offset: int = 0,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Haftalık sağlık raporunu getir
    
    - week_offset: 0 = bu hafta, 1 = geçen hafta, vb.
    """
    if settings.use_mock_data:
        mock_report = generate_weekly_report(current_user.id, week_offset)
        return WeeklyReportResponse(
            success=True,
            report=WeeklyReportDto(
                week_start=mock_report["week_start"],
                week_end=mock_report["week_end"],
                avg_heart_rate=mock_report["avg_heart_rate"],
                avg_systolic=mock_report["avg_systolic"],
                avg_diastolic=mock_report["avg_diastolic"],
                avg_spo2=mock_report["avg_spo2"],
                readings_count=mock_report["readings_count"],
                health_score=mock_report["health_score"],
                recommendations=mock_report["recommendations"],
                daily_summaries=[
                    DailySummaryDto(**ds) for ds in mock_report["daily_summaries"]
                ]
            )
        )
    
    # TODO: Implement actual report generation from database
    return WeeklyReportResponse(
        success=False,
        message="Rapor oluşturmak için yeterli veri yok"
    )


@router.get("/monthly", response_model=WeeklyReportResponse)
async def get_monthly_report(
    month_offset: int = 0,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Aylık sağlık raporunu getir
    
    - month_offset: 0 = bu ay, 1 = geçen ay, vb.
    """
    # For now, return a 4-week report as monthly
    if settings.use_mock_data:
        # Generate 4 weeks of data
        all_daily_summaries = []
        total_hr = 0
        total_sys = 0
        total_dia = 0
        total_spo2 = 0
        total_readings = 0
        
        for week in range(4):
            week_report = generate_weekly_report(
                current_user.id, 
                week_offset=month_offset * 4 + week
            )
            all_daily_summaries.extend(week_report["daily_summaries"])
            total_hr += week_report["avg_heart_rate"] * week_report["readings_count"]
            total_sys += week_report["avg_systolic"] * week_report["readings_count"]
            total_dia += week_report["avg_diastolic"] * week_report["readings_count"]
            total_spo2 += week_report["avg_spo2"] * week_report["readings_count"]
            total_readings += week_report["readings_count"]
        
        return WeeklyReportResponse(
            success=True,
            report=WeeklyReportDto(
                week_start=all_daily_summaries[-1]["date"] if all_daily_summaries else "",
                week_end=all_daily_summaries[0]["date"] if all_daily_summaries else "",
                avg_heart_rate=round(total_hr / total_readings, 1) if total_readings else 0,
                avg_systolic=round(total_sys / total_readings, 1) if total_readings else 0,
                avg_diastolic=round(total_dia / total_readings, 1) if total_readings else 0,
                avg_spo2=round(total_spo2 / total_readings, 1) if total_readings else 0,
                readings_count=total_readings,
                health_score=82,
                recommendations=[
                    "Aylık sağlık verileriniz stabil görünüyor",
                    "Düzenli ölçüm yapmaya devam edin"
                ],
                daily_summaries=[DailySummaryDto(**ds) for ds in all_daily_summaries[:14]]
            )
        )
    
    return WeeklyReportResponse(
        success=False,
        message="Aylık rapor için yeterli veri yok"
    )

