"""
 ╔══════════════════════════════════════════════════════════════╗
 ║                BPSync — Dashboard Router                     ║
 ╠══════════════════════════════════════════════════════════════╣
 ║  Provides the home-screen summary card for the mobile app    ║
 ║  Endpoints: /summary, /health-status, /pulse, /ppg/signal    ║
 ╚══════════════════════════════════════════════════════════════╝
"""
import json
import statistics
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import text
from typing import Any, Optional, List
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


def _decode_numeric_series(value: Any) -> list[float]:
    if value is None:
        return []
    if isinstance(value, str):
        stripped = value.strip()
        if not stripped:
            return []
        if stripped.startswith("[") and stripped.endswith("]"):
            try:
                value = json.loads(stripped)
            except json.JSONDecodeError:
                return []
        else:
            try:
                return [float(stripped)]
            except ValueError:
                return []
    if isinstance(value, (list, tuple)):
        decoded: list[float] = []
        for item in value:
            try:
                decoded.append(float(item))
            except (TypeError, ValueError):
                continue
        return decoded
    try:
        return [float(value)]
    except (TypeError, ValueError):
        return []


def _ppg_quality_from_series(ppg_ir: list[float], ppg_red: list[float], qi_value: Optional[int] = None) -> float:
    combined = ppg_ir or ppg_red
    if not combined:
        return 0.0

    mean_value = abs(statistics.fmean(combined))
    spread = max(combined) - min(combined)
    variability = statistics.pstdev(combined) if len(combined) > 1 else 0.0

    base_quality = 100.0
    if mean_value > 0:
        base_quality -= min(55.0, (variability / mean_value) * 140.0)
        base_quality -= min(20.0, (spread / mean_value) * 18.0)
    else:
        base_quality -= 60.0

    if ppg_red and len(ppg_red) > 1:
        red_mean = abs(statistics.fmean(ppg_red))
        red_variability = statistics.pstdev(ppg_red)
        if red_mean > 0:
            base_quality -= min(15.0, (red_variability / red_mean) * 60.0)

    if qi_value is not None and qi_value <= 0:
        base_quality -= 35.0

    return float(max(0.0, min(100.0, base_quality)))


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
      - bp_readings    -> systolic, diastolic, heart_rate, optional spo2
      - wristband_data -> temperature (most recent frame)

    Live BLE CNN inference does not yet derive SpO2, so the value is often
    NULL for streaming rows. When that happens we fall back to a safe 98
    placeholder so the existing mobile UI keeps working.

    Falls back to mock data when no rows exist and USE_MOCK_DATA=true.
    """
    # Fetch the most-recent BP reading for this user.
    # Accept both UUID (current format) and email (legacy format) as user_id
    bp_row = db.execute(
        text(f'''
            SELECT time, systolic, diastolic, heart_rate, spo2
            FROM bp_readings
            WHERE {sensor_user_clause()}
            ORDER BY time DESC
            LIMIT 1
        '''),
        sensor_user_params(current_user),
    ).fetchone()

    if not bp_row:
        return DashboardResponse(success=False, message='No readings yet. Connect your BPSync wristband to start measuring.')

    hr_row = db.execute(
        text(f'''
            SELECT heart_rate
            FROM bp_readings
            WHERE {sensor_user_clause()}
              AND heart_rate IS NOT NULL
              AND heart_rate > 0
            ORDER BY time DESC
            LIMIT 1
        '''),
        sensor_user_params(current_user),
    ).fetchone()


    # Fetch the most-recent temperature from wristband raw frames.
    # wristband_data is updated continuously from the synchronized mobile flow,
    # so this remains the freshest temperature sample.
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

    spo2 = int(bp_row.spo2) if getattr(bp_row, "spo2", None) is not None else 0

    temperature = float(temp_row.temperature) if temp_row else 0.0

    systolic   = bp_row.systolic    or 0
    diastolic  = bp_row.diastolic   or 0
    heart_rate = int(hr_row.heart_rate) if hr_row and hr_row.heart_rate is not None else 0

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
    latest_hr_row = db.execute(
        text(f'''
            SELECT heart_rate
            FROM bp_readings
            WHERE {sensor_user_clause()}
              AND heart_rate IS NOT NULL
              AND heart_rate > 0
            ORDER BY time DESC
            LIMIT 1
        '''), sensor_user_params(current_user)
    ).fetchone()

    latest_hr = int(latest_hr_row.heart_rate) if latest_hr_row and latest_hr_row.heart_rate is not None else None
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
                COUNT(DISTINCT DATE(time)) AS active_days,
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

    weekly_reading_count = int(weekly_stats.reading_count or 0) if weekly_stats else 0
    weekly_active_days = int(weekly_stats.active_days or 0) if weekly_stats and weekly_stats.active_days is not None else 0

    if weekly_reading_count == 0 or weekly_active_days < 7:
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
            seconds_until_weekly_status=max(0, int((weekly_ready_at - now).total_seconds())),
            tracking_day=tracking_day,
            is_calibrated=True,
            is_week_ready=False,
            countdown_phase="weekly_pending",
            status_mode=status_mode,
            message="Collect readings across 7 distinct days to unlock weekly status.",
        )

    if not weekly_stats or weekly_reading_count == 0:
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
    Uses only real Timescale rows. If no data is available, numeric fields
    stay at zero and waveform points are returned empty.
    """
    # 1. Get latest BPM
    latest_row = db.execute(
        text(f'''
            SELECT heart_rate
            FROM bp_readings
            WHERE {sensor_user_clause()}
              AND heart_rate IS NOT NULL
              AND heart_rate > 0
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
              AND heart_rate > 0
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
                date_trunc('hour', time) AS bucket_time,
                AVG(heart_rate) AS hr
            FROM bp_readings
            WHERE {sensor_user_clause()} AND time >= NOW() - INTERVAL '24 hours'
              AND heart_rate IS NOT NULL
              AND heart_rate > 0
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

    # 4. Load recent raw ECG data from TimescaleDB.
    ecg_rows = db.execute(
        text(f'''
            SELECT time, ecg_value
            FROM ecg_data
            WHERE {sensor_user_clause()}
              AND ecg_value IS NOT NULL
            ORDER BY time DESC
            LIMIT 500
        '''),
        sensor_user_params(current_user),
    ).fetchall()

    ecg_waveform = [round(float(row.ecg_value), 3) for row in reversed(ecg_rows) if row.ecg_value is not None]
    message = "Pulse screen using raw ECG data from TimescaleDB." if ecg_rows else "No raw ECG data available in TimescaleDB."

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
        message=message,
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
async def get_ppg_signal(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    """
    Return PPG signal quality data for the PPG screen.
    Prefer raw Timescale wrist rows only. If no raw rows are available,
    return empty chart points and zeroed summary values.
    """
    chart_points = []
    qualities = []

    raw_rows = db.execute(
        text(f'''
            SELECT time, ppg_ir, ppg_red, ppg_ir_batch, ppg_red_batch, qi_w, qi
            FROM wristband_data
            WHERE {sensor_user_clause()}
              AND (ppg_ir IS NOT NULL OR ppg_ir_batch IS NOT NULL)
            ORDER BY time DESC
            LIMIT 60
        '''),
        sensor_user_params(current_user),
    ).fetchall()

    for row in reversed(raw_rows):
        raw_time = row.time
        ts = int(raw_time.timestamp() * 1000)
        ir_series = _decode_numeric_series(row.ppg_ir_batch if getattr(row, "ppg_ir_batch", None) is not None else row.ppg_ir)
        red_series = _decode_numeric_series(row.ppg_red_batch if getattr(row, "ppg_red_batch", None) is not None else row.ppg_red)
        qi_value = max(int(getattr(row, "qi_w", 0) or 0), int(getattr(row, "qi", 0) or 0))
        quality = _ppg_quality_from_series(ir_series, red_series, qi_value)
        round_q = round(quality, 1)

        chart_points.append(PpgDataPoint(timestamp=ts, quality=round_q))
        qualities.append(round_q)

    avg_q = int(round(sum(qualities) / len(qualities))) if qualities else 0
    high_q = int(max(qualities)) if qualities else 0
    low_q = int(min(qualities)) if qualities else 0
    stability = max(0, 100 - (high_q - low_q))
    message = "PPG screen using raw wristband rows from TimescaleDB." if raw_rows else "No raw PPG data available in TimescaleDB."

    return PpgSignalResponse(
        success=True,
        avg_quality=avg_q,
        signal_stability=stability,
        highest_quality=high_q,
        lowest_quality=low_q,
        chart_points=chart_points,
        message=message,
    )
