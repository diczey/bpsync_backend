"""
 ╔══════════════════════════════════════════════════════════════╗
 ║                BPSync — Dashboard Router                     ║
 ╠══════════════════════════════════════════════════════════════╣
 ║  Provides the home-screen summary card for the mobile app    ║
 ║  Endpoints: /summary, /health-status, /pulse, /ppg/signal    ║
 ╚══════════════════════════════════════════════════════════════╝
"""
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import text
from typing import Optional, List
from pydantic import BaseModel

from backend.database import get_sensor_db
from backend.models.user import User
from backend.utils.security import get_current_user
from backend.utils.sensor_identity import sensor_user_clause, sensor_user_params

router = APIRouter()


# ══════════════════════════════════════════════════════════════
#  RESPONSE MODELS
# ══════════════════════════════════════════════════════════════
# Field names mirror Android DTOs exactly for Gson compatibility.

class DashboardSummary(BaseModel):
    """Mirrors DashboardSummaryDto in Android Models.kt"""
    latest_systolic:    int
    latest_diastolic:   int
    latest_heart_rate:  int
    latest_spo2:        int    # Oxygen saturation percentage (95-100 normal)
    latest_temperature: float  # Body temperature in Celsius from wristband_data
    health_status:      str    # "NORMAL", "HIGH", "LOW", "ELEVATED"
    last_updated:       int    # Unix timestamp in milliseconds (matches Android Long)


class DashboardResponse(BaseModel):
    success:  bool
    summary:  Optional[DashboardSummary] = None
    message:  Optional[str] = None


# ══════════════════════════════════════════════════════════════
#  HELPERS
# ══════════════════════════════════════════════════════════════
# Helper: determine textual health status from BP values
def _classify_bp(systolic: int, spo2: int, heart_rate: int) -> str:
    """
    Classify overall health status based on blood-pressure category.

    Rules (simplified JNC-8 / AHA 2017):
      systolic > 140  → HIGH
      systolic < 90   → LOW
      SpO2 < 95 or HR > 100 → ELEVATED
      else            → NORMAL
    """
    if systolic > 140:
        return "HIGH"
    if systolic < 90:
        return "LOW"
    if spo2 < 95 or heart_rate > 100:
        return "ELEVATED"
    return "NORMAL"


