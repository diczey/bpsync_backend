"""
 ╔══════════════════════════════════════════════════════════════╗
 ║               BPSync — Health Readings Router                ║
 ╠══════════════════════════════════════════════════════════════╣
 ║  Endpoints: GET /readings, POST /readings                    ║
 ║             POST /readings/predict-bp, /calibrate-bp         ║
 ╚══════════════════════════════════════════════════════════════╝
"""
import json
import statistics
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import text
from typing import Any, List, Optional
from pydantic import BaseModel
from datetime import datetime, timezone

from backend.database import get_sensor_db
from backend.models.user import User
from backend.utils.security import get_current_user
from backend.utils.sensor_identity import (
    canonical_sensor_user_key,
    sensor_user_clause,
    sensor_user_params,
)
from backend.services.bp_model_service import get_prediction_service

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
    spo2:         Optional[int]   = None  # Present for manual/cuff rows; live BLE may leave it null
    temperature:  Optional[float] = None  # Latest raw temperature lives in wristband_data
    ecg_data:     Optional[List[float]] = None  # Future: ECG waveform samples
    ppg_data:     Optional[List[float]] = None  # Future: PPG waveform samples


class HealthReadingsResponse(BaseModel):
    success:  bool
    readings: List[HealthReadingDto] = []
    message:  Optional[str] = None


class SeedDemoDataRequest(BaseModel):
    count: int = 24
    replace_existing: bool = False


class SeedDemoDataResponse(BaseModel):
    success: bool
    inserted_count: int
    message: Optional[str] = None


# ══════════════════════════════════════════════════════════════
#  REQUEST MODELS
# ══════════════════════════════════════════════════════════════
class HealthReadingCreate(BaseModel):
    """Reading submitted manually from the mobile sign (e.g. cuff measurement)"""
    timestamp:   int                         # Unix milliseconds
    heart_rate:  Optional[int]   = None
    systolic_bp: Optional[int]   = None
    diastolic_bp: Optional[int]  = None
    spo2:        Optional[int]   = None      # Stored in bp_readings when supplied manually
    temperature: Optional[float] = None      # Stored in wristband_data for context
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
    model:     Optional[str] = None
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

    bp_readings may store spo2 for manual readings, but the live BLE CNN path
    currently leaves it null. We mirror the dashboard behaviour by falling back
    to a safe placeholder (98) until live SpO2 inference is implemented.
    Temperature is written into wristband_data, so GET /readings selects the
    nearest raw temperature sample for each BP row and exposes it here.
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
        temperature=getattr(row, 'temperature', None),
    )


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


def _load_latest_raw_series(
    db: Session,
    current_user: User,
    row,
) -> tuple[Optional[List[float]], Optional[List[float]]]:
    source_start = getattr(row, "source_seq_start", None)
    source_end = getattr(row, "source_seq_end", None)
    if source_start is None or source_end is None:
        return None, None

    params = {
        **sensor_user_params(current_user),
        "seq_start": int(source_start),
        "seq_end": int(source_end),
    }

    ecg_rows = db.execute(
        text(f"""
            SELECT ecg_value
            FROM ecg_data
            WHERE {sensor_user_clause()}
              AND frame_seq BETWEEN :seq_start AND :seq_end
            ORDER BY frame_seq ASC, sample_index ASC
        """),
        params,
    ).fetchall()
    ecg_data = [
        round(float(raw.ecg_value), 3)
        for raw in ecg_rows
        if getattr(raw, "ecg_value", None) is not None
    ]

    wrist_rows = db.execute(
        text(f"""
            SELECT ppg_ir, ppg_red, ppg_ir_batch, ppg_red_batch, qi_w, qi
            FROM wristband_data
            WHERE {sensor_user_clause()}
              AND frame_seq BETWEEN :seq_start AND :seq_end
            ORDER BY frame_seq ASC
        """),
        params,
    ).fetchall()
    ppg_quality = []
    for raw in wrist_rows:
        ir_series = _decode_numeric_series(
            raw.ppg_ir_batch if getattr(raw, "ppg_ir_batch", None) is not None else raw.ppg_ir
        )
        red_series = _decode_numeric_series(
            raw.ppg_red_batch if getattr(raw, "ppg_red_batch", None) is not None else raw.ppg_red
        )
        qi_value = max(int(getattr(raw, "qi_w", 0) or 0), int(getattr(raw, "qi", 0) or 0))
        ppg_quality.append(round(_ppg_quality_from_series(ir_series, red_series, qi_value), 1))

    if ecg_data or ppg_quality:
        return (ecg_data or None, ppg_quality or None)

    reading_time = getattr(row, "time", None)
    if reading_time is None:
        return None, None

    fallback_params = {
        **sensor_user_params(current_user),
        "reading_time": reading_time,
    }

    fallback_ecg_rows = db.execute(
        text(f"""
            SELECT ecg_value
            FROM ecg_data
            WHERE {sensor_user_clause("user_id")}
              AND time BETWEEN :reading_time - INTERVAL '5 minutes'
                           AND :reading_time + INTERVAL '5 minutes'
            ORDER BY time ASC, frame_seq ASC, sample_index ASC
            LIMIT 1000
        """),
        fallback_params,
    ).fetchall()
    fallback_ecg_data = [
        round(float(raw.ecg_value), 3)
        for raw in fallback_ecg_rows
        if getattr(raw, "ecg_value", None) is not None
    ]

    fallback_wrist_rows = db.execute(
        text(f"""
            SELECT ppg_ir, ppg_red, ppg_ir_batch, ppg_red_batch, qi_w, qi
            FROM wristband_data
            WHERE {sensor_user_clause("user_id")}
              AND time BETWEEN :reading_time - INTERVAL '5 minutes'
                           AND :reading_time + INTERVAL '5 minutes'
            ORDER BY time ASC, frame_seq ASC
            LIMIT 25
        """),
        fallback_params,
    ).fetchall()
    fallback_ppg_quality = []
    for raw in fallback_wrist_rows:
        ir_series = _decode_numeric_series(
            raw.ppg_ir_batch if getattr(raw, "ppg_ir_batch", None) is not None else raw.ppg_ir
        )
        red_series = _decode_numeric_series(
            raw.ppg_red_batch if getattr(raw, "ppg_red_batch", None) is not None else raw.ppg_red
        )
        qi_value = max(int(getattr(raw, "qi_w", 0) or 0), int(getattr(raw, "qi", 0) or 0))
        fallback_ppg_quality.append(round(_ppg_quality_from_series(ir_series, red_series, qi_value), 1))

    return (fallback_ecg_data or None, fallback_ppg_quality or None)


