"""
Trends Router - BP Trend Analysis from TimescaleDB
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


class TrendPoint(BaseModel):
    bucket: str
    avg_systolic: Optional[float] = None
    avg_diastolic: Optional[float] = None
    avg_heart_rate: Optional[float] = None
    count: int


class TrendResponse(BaseModel):
    success: bool
    period: str
    points: List[TrendPoint] = []
    message: Optional[str] = None


PERIOD_INTERVAL = {
    'day':   ('1 hour',  '1 day'),
    'week':  ('1 day',   '7 days'),
    'month': ('1 day',  '30 days'),
}


@router.get('', response_model=TrendResponse)
async def get_trends(
    period: str = 'week',
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    '''
    Aggregated BP trends per time bucket.
    period: day (hourly buckets), week (daily), month (daily last 30 days)
    '''
    bucket_interval, back_interval = PERIOD_INTERVAL.get(period, ('1 day', '7 days'))

    rows = db.execute(
        text('''
            SELECT
                time_bucket(:bucket, time) AS bucket,
                ROUND(AVG(systolic)::numeric, 1)    AS avg_systolic,
                ROUND(AVG(diastolic)::numeric, 1)   AS avg_diastolic,
                ROUND(AVG(heart_rate)::numeric, 1)  AS avg_heart_rate,
                COUNT(*)                            AS count
            FROM bp_readings
            WHERE user_id = :uid
              AND time >= NOW() - INTERVAL :back
            GROUP BY bucket
            ORDER BY bucket ASC
        '''),
        {'uid': current_user.id, 'bucket': bucket_interval, 'back': back_interval},
    ).fetchall()

    if not rows:
        from backend.config import settings
        if settings.use_mock_data:
            from backend.utils.mock_data import generate_trends
            mock_points = generate_trends(period=period)
            return TrendResponse(
                success=True,
                period=period,
                points=[
                    TrendPoint(
                        bucket=p['bucket'],
                        avg_systolic=p['avg_systolic'],
                        avg_diastolic=p['avg_diastolic'],
                        avg_heart_rate=p['avg_heart_rate'],
                        count=p['count']
                    ) for p in mock_points
                ],
                message="Mock trends provided for testing."
            )
        return TrendResponse(success=True, period=period, message='No data for this period.')

    return TrendResponse(
        success=True,
        period=period,
        points=[
            TrendPoint(
                bucket=str(r.bucket),
                avg_systolic=r.avg_systolic,
                avg_diastolic=r.avg_diastolic,
                avg_heart_rate=r.avg_heart_rate,
                count=r.count,
            )
            for r in rows
        ],
    )