# ══════════════════════════════════════════════════════════════
#  ENDPOINTS
# ══════════════════════════════════════════════════════════════
@router.get('/summary', response_model=DashboardResponse)
async def get_dashboard_summary(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    """
    Return the latest health reading for the logged-in user.

    Data sources (TimescaleDB):
      - bp_readings    → systolic, diastolic, heart_rate
      - wristband_data → temperature (most recent frame)

    SpO2 is not stored in bp_readings; it is provided as a constant 98
    until the data pipeline includes a dedicated SpO2 inference step.

    Falls back to mock data when no rows exist and USE_MOCK_DATA=true.
    """
    # Fetch the most-recent BP reading for this user.
    # Accept both UUID (current format) and email (legacy format) as user_id
    bp_row = db.execute(
        text(f'''
            SELECT time, systolic, diastolic, heart_rate
            FROM bp_readings
            WHERE {sensor_user_clause()}
            ORDER BY time DESC
            LIMIT 1
        '''),
        sensor_user_params(current_user),
    ).fetchone()

    if not bp_row:
        return DashboardResponse(success=False, message='No readings yet. Connect your BPSync wristband to start measuring.')


    # Fetch the most-recent temperature from wristband raw frames.
    # wristband_data is written at 10 Hz so this is nearly real-time.
    temp_row = db.execute(
        text(f'''
            SELECT temperature
            FROM wristband_data
            WHERE {sensor_user_clause()}
              AND temperature IS NOT NULL
            ORDER BY time DESC
            LIMIT 1
        '''),
        sensor_user_params(current_user),
    ).fetchone()

    # SpO2: not yet derived from ppg_ir/ppg_red in the pipeline.
    # Use 98 as a physiologically safe placeholder until inference is added.
    spo2 = 98

    temperature = float(temp_row.temperature) if temp_row else 36.6

    systolic   = bp_row.systolic    or 0
    diastolic  = bp_row.diastolic   or 0
    heart_rate = bp_row.heart_rate  or 0

    # Convert DB timestamp to Unix milliseconds so Android can parse it as Long
    last_updated_ms = int(bp_row.time.timestamp() * 1000)

    return DashboardResponse(
        success=True,
        summary=DashboardSummary(
            latest_systolic=systolic,
            latest_diastolic=diastolic,
            latest_heart_rate=heart_rate,
            latest_spo2=spo2,
            latest_temperature=temperature,
            health_status=_classify_bp(systolic, spo2, heart_rate),
            last_updated=last_updated_ms,
        ),
    )


class HealthStatusResponse(BaseModel):
    success: bool
    health_score: int
    overall_status: str
    blood_pressure_status: str
    heart_rate_status: str
    oxygen_status: str
    calibration_started_at: Optional[int] = None
    calibration_ready_at: Optional[int] = None
    weekly_status_ready_at: Optional[int] = None
    seconds_until_calibrated: int = 0
    seconds_until_weekly_status: int = 0
    tracking_day: int = 0
    is_calibrated: bool = False
    is_week_ready: bool = False
    countdown_phase: str = "awaiting_device"
    status_mode: str = "standard"
    message: Optional[str] = None


def _utc(dt_value: Optional[datetime]) -> Optional[datetime]:
    if dt_value is None:
        return None
    if dt_value.tzinfo is None:
        return dt_value.replace(tzinfo=timezone.utc)
    return dt_value.astimezone(timezone.utc)


def _to_ms(dt_value: Optional[datetime]) -> Optional[int]:
    utc_value = _utc(dt_value)
    return int(utc_value.timestamp() * 1000) if utc_value else None


def _classify_standard_bp(systolic: Optional[int], diastolic: Optional[int]) -> str:
    if not systolic or not diastolic:
        return "No Data"
    if systolic < 90 or diastolic < 60:
        return "Low"
    if systolic >= 140 or diastolic >= 90:
        return "High"
    if systolic >= 120 or diastolic >= 80:
        return "Elevated"
    return "Normal"


def _classify_personalized_bp(
    systolic: Optional[int],
    diastolic: Optional[int],
    baseline_sys: Optional[float],
    baseline_dia: Optional[float],
) -> str:
    if not systolic or not diastolic or baseline_sys is None or baseline_dia is None:
        return _classify_standard_bp(systolic, diastolic)

    sys_delta = systolic - baseline_sys
    dia_delta = diastolic - baseline_dia

    if sys_delta >= 12 or dia_delta >= 8:
        return "High"
    if sys_delta <= -12 or dia_delta <= -8:
        return "Low"
    if sys_delta >= 6 or dia_delta >= 4:
        return "Elevated"
    return "Normal"


def _classify_standard_hr(heart_rate: Optional[int]) -> str:
    if not heart_rate:
        return "No Data"
    if heart_rate < 60:
        return "Low"
    if heart_rate > 100:
        return "High"
    return "Normal"


def _classify_personalized_hr(heart_rate: Optional[int], baseline_hr: Optional[float]) -> str:
    if not heart_rate or baseline_hr is None:
        return _classify_standard_hr(heart_rate)

    delta = heart_rate - baseline_hr
    if delta >= 12:
        return "High"
    if delta <= -12:
        return "Low"
    return "Normal"


@router.get('/health-status', response_model=HealthStatusResponse)
async def get_health_status(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    """
    Return a BLE-driven health status lifecycle.

    Phase 1: first 3 days after the first successful BLE connection are used as
    calibration days.
    Phase 2: days 4-6 use the 3-day personalized baseline for daily labels.
    Phase 3: from day 7 onward, a weekly health score is also returned.
    """
    calibration_started_at = _utc(current_user.ble_calibration_started_at)
    if calibration_started_at is None:
        return HealthStatusResponse(
            success=False,
            health_score=0,
            overall_status="No Data",
            blood_pressure_status="No Data",
            heart_rate_status="No Data",
            oxygen_status="No Data",
            countdown_phase="awaiting_device",
            status_mode="standard",
            message="Connect your wristband to start the 3-day calibration countdown.",
        )

    now = datetime.now(timezone.utc)
    calibration_ready_at = calibration_started_at + timedelta(days=3)
    weekly_ready_at = calibration_started_at + timedelta(days=7)
    is_calibrated = now >= calibration_ready_at
    is_week_ready = now >= weekly_ready_at
    seconds_until_calibrated = max(0, int((calibration_ready_at - now).total_seconds()))
    seconds_until_weekly_status = max(0, int((weekly_ready_at - now).total_seconds()))
    tracking_day = max(1, int((now - calibration_started_at).total_seconds() // 86400) + 1)

    latest_row = db.execute(
        text(f'''
            SELECT systolic, diastolic, heart_rate, spo2
            FROM bp_readings
            WHERE {sensor_user_clause()}
            ORDER BY time DESC
            LIMIT 1
        '''),
        sensor_user_params(current_user),
    ).fetchone()

    calibration_params = {
        **sensor_user_params(current_user),
        "calibration_started_at": calibration_started_at,
        "calibration_ready_at": calibration_ready_at,
    }
    calibration_stats = db.execute(
        text(f'''
            SELECT
                COUNT(*) AS reading_count,
                AVG(systolic) AS avg_systolic,
                AVG(diastolic) AS avg_diastolic,
                AVG(heart_rate) AS avg_heart_rate
            FROM bp_readings
            WHERE {sensor_user_clause()}
              AND time >= :calibration_started_at
              AND time < :calibration_ready_at
        '''),
        calibration_params,
    ).fetchone()

    baseline_sys = float(calibration_stats.avg_systolic) if calibration_stats and calibration_stats.avg_systolic is not None else None
    baseline_dia = float(calibration_stats.avg_diastolic) if calibration_stats and calibration_stats.avg_diastolic is not None else None
    baseline_hr = float(calibration_stats.avg_heart_rate) if calibration_stats and calibration_stats.avg_heart_rate is not None else None
    baseline_ready = bool(calibration_stats and (calibration_stats.reading_count or 0) > 0)

    latest_sys = int(latest_row.systolic) if latest_row and latest_row.systolic is not None else None
    latest_dia = int(latest_row.diastolic) if latest_row and latest_row.diastolic is not None else None
    latest_hr = int(latest_row.heart_rate) if latest_row and latest_row.heart_rate is not None else None
    latest_spo2 = int(latest_row.spo2) if latest_row and latest_row.spo2 is not None else None

    if is_calibrated and baseline_ready:
        status_mode = "personalized"
        bp_status = _classify_personalized_bp(latest_sys, latest_dia, baseline_sys, baseline_dia)
        hr_status = _classify_personalized_hr(latest_hr, baseline_hr)
    else:
        status_mode = "standard"
        bp_status = _classify_standard_bp(latest_sys, latest_dia)
        hr_status = _classify_standard_hr(latest_hr)

    ox_status = "Normal" if latest_spo2 and latest_spo2 >= 95 else ("Low" if latest_spo2 else "No Data")

    if not is_calibrated:
        return HealthStatusResponse(
            success=True,
            health_score=0,
            overall_status="Calibrating",
            blood_pressure_status=bp_status,
            heart_rate_status=hr_status,
            oxygen_status=ox_status,
            calibration_started_at=_to_ms(calibration_started_at),
            calibration_ready_at=_to_ms(calibration_ready_at),
            weekly_status_ready_at=_to_ms(weekly_ready_at),
            seconds_until_calibrated=seconds_until_calibrated,
            seconds_until_weekly_status=seconds_until_weekly_status,
            tracking_day=tracking_day,
            is_calibrated=False,
            is_week_ready=False,
            countdown_phase="calibration",
            status_mode=status_mode,
            message="3-day BLE calibration is in progress.",
        )

    if not is_week_ready:
        return HealthStatusResponse(
            success=True,
            health_score=0,
            overall_status="Personalized Tracking",
            blood_pressure_status=bp_status,
            heart_rate_status=hr_status,
            oxygen_status=ox_status,
            calibration_started_at=_to_ms(calibration_started_at),
            calibration_ready_at=_to_ms(calibration_ready_at),
            weekly_status_ready_at=_to_ms(weekly_ready_at),
            seconds_until_calibrated=0,
            seconds_until_weekly_status=seconds_until_weekly_status,
            tracking_day=tracking_day,
            is_calibrated=True,
            is_week_ready=False,
            countdown_phase="personalized",
            status_mode=status_mode,
            message="Calibration completed. Personalized daily labels are active until day 7.",
        )

    weekly_window_start = max(calibration_started_at, now - timedelta(days=7))
    weekly_stats = db.execute(
        text(f'''
            SELECT
                COUNT(*) AS reading_count,
                AVG(systolic) AS avg_systolic,
                AVG(diastolic) AS avg_diastolic,
                AVG(heart_rate) AS avg_heart_rate
            FROM bp_readings
            WHERE {sensor_user_clause()}
              AND time >= :weekly_window_start
              AND time <= :weekly_window_end
        '''),
        {
            **sensor_user_params(current_user),
            "weekly_window_start": weekly_window_start,
            "weekly_window_end": now,
        },
    ).fetchone()

    if not weekly_stats or (weekly_stats.reading_count or 0) == 0:
        return HealthStatusResponse(
            success=False,
            health_score=0,
            overall_status="No Data",
            blood_pressure_status=bp_status,
            heart_rate_status=hr_status,
            oxygen_status=ox_status,
            calibration_started_at=_to_ms(calibration_started_at),
            calibration_ready_at=_to_ms(calibration_ready_at),
            weekly_status_ready_at=_to_ms(weekly_ready_at),
            seconds_until_calibrated=0,
            seconds_until_weekly_status=0,
            tracking_day=tracking_day,
            is_calibrated=True,
            is_week_ready=True,
            countdown_phase="weekly_ready",
            status_mode=status_mode,
            message="Not enough weekly readings to calculate health status.",
        )

    avg_week_sys = float(weekly_stats.avg_systolic) if weekly_stats.avg_systolic is not None else None
    avg_week_dia = float(weekly_stats.avg_diastolic) if weekly_stats.avg_diastolic is not None else None
    avg_week_hr = float(weekly_stats.avg_heart_rate) if weekly_stats.avg_heart_rate is not None else None

    if baseline_ready:
        weekly_bp_status = _classify_personalized_bp(
            int(avg_week_sys) if avg_week_sys is not None else None,
            int(avg_week_dia) if avg_week_dia is not None else None,
            baseline_sys,
            baseline_dia,
        )
        weekly_hr_status = _classify_personalized_hr(
            int(avg_week_hr) if avg_week_hr is not None else None,
            baseline_hr,
        )
    else:
        weekly_bp_status = _classify_standard_bp(
            int(avg_week_sys) if avg_week_sys is not None else None,
            int(avg_week_dia) if avg_week_dia is not None else None,
        )
        weekly_hr_status = _classify_standard_hr(int(avg_week_hr) if avg_week_hr is not None else None)

    score = 100
    if weekly_bp_status == "High":
        score -= 22
    elif weekly_bp_status == "Low":
        score -= 18
    elif weekly_bp_status == "Elevated":
        score -= 10

    if weekly_hr_status == "High":
        score -= 14
    elif weekly_hr_status == "Low":
        score -= 10

    if ox_status == "Low":
        score -= 8

    if score >= 90:
        overall = "Excellent"
    elif score >= 75:
        overall = "Good"
    elif score >= 60:
        overall = "Fair"
    else:
        overall = "Needs Attention"

    return HealthStatusResponse(
        success=True,
        health_score=max(0, score),
        overall_status=overall,
        blood_pressure_status=weekly_bp_status,
        heart_rate_status=weekly_hr_status,
        oxygen_status=ox_status,
        calibration_started_at=_to_ms(calibration_started_at),
        calibration_ready_at=_to_ms(calibration_ready_at),
        weekly_status_ready_at=_to_ms(weekly_ready_at),
        seconds_until_calibrated=0,
        seconds_until_weekly_status=0,
        tracking_day=tracking_day,
        is_calibrated=True,
        is_week_ready=True,
        countdown_phase="weekly_ready",
        status_mode=status_mode,
        message="7-day personalized health status is ready.",
    )


class PulseDataPoint(BaseModel):
    timestamp: int
    value: float


class PulseResponse(BaseModel):
    success: bool
    current_bpm: int
    resting_hr: int
    min_hr: int
    avg_hr: int
    max_hr: int
    status_label: str
    pattern_24h: List[PulseDataPoint]
    ecg_waveform_points: List[float]
    message: Optional[str] = None


@router.get('/pulse', response_model=PulseResponse)
async def get_pulse_data(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    """
    Return detailed Heart Rate & ECG data for the Pulse screen.
    Combines real DB stats with a synthetic ECG waveform.
    """
    # 1. Get latest BPM
    latest_row = db.execute(
        text(f'''
            SELECT heart_rate
            FROM bp_readings
            WHERE {sensor_user_clause()} AND heart_rate IS NOT NULL
            ORDER BY time DESC LIMIT 1
        '''), sensor_user_params(current_user)
    ).fetchone()
    current_bpm = int(latest_row.heart_rate) if latest_row and latest_row.heart_rate else 0

    # 2. Get 24-hour stats
    stats_row = db.execute(
        text(f'''
            SELECT 
                MIN(heart_rate) as min_hr,
                AVG(heart_rate) as avg_hr,
                MAX(heart_rate) as max_hr
            FROM bp_readings
            WHERE {sensor_user_clause()} AND time >= NOW() - INTERVAL '24 hours'
              AND heart_rate IS NOT NULL
        '''), sensor_user_params(current_user)
    ).fetchone()

    min_hr = int(stats_row.min_hr) if stats_row and stats_row.min_hr else 0
    avg_hr = int(stats_row.avg_hr) if stats_row and stats_row.avg_hr else 0
    max_hr = int(stats_row.max_hr) if stats_row and stats_row.max_hr else 0

    # Assume resting HR is close to the minimum HR measured over a 24h span
    resting_hr = min_hr

    if avg_hr == 0:
        status_label = "No Data"
    elif avg_hr < 60:
        status_label = "Bradycardia"
    elif avg_hr > 100:
        status_label = "Tachycardia"
    else:
        status_label = "Normal"

    # 3. Get 24-hour pattern (hourly buckets)
    pattern_rows = db.execute(
        text(f'''
            SELECT 
                time_bucket('1 hour', time) AS bucket_time,
                AVG(heart_rate) AS hr
            FROM bp_readings
            WHERE {sensor_user_clause()} AND time >= NOW() - INTERVAL '24 hours'
              AND heart_rate IS NOT NULL
            GROUP BY bucket_time
            ORDER BY bucket_time ASC
        '''), sensor_user_params(current_user)
    ).fetchall()

    pattern_24h = [
        PulseDataPoint(
            timestamp=int(r.bucket_time.timestamp() * 1000),
            value=round(float(r.hr), 1)
        ) for r in pattern_rows
    ]

    # 4. Generate Synthetic ECG Waveform (Fallback until real sensor data is tracked)
    import math
    ecg_waveform = []
    # Generates 5 seconds of simple synthetic ECG at 100Hz (500 data points)
    for i in range(500):
        t = i / 100.0
        # Base baseline drift
        val = math.sin(t * 0.5) * 0.05
        # P-wave synthesis
        val += math.sin(t * math.pi * 5) * 0.1 if (t % 1.0) < 0.2 else 0
        # QRS complex (sharp spike centered at t=0.25)
        if 0.2 < (t % 1.0) < 0.3:
            val += 2.0 * math.sin((t % 1.0 - 0.2) * math.pi * 10)
        # T-wave synthesis
        val += math.sin((t % 1.0 - 0.4) * math.pi * 4) * 0.2 if 0.4 < (t % 1.0) < 0.65 else 0
        
        ecg_waveform.append(round(val, 3))

    return PulseResponse(
        success=True,
        current_bpm=current_bpm,
        resting_hr=resting_hr,
        min_hr=min_hr,
        avg_hr=avg_hr,
        max_hr=max_hr,
        status_label=status_label,
        pattern_24h=pattern_24h,
        ecg_waveform_points=ecg_waveform,
    )


class PpgDataPoint(BaseModel):
    timestamp: int
    quality: float


class PpgSignalResponse(BaseModel):
    success: bool
    avg_quality: int
    signal_stability: int
    highest_quality: int
    lowest_quality: int
    chart_points: List[PpgDataPoint]
    message: Optional[str] = None


@router.get('/ppg/signal', response_model=PpgSignalResponse)
async def get_ppg_signal(current_user: User = Depends(get_current_user)):
    """
    Return synthetic PPG signal quality data for the PPG screen.
    Since raw PPG signal quality over time is not currently tracked 
    in the database, this acts as a demo endpoint to unblock frontend UI.
    Provides 60 seconds of simulated stability metrics and points.
    """
    import math
    import time
    
    # Generate 60 seconds of synthetic signal quality data (1 point per second)
    now_ms = int(time.time() * 1000)
    
    chart_points = []
    qualities = []
    
    for i in range(60):
        # Time steps of 1 second backwards, from oldest to newest
        ts = now_ms - ((59 - i) * 1000)
        
        # Base quality around 85, with some sinusoidal variation and random noise
        # This makes it look like a somewhat stable but fluctuating signal
        base = 85.0
        variation = math.sin(i * 0.2) * 10.0
        noise = (i % 3) * 2.0 - 2.0  # simple deterministic noise
        
        # Ensure quality bounds are strictly 0 to 100
        q = min(100.0, max(0.0, base + variation + noise))
        round_q = round(q, 1)
        
        chart_points.append(PpgDataPoint(timestamp=ts, quality=round_q))
        qualities.append(round_q)
        
    avg_q = int(sum(qualities) / len(qualities))
    high_q = int(max(qualities))
    low_q = int(min(qualities))
    
    # Signal stability: higher difference between min and max means lower stability
    # 100 = perfectly stable (flatline), lower = unstable
    stability = max(0, 100 - (high_q - low_q))

    return PpgSignalResponse(
        success=True,
        avg_quality=avg_q,
        signal_stability=stability,
        highest_quality=high_q,
        lowest_quality=low_q,
        chart_points=chart_points,
        message="Demo PPG data generated successfully."
    )