def _resolve_category(systolic: int, diastolic: int) -> str:
    if systolic >= 140 or diastolic >= 90:
        return "Stage 2 Hypertension"
    if systolic >= 130 or diastolic >= 80:
        return "Stage 1 Hypertension"
    if systolic >= 120 and diastolic < 80:
        return "Elevated"
    return "Normal"


# ══════════════════════════════════════════════════════════════
#  ENDPOINTS
# ══════════════════════════════════════════════════════════════

@router.get('', response_model=HealthReadingsResponse)
async def get_readings(
    limit: int = 50,
    include_raw: bool = False,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    """
    Return the most-recent BP readings for the authenticated user.

    Reads from the 'bp_readings' TimescaleDB hypertable which is populated
    by the BLE data pipeline (ble/data_manager.py) after each ML inference
    window (waveform mode typically every 25 synchronized wrist/chest frames).

    Falls back to mock data when no rows exist and USE_MOCK_DATA=true.
    """
    rows = db.execute(
        text(f'''
            SELECT
                r.time,
                r.user_id,
                r.systolic,
                r.diastolic,
                r.heart_rate,
                COALESCE(r.spo2, 98) AS spo2,
                temp_match.temperature AS temperature,
                r.source_seq_start,
                r.source_seq_end
            FROM bp_readings AS r
            LEFT JOIN LATERAL (
                SELECT w.temperature
                FROM wristband_data AS w
                WHERE {sensor_user_clause("w.user_id")}
                  AND w.temperature IS NOT NULL
                  AND w.time BETWEEN r.time - INTERVAL '12 hours' AND r.time + INTERVAL '12 hours'
                ORDER BY ABS(EXTRACT(EPOCH FROM (w.time - r.time))) ASC
                LIMIT 1
            ) AS temp_match ON TRUE
            WHERE {sensor_user_clause("r.user_id")}
            ORDER BY r.time DESC
            LIMIT :limit
        '''),
        {
            **sensor_user_params(current_user),
            'limit': limit,
        },
    ).fetchall()


    if not rows:
        return HealthReadingsResponse(success=True, readings=[], message="No readings found. Connect your BPSync wristband to start measuring.")


    readings: List[HealthReadingDto] = []
    raw_index = None
    raw_series = (None, None)
    if include_raw:
        raw_index = next(
            (
                index
                for index, row in enumerate(rows)
                if getattr(row, "source_seq_start", None) is not None and getattr(row, "source_seq_end", None) is not None
            ),
            0 if rows else None,
        )
        raw_series = _load_latest_raw_series(db, current_user, rows[raw_index]) if raw_index is not None else (None, None)
    for index, row in enumerate(rows):
        ecg_data, ppg_data = raw_series if index == raw_index else (None, None)
        dto = _row_to_dto(row)
        if ecg_data is not None or ppg_data is not None:
            dto.ecg_data = ecg_data
            dto.ppg_data = ppg_data
        readings.append(dto)

    return HealthReadingsResponse(success=True, readings=readings)


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
    Temperature is additionally mirrored into wristband_data so dashboard and
    raw-signal inspection stay consistent with the live BLE schema.

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
            'uid':  canonical_sensor_user_key(current_user),
            'sys':  systolic or None,
            'dia':  diastolic or None,
            'hr':   request.heart_rate,
            'spo2': request.spo2,
            'cat':  category,
        },
    )
    if request.temperature is not None:
        db.execute(
            text('''
                INSERT INTO wristband_data (time, user_id, temperature, frame_mode)
                VALUES (:time, :uid, :temperature, :frame_mode)
            '''),
            {
                'time': reading_time,
                'uid': canonical_sensor_user_key(current_user),
                'temperature': request.temperature,
                'frame_mode': 'manual',
            },
        )
    db.commit()

    # Return the saved reading in the same DTO format as GET /readings
    saved = HealthReadingDto(
        id=f"{canonical_sensor_user_key(current_user)}-{request.timestamp}",
        user_id=canonical_sensor_user_key(current_user),
        timestamp=request.timestamp,
        heart_rate=request.heart_rate,
        systolic_bp=request.systolic_bp,
        diastolic_bp=request.diastolic_bp,
        spo2=request.spo2,
        temperature=request.temperature,
    )
    return HealthReadingsResponse(success=True, readings=[saved])


