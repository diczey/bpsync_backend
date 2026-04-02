"""
 ╔══════════════════════════════════════════════════════════════╗
 ║               BPSync — Health Readings Router                ║
 ╠══════════════════════════════════════════════════════════════╣
 ║  Endpoints: GET /readings, POST /readings                    ║
 ║             POST /readings/predict-bp, /calibrate-bp         ║
 ╚══════════════════════════════════════════════════════════════╝
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import text
from typing import List, Optional
from pydantic import BaseModel
from datetime import datetime, timezone

from backend.database import get_sensor_db
from backend.models.user import User
from backend.utils.security import get_current_user
from backend.services.ml_service import get_bp_model, predict_blood_pressure

router = APIRouter()


# ══════════════════════════════════════════════════════════════
#  RESPONSE MODELS
# ══════════════════════════════════════════════════════════════
# DTO that matches Android HealthReadingDto exactly.
class HealthReadingDto(BaseModel):
    """A single health reading returned to the mobile app"""
    id:           str             # Surrogate identifier: "<user_id>-<unix_ms>"
    user_id:      str
    timestamp:    int             # Unix milliseconds — Android expects Long
    heart_rate:   Optional[int]   = None
    systolic_bp:  Optional[int]   = None  # Renamed from 'systolic' to match Android
    diastolic_bp: Optional[int]   = None  # Renamed from 'diastolic' to match Android
    spo2:         Optional[int]   = None  # Not in bp_readings; returned as None from DB
    temperature:  Optional[float] = None  # Not in bp_readings; returned as None from DB
    ecg_data:     Optional[List[float]] = None  # Future: ECG waveform samples
    ppg_data:     Optional[List[float]] = None  # Future: PPG waveform samples


class HealthReadingsResponse(BaseModel):
    success:  bool
    readings: List[HealthReadingDto] = []
    message:  Optional[str] = None


# ══════════════════════════════════════════════════════════════
#  REQUEST MODELS
# ══════════════════════════════════════════════════════════════
class HealthReadingCreate(BaseModel):
    """Reading submitted manually from the mobile sign (e.g. cuff measurement)"""
    timestamp:   int                         # Unix milliseconds
    heart_rate:  Optional[int]   = None
    systolic_bp: Optional[int]   = None
    diastolic_bp: Optional[int]  = None
    spo2:        Optional[int]   = None      # Stored for context; not in bp_readings schema yet
    temperature: Optional[float] = None      # Stored for context; not in bp_readings schema yet
    ecg_data:    Optional[List[float]] = None
    ppg_data:    Optional[List[float]] = None


# ══════════════════════════════════════════════════════════════
#  ML MODELS
# ══════════════════════════════════════════════════════════════
class BPPredictionRequest(BaseModel):
    ptt:       float
    heart_rate: float
    age:       Optional[float] = 40
    ptt_std:   Optional[float] = 15


class BPPredictionResponse(BaseModel):
    success:   bool
    systolic:  int
    diastolic: int
    category:  str
    message:   Optional[str] = None


class BPCalibrationRequest(BaseModel):
    measured_systolic:  int
    measured_diastolic: int
    ptt:                float
    heart_rate:         float
    age:                Optional[float] = 40


# ══════════════════════════════════════════════════════════════
#  HELPERS
# ══════════════════════════════════════════════════════════════
def _row_to_dto(row) -> HealthReadingDto:
    """
    Convert a bp_readings DB row into a HealthReadingDto.

    bp_readings does not store spo2 or temperature directly (those come from
    wristband_data at a different write frequency), so they are returned as
    None here. The mobile handles None gracefully by showing '--'.
    A surrogate 'id' is built from user_id + timestamp to give each row a
    unique stable key (bp_readings has no UUID primary key in TimescaleDB).
    """
    ts_ms = int(row.time.timestamp() * 1000)
    return HealthReadingDto(
        id=f"{row.user_id}-{ts_ms}",
        user_id=row.user_id,
        timestamp=ts_ms,
        heart_rate=row.heart_rate,
        systolic_bp=row.systolic,
        diastolic_bp=row.diastolic,
        spo2=getattr(row, 'spo2', None),
        temperature=None,  # Not stored in bp_readings; future pipeline improvement
    )


# ══════════════════════════════════════════════════════════════
#  ENDPOINTS
# ══════════════════════════════════════════════════════════════

@router.get('', response_model=HealthReadingsResponse)
async def get_readings(
    limit: int = 50,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    """
    Return the most-recent BP readings for the authenticated user.

    Reads from the 'bp_readings' TimescaleDB hypertable which is populated
    by the BLE data pipeline (ble/data_manager.py) after each ML inference
    window (~every 10 seconds at 10 Hz streaming).

    Falls back to mock data when no rows exist and USE_MOCK_DATA=true.
    """
    rows = db.execute(
        text('''
            SELECT time, user_id, systolic, diastolic, heart_rate, spo2
            FROM bp_readings
            WHERE user_id = :uid OR user_id = :email
            ORDER BY time DESC
            LIMIT :limit
        '''),
        {'uid': current_user.id, 'email': current_user.email, 'limit': limit},
    ).fetchall()

    if not rows:
        return HealthReadingsResponse(success=True, readings=[], message="No readings found. Connect your BPSync wristband to start measuring.")


    return HealthReadingsResponse(
        success=True,
        readings=[_row_to_dto(r) for r in rows],
    )


@router.post('', response_model=HealthReadingsResponse)
async def add_reading(
    request: HealthReadingCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    """
    Accept a manually entered or cuff-measured reading from the mobile app.

    The mobile sends a HealthReadingCreate object (timestamp, heart_rate,
    systolic_bp, diastolic_bp, spo2, temperature). We write systolic,
    diastolic, heart_rate and spo2 into bp_readings (the primary hypertable).
    temperature is acknowledged but not written because bp_readings
    has no dedicated column for it yet — this is flagged as a future DB
    schema upgrade in backendGuide.md.

    The category string is derived from the systolic value using the same
    JNC-8 rules as the ML pipeline so dashboard summaries stay consistent.
    """
    # Convert Android Long (Unix ms) to a Python datetime for TimescaleDB
    reading_time = datetime.fromtimestamp(request.timestamp / 1000, tz=timezone.utc)

    # Determine BP category so the row is consistent with ML-inferred rows
    systolic  = request.systolic_bp  or 0
    diastolic = request.diastolic_bp or 0
    if systolic > 140:
        category = "Stage 2 Hypertension"
    elif systolic > 130:
        category = "Stage 1 Hypertension"
    elif systolic > 120:
        category = "Elevated"
    else:
        category = "Normal"

    db.execute(
        text('''
            INSERT INTO bp_readings (time, user_id, systolic, diastolic, heart_rate, spo2, category)
            VALUES (:time, :uid, :sys, :dia, :hr, :spo2, :cat)
        '''),
        {
            'time': reading_time,
            'uid':  current_user.id,
            'sys':  systolic or None,
            'dia':  diastolic or None,
            'hr':   request.heart_rate,
            'spo2': request.spo2,
            'cat':  category,
        },
    )
    db.commit()

    # Return the saved reading in the same DTO format as GET /readings
    saved = HealthReadingDto(
        id=f"{current_user.id}-{request.timestamp}",
        user_id=current_user.id,
        timestamp=request.timestamp,
        heart_rate=request.heart_rate,
        systolic_bp=request.systolic_bp,
        diastolic_bp=request.diastolic_bp,
        spo2=request.spo2,
        temperature=request.temperature,
    )
    return HealthReadingsResponse(success=True, readings=[saved])


@router.post('/predict-bp', response_model=BPPredictionResponse)
async def predict_bp(
    request: BPPredictionRequest,
    current_user: User = Depends(get_current_user),
):
    """
    Trigger an XGBoost BP prediction from PTT + heart-rate values.

    Used by the mobile BLE screen after a streaming session to preview
    the predicted BP before it is stored via POST /readings.
    Uses the user's calculated age (from date_of_birth) for better accuracy.
    """
    try:
        result = predict_blood_pressure(
            ptt=request.ptt,
            heart_rate=request.heart_rate,
            age=current_user.age,        # Derived from User.date_of_birth property
            ptt_std=request.ptt_std or 15,
        )
        msg = '{}/{} mmHg - {}'.format(result['systolic'], result['diastolic'], result['category'])
        return BPPredictionResponse(
            success=True,
            systolic=result['systolic'],
            diastolic=result['diastolic'],
            category=result['category'],
            message=msg,
        )
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc))


@router.post('/calibrate-bp')
async def calibrate_bp(
    request: BPCalibrationRequest,
    current_user: User = Depends(get_current_user),
):
    """
    Calibrate the XGBoost model with a cuff-measured reference reading.

    The mobile sends an actual cuff measurement alongside the PTT that was
    measured at the same time. The model adjusts its per-user offset so
    future predictions are more accurate for this individual.
    """
    try:
        model = get_bp_model()
        model.calibrate(
            measured_systolic=request.measured_systolic,
            measured_diastolic=request.measured_diastolic,
            ptt=request.ptt,
            heart_rate=request.heart_rate,
            age=current_user.age,
        )
        return {
            'success': True,
            'message': 'Calibration done. Reference: {}/{} mmHg'.format(
                request.measured_systolic, request.measured_diastolic
            )
        }
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc))


@router.get('/model-info')
async def get_model_info(current_user: User = Depends(get_current_user)):
    """
    Return XGBoost model status and feature importances.
    Useful for the BLE/debug screen to confirm the model is loaded.
    """
    model = get_bp_model()
    return {
        'success': True,
        'model_loaded': model.is_loaded,
        'has_calibration': model.calibration is not None,
        'feature_importance': model.get_feature_importance(),
    }
