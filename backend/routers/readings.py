"""
Health Readings Router - BP Readings from TimescaleDB + ML Prediction
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import text
from typing import List, Optional
from pydantic import BaseModel

from backend.database import get_sensor_db
from backend.models.user import User
from backend.utils.security import get_current_user
from backend.services.ml_service import get_bp_model, predict_blood_pressure

router = APIRouter()


class BPReadingDto(BaseModel):
    time: str
    user_id: str
    systolic: Optional[int] = None
    diastolic: Optional[int] = None
    heart_rate: Optional[int] = None
    ptt: Optional[float] = None
    quality: Optional[int] = None
    category: Optional[str] = None


class BPReadingsResponse(BaseModel):
    success: bool
    readings: List[BPReadingDto] = []
    message: Optional[str] = None


class BPPredictionRequest(BaseModel):
    ptt: float
    heart_rate: float
    age: Optional[float] = 40
    ptt_std: Optional[float] = 15

class BPPredictionResponse(BaseModel):
    success: bool
    systolic: int
    diastolic: int
    category: str
    message: Optional[str] = None

class BPCalibrationRequest(BaseModel):
    measured_systolic: int
    measured_diastolic: int
    ptt: float
    heart_rate: float
    age: Optional[float] = 40


@router.post('/predict-bp', response_model=BPPredictionResponse)
async def predict_bp(
    request: BPPredictionRequest,
    current_user: User = Depends(get_current_user),
):
    '''Manually trigger XGBoost BP prediction from PTT + HR values.'''
    try:
        result = predict_blood_pressure(
            ptt=request.ptt,
            heart_rate=request.heart_rate,
            age=request.age or 40,
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
    '''Calibrate the XGBoost model with a cuff measurement reference.'''
    try:
        model = get_bp_model()
        model.calibrate(
            measured_systolic=request.measured_systolic,
            measured_diastolic=request.measured_diastolic,
            ptt=request.ptt,
            heart_rate=request.heart_rate,
            age=request.age or 40,
        )
        return {'success': True, 'message': 'Calibration done. Reference: {}/{} mmHg'.format(request.measured_systolic, request.measured_diastolic)}
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc))


@router.get('/model-info')
async def get_model_info(current_user: User = Depends(get_current_user)):
    '''XGBoost model status and feature importances.'''
    model = get_bp_model()
    return {
        'success': True,
        'model_loaded': model.is_loaded,
        'has_calibration': model.calibration is not None,
        'feature_importance': model.get_feature_importance(),
    }


@router.get('', response_model=BPReadingsResponse)
async def get_readings(
    limit: int = 50,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_sensor_db),
):
    '''List recent BP readings for the current user (from TimescaleDB bp_readings).'''
    rows = db.execute(
        text('''
            SELECT time, user_id, systolic, diastolic, heart_rate, ptt, quality, category
            FROM bp_readings
            WHERE user_id = :uid
            ORDER BY time DESC
            LIMIT :limit
        '''),
        {'uid': current_user.id, 'limit': limit},
    ).fetchall()
    return BPReadingsResponse(
        success=True,
        readings=[
            BPReadingDto(
                time=str(r.time),
                user_id=r.user_id,
                systolic=r.systolic,
                diastolic=r.diastolic,
                heart_rate=r.heart_rate,
                ptt=r.ptt,
                quality=r.quality,
                category=r.category,
            )
            for r in rows
        ],
    )