@router.post('/seed-demo', response_model=SeedDemoDataResponse)
async def seed_demo_readings(
    request: SeedDemoDataRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    """
    Insert realistic sample readings directly into TimescaleDB for the
    authenticated user so the mobile app can be tested without a BLE device.
    """
    count = max(1, min(request.count, 120))

    if request.replace_existing:
        params = sensor_user_params(current_user)
        db.execute(
            text(f'''
                DELETE FROM bp_readings
                WHERE {sensor_user_clause()}
            '''),
            params,
        )
        db.execute(
            text(f'''
                DELETE FROM wristband_data
                WHERE {sensor_user_clause()}
            '''),
            params,
        )

    from backend.utils.mock_data import generate_health_readings

    generated = generate_health_readings(current_user.id, count=count)
    sensor_user_id = canonical_sensor_user_key(current_user)

    for item in generated:
        reading_time = datetime.fromtimestamp(item["timestamp"] / 1000, tz=timezone.utc)
        systolic = item.get("systolic_bp")
        diastolic = item.get("diastolic_bp")
        heart_rate = item.get("heart_rate")
        spo2 = item.get("spo2")
        temperature = item.get("temperature")

        db.execute(
            text('''
                INSERT INTO bp_readings (time, user_id, systolic, diastolic, heart_rate, spo2, category)
                VALUES (:time, :uid, :sys, :dia, :hr, :spo2, :cat)
            '''),
            {
                'time': reading_time,
                'uid': sensor_user_id,
                'sys': systolic,
                'dia': diastolic,
                'hr': heart_rate,
                'spo2': spo2,
                'cat': _resolve_category(systolic or 0, diastolic or 0),
            },
        )

        db.execute(
            text('''
                INSERT INTO wristband_data (time, user_id, temperature, frame_mode)
                VALUES (:time, :uid, :temperature, :frame_mode)
            '''),
            {
                'time': reading_time,
                'uid': sensor_user_id,
                'temperature': temperature,
                'frame_mode': 'demo',
            },
        )

    db.commit()

    return SeedDemoDataResponse(
        success=True,
        inserted_count=count,
        message=f"{count} demo reading(s) inserted for {current_user.email}."
    )


@router.post('/predict-bp', response_model=BPPredictionResponse)
async def predict_bp(
    request: BPPredictionRequest,
    current_user: User = Depends(get_current_user),
):
    """
    Feature-based preview prediction has been removed.

    Blood pressure predictions now require live BLE waveform batches so the
    CNN-LSTM model can infer from ECG + PPG_RED + PPG_IR directly.
    """
    raise HTTPException(
        status_code=status.HTTP_410_GONE,
        detail=(
            "PTT-based prediction has been removed. Stream live BLE waveform batches "
            "to use the CNN-LSTM blood pressure model."
        ),
    )


@router.post('/calibrate-bp')
async def calibrate_bp(
    request: BPCalibrationRequest,
    current_user: User = Depends(get_current_user),
):
    """
    Feature-based calibration has been removed.

    Calibration now starts automatically after a successful BLE connection and
    continues from live waveform readings during the first 3 days.
    """
    raise HTTPException(
        status_code=status.HTTP_410_GONE,
        detail=(
            "Feature-based calibration has been removed. Connect the wristband and "
            "stream waveform BLE data to start CNN-LSTM calibration automatically."
        ),
    )


@router.get('/model-info')
async def get_model_info(current_user: User = Depends(get_current_user)):
    """
    Return active BP model information for the live BLE pipeline.

    The backend is now CNN-only, so this endpoint reports whether the
    feature-based inference or Göksu's CNN-LSTM waveform model.
    """
    info = get_prediction_service().model_status()
    return {
        'success': True,
        **info,
    }
