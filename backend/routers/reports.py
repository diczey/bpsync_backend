"""
Reports Router - Weekly BP Summary from TimescaleDB
"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import text
from typing import List, Optional
from pydantic import BaseModel

from backend.database import get_sensor_db
from backend.models.user import User
from backend.utils.security import get_current_user

router = APIRouter()


class DailyBP(BaseModel):
    date: str
    avg_systolic: Optional[float] = None
    avg_diastolic: Optional[float] = None
    avg_heart_rate: Optional[float] = None
    reading_count: int


class WeeklyReport(BaseModel):
    week_start: str
    week_end: str
    avg_systolic: Optional[float] = None
    avg_diastolic: Optional[float] = None
    avg_heart_rate: Optional[float] = None
    readings_count: int
    health_score: int
    daily_summaries: List[DailyBP] = []


class WeeklyReportResponse(BaseModel):
    success: bool
    report: Optional[WeeklyReport] = None
    message: Optional[str] = None


def _health_score(avg_systolic, avg_diastolic):
    '''Simple score 0-100 based on average BP category.'''
    if avg_systolic is None:
        return 0
    if avg_systolic < 120 and avg_diastolic < 80:
        return 100
    elif avg_systolic < 130:
        return 85
    elif avg_systolic < 140:
        return 65
    elif avg_systolic < 180:
        return 40
    return 20


@router.get('/weekly', response_model=WeeklyReportResponse)
async def get_weekly_report(
    week_offset: int = 0,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    '''
    Weekly BP report. week_offset=0 is current week, 1 is last week, etc.
    Reads from TimescaleDB bp_readings.
    '''
    # Daily aggregation for the selected week
    rows = db.execute(
        text('''
            SELECT
                time_bucket('1 day', time) AS day,
                ROUND(AVG(systolic)::numeric, 1)   AS avg_sys,
                ROUND(AVG(diastolic)::numeric, 1)  AS avg_dia,
                ROUND(AVG(heart_rate)::numeric, 1) AS avg_hr,
                COUNT(*) AS cnt
            FROM bp_readings
            WHERE user_id = :uid
              AND time >= NOW() - INTERVAL :start_back
              AND time <  NOW() - INTERVAL :end_back
            GROUP BY day
            ORDER BY day ASC
        '''),
        {
            'uid': current_user.id,
            'start_back': '{} days'.format((week_offset + 1) * 7),
            'end_back':   '{} days'.format(week_offset * 7),
        },
    ).fetchall()

    if not rows:
        return WeeklyReportResponse(success=False, message='No data for this week.')

    total_sys = sum(r.avg_sys or 0 for r in rows)
    total_dia = sum(r.avg_dia or 0 for r in rows)
    total_hr  = sum(r.avg_hr  or 0 for r in rows)
    total_cnt = sum(r.cnt for r in rows)
    n = len(rows)

    avg_sys = round(total_sys / n, 1)
    avg_dia = round(total_dia / n, 1)
    avg_hr  = round(total_hr  / n, 1)

    return WeeklyReportResponse(
        success=True,
        report=WeeklyReport(
            week_start=str(rows[0].day),
            week_end=str(rows[-1].day),
            avg_systolic=avg_sys,
            avg_diastolic=avg_dia,
            avg_heart_rate=avg_hr,
            readings_count=total_cnt,
            health_score=_health_score(avg_sys, avg_dia),
            daily_summaries=[
                DailyBP(
                    date=str(r.day),
                    avg_systolic=r.avg_sys,
                    avg_diastolic=r.avg_dia,
                    avg_heart_rate=r.avg_hr,
                    reading_count=r.cnt,
                )
                for r in rows
            ],
        ),
    )


@router.get('/monthly', response_model=WeeklyReportResponse)
async def get_monthly_report(
    month_offset: int = 0,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    '''30-day BP report with daily aggregation.'''
    rows = db.execute(
        text('''
            SELECT
                time_bucket('1 day', time) AS day,
                ROUND(AVG(systolic)::numeric, 1)   AS avg_sys,
                ROUND(AVG(diastolic)::numeric, 1)  AS avg_dia,
                ROUND(AVG(heart_rate)::numeric, 1) AS avg_hr,
                COUNT(*) AS cnt
            FROM bp_readings
            WHERE user_id = :uid
              AND time >= NOW() - INTERVAL :start_back
              AND time <  NOW() - INTERVAL :end_back
            GROUP BY day
            ORDER BY day ASC
        '''),
        {
            'uid': current_user.id,
            'start_back': '{} days'.format((month_offset + 1) * 30),
            'end_back':   '{} days'.format(month_offset * 30),
        },
    ).fetchall()

    if not rows:
        return WeeklyReportResponse(success=False, message='No data for this month.')

    total_sys = sum(r.avg_sys or 0 for r in rows)
    total_dia = sum(r.avg_dia or 0 for r in rows)
    total_hr  = sum(r.avg_hr  or 0 for r in rows)
    total_cnt = sum(r.cnt for r in rows)
    n = len(rows)

    avg_sys = round(total_sys / n, 1)
    avg_dia = round(total_dia / n, 1)
    avg_hr  = round(total_hr  / n, 1)

    return WeeklyReportResponse(
        success=True,
        report=WeeklyReport(
            week_start=str(rows[0].day),
            week_end=str(rows[-1].day),
            avg_systolic=avg_sys,
            avg_diastolic=avg_dia,
            avg_heart_rate=avg_hr,
            readings_count=total_cnt,
            health_score=_health_score(avg_sys, avg_dia),
            daily_summaries=[
                DailyBP(
                    date=str(r.day),
                    avg_systolic=r.avg_sys,
                    avg_diastolic=r.avg_dia,
                    avg_heart_rate=r.avg_hr,
                    reading_count=r.cnt,
                )
                for r in rows
            ],
        ),
    )
