"""
 ╔══════════════════════════════════════════════════════════════╗
 ║                  BPSync — Trends Router                      ║
 ╠══════════════════════════════════════════════════════════════╣
 ║  Provides time-series BP & HR metrics from TimescaleDB       ║
 ║  Endpoints: /trends                                          ║
 ╚══════════════════════════════════════════════════════════════╝
"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import text
from typing import List, Optional
from pydantic import BaseModel

from backend.database import get_sensor_db
from backend.models.user import User
from backend.utils.security import get_current_user
from backend.utils.sensor_identity import sensor_user_clause, sensor_user_params

router = APIRouter()


# ══════════════════════════════════════════════════════════════
#  RESPONSE MODELS
# ══════════════════════════════════════════════════════════════

class TrendDataPoint(BaseModel):
    """A single {timestamp, value} pair in a trend series"""
    timestamp: int    # Unix milliseconds — matches Android Long
    value:     float


class TrendDataDto(BaseModel):
    """
    One metric's complete trend dataset.
    'type' is the metric name (e.g. 'systolic') so the mobile can label
    each chart axis correctly without hard-coding series order.
    """
    type:        str
    data_points: List[TrendDataPoint]
    average:     float   # Pre-computed so mobile avoids client-side aggregation
    min:         float
    max:         float


class TrendResponse(BaseModel):
    success: bool
    trends:  List[TrendDataDto] = []
    message: Optional[str] = None


# ══════════════════════════════════════════════════════════════
#  CONFIGURATION & CONSTANTS
# ══════════════════════════════════════════════════════════════
PERIOD_BACK = {
    'day':   '1 day',
    'week':  '7 days',
    'month': '30 days',
}

PERIOD_BUCKET_EXPR = {
    # PostgreSQL-compatible bucket expressions (no Timescale-only functions).
    'day':   "date_trunc('hour', time)",
    'week':  "date_trunc('hour', time) - ((EXTRACT(hour FROM time)::int % 6) * INTERVAL '1 hour')",
    'month': "date_trunc('day', time)",
}


# ══════════════════════════════════════════════════════════════
#  ENDPOINTS
# ══════════════════════════════════════════════════════════════
@router.get('', response_model=TrendResponse)
async def get_trends(
    period: str = 'week',
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    """
    Return systolic, diastolic, and heart-rate trend data for the requested period.
    Aggregates points into time buckets (hourly, 6-hourly, daily) via TimescaleDB,
    and returns absolute min/max/average summaries across the full period.

    Falls back to mock data when no rows exist and USE_MOCK_DATA=true.
    """
    back_interval = PERIOD_BACK.get(period, '7 days')
    bucket_expr = PERIOD_BUCKET_EXPR.get(period, PERIOD_BUCKET_EXPR['week'])

    # 1. Fetch total summary (stats across the entire requested period) for the top boxes
    summary_row = db.execute(
        text(f'''
            SELECT 
                AVG(systolic) AS avg_sys, MAX(systolic) AS max_sys, MIN(systolic) AS min_sys,
                AVG(diastolic) AS avg_dia, MAX(diastolic) AS max_dia, MIN(diastolic) AS min_dia,
                AVG(heart_rate) AS avg_hr, MAX(heart_rate) AS max_hr, MIN(heart_rate) AS min_hr
            FROM bp_readings
            WHERE {sensor_user_clause()}
              AND time >= NOW() - CAST(:back AS interval)
        '''),
        {
            **sensor_user_params(current_user),
            'back': back_interval,
        },
    ).fetchone()


    if not summary_row or summary_row.avg_sys is None:
        return TrendResponse(success=True, trends=[], message='No data for this period.')


    # 2. Fetch time-bucketed chart data points
    bucket_rows = db.execute(
        text(f'''
            SELECT 
                {bucket_expr} AS bucket_time,
                AVG(systolic) AS systolic,
                AVG(diastolic) AS diastolic,
                AVG(heart_rate) AS heart_rate
            FROM bp_readings
            WHERE {sensor_user_clause()}
              AND time >= NOW() - CAST(:back AS interval)
            GROUP BY bucket_time
            ORDER BY bucket_time ASC
        '''),
        {
            **sensor_user_params(current_user),
            'back': back_interval,
        },
    ).fetchall()

    points_sys, points_dia, points_hr = [], [], []
    for row in bucket_rows:
        ts = int(row.bucket_time.timestamp() * 1000)
        if row.systolic is not None:
            points_sys.append(TrendDataPoint(timestamp=ts, value=round(float(row.systolic), 2)))
        if row.diastolic is not None:
            points_dia.append(TrendDataPoint(timestamp=ts, value=round(float(row.diastolic), 2)))
        if row.heart_rate is not None:
            points_hr.append(TrendDataPoint(timestamp=ts, value=round(float(row.heart_rate), 2)))

    trends = [
        TrendDataDto(
            type="systolic",
            data_points=points_sys,
            average=round(float(summary_row.avg_sys), 2),
            min=round(float(summary_row.min_sys), 2),
            max=round(float(summary_row.max_sys), 2),
        ),
        TrendDataDto(
            type="diastolic",
            data_points=points_dia,
            average=round(float(summary_row.avg_dia), 2),
            min=round(float(summary_row.min_dia), 2),
            max=round(float(summary_row.max_dia), 2),
        ),
        TrendDataDto(
            type="heart_rate",
            data_points=points_hr,
            average=round(float(summary_row.avg_hr), 2),
            min=round(float(summary_row.min_hr), 2),
            max=round(float(summary_row.max_hr), 2),
        ),
        TrendDataDto(
            type="spo2",
            data_points=[],
            average=0.0,
            min=0.0,
            max=0.0,
        )
    ]

    return TrendResponse(success=True, trends=trends)
