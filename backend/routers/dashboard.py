"""
 ╔══════════════════════════════════════════════════════════════╗
 ║                BPSync — Dashboard Router                     ║
 ╠══════════════════════════════════════════════════════════════╣
 ║  Provides the home-screen summary card for the mobile app    ║
 ║  Endpoints: /summary, /health-status, /pulse, /ppg/signal    ║
 ╚══════════════════════════════════════════════════════════════╝
"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import text
from typing import Optional, List
from pydantic import BaseModel

from backend.database import get_sensor_db
from backend.models.user import User
from backend.utils.security import get_current_user

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
        text('''
            SELECT time, systolic, diastolic, heart_rate
            FROM bp_readings
            WHERE user_id = :uid OR user_id = :email
            ORDER BY time DESC
            LIMIT 1
        '''),
        {'uid': current_user.id, 'email': current_user.email},
    ).fetchone()

    if not bp_row:
        return DashboardResponse(success=False, message='No readings yet. Connect your BPSync wristband to start measuring.')


    # Fetch the most-recent temperature from wristband raw frames.
    # wristband_data is written at 10 Hz so this is nearly real-time.
    temp_row = db.execute(
        text('''
            SELECT temperature
            FROM wristband_data
            WHERE (user_id = :uid OR user_id = :email)
              AND temperature IS NOT NULL
            ORDER BY time DESC
            LIMIT 1
        '''),
        {'uid': current_user.id, 'email': current_user.email},
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
    message: Optional[str] = None


@router.get('/health-status', response_model=HealthStatusResponse)
async def get_health_status(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    """
    Return the derived health status based on the user's recent sensor data.
    Uses the average of the last 7 days of readings to calculate a score.
    """
    rows = db.execute(
        text('''
            SELECT systolic, diastolic, heart_rate
            FROM bp_readings
            WHERE user_id = :uid
              AND time >= NOW() - INTERVAL '7 days'
        '''),
        {'uid': current_user.id},
    ).fetchall()

    if not rows:
        return HealthStatusResponse(
            success=False,
            health_score=0,
            overall_status="No Data",
            blood_pressure_status="No Data",
            heart_rate_status="No Data",
            oxygen_status="No Data",
            message="Not enough readings to calculate status."
        )

    valid_sys = [r.systolic for r in rows if r.systolic is not None]
    valid_dia = [r.diastolic for r in rows if r.diastolic is not None]
    valid_hr  = [r.heart_rate for r in rows if r.heart_rate is not None]

    avg_sys = sum(valid_sys) / len(valid_sys) if valid_sys else 120
    avg_hr  = sum(valid_hr) / len(valid_hr) if valid_hr else 70

    score = 100
    
    # Blood Pressure Status Rules
    if avg_sys < 90 or avg_sys >= 140:
        bp_status = "Attention Required"
        score -= 20
    elif avg_sys >= 130:
        bp_status = "Stage 1 Hypertension"
        score -= 15
    elif avg_sys >= 120:
        bp_status = "Elevated"
        score -= 5
    else:
        bp_status = "Optimal"

    # Heart Rate Status Rules
    if avg_hr < 60 or avg_hr > 100:
        hr_status = "Attention Required"
        score -= 15
    else:
        hr_status = "Normal"

    # Oxygen Status (Placeholder until actual sensor integration)
    ox_status = "Normal"

    # Overall Status
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
        health_score=score,
        overall_status=overall,
        blood_pressure_status=bp_status,
        heart_rate_status=hr_status,
        oxygen_status=ox_status,
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
        text('''
            SELECT heart_rate
            FROM bp_readings
            WHERE user_id = :uid AND heart_rate IS NOT NULL
            ORDER BY time DESC LIMIT 1
        '''), {'uid': current_user.id}
    ).fetchone()
    current_bpm = int(latest_row.heart_rate) if latest_row and latest_row.heart_rate else 0

    # 2. Get 24-hour stats
    stats_row = db.execute(
        text('''
            SELECT 
                MIN(heart_rate) as min_hr,
                AVG(heart_rate) as avg_hr,
                MAX(heart_rate) as max_hr
            FROM bp_readings
            WHERE user_id = :uid AND time >= NOW() - INTERVAL '24 hours'
              AND heart_rate IS NOT NULL
        '''), {'uid': current_user.id}
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
        text('''
            SELECT 
                time_bucket('1 hour', time) AS bucket_time,
                AVG(heart_rate) AS hr
            FROM bp_readings
            WHERE user_id = :uid AND time >= NOW() - INTERVAL '24 hours'
              AND heart_rate IS NOT NULL
            GROUP BY bucket_time
            ORDER BY bucket_time ASC
        '''), {'uid': current_user.id}
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
