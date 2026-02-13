"""
Health Readings Router - CRUD Operations for Health Data + BP Prediction
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import Optional
from datetime import datetime
from pydantic import BaseModel

from app.database import get_db
from app.models.user import User
from app.models.health_reading import HealthReading
from app.schemas.health import (
    HealthReadingDto, 
    HealthReadingCreate, 
    HealthReadingsResponse
)
from app.utils.security import get_current_user
from app.utils.mock_data import generate_historical_readings
from app.config import settings
from app.services.ml_service import get_bp_model, predict_blood_pressure

router = APIRouter()


# ==================== XGBoost BP Prediction Schemas ====================

class BPPredictionRequest(BaseModel):
    """Tansiyon tahmini için istek"""
    ptt: float  # Pulse Transit Time (ms)
    heart_rate: float  # Kalp atış hızı (bpm)
    age: Optional[float] = 40  # Yaş
    ptt_std: Optional[float] = 15  # PTT standart sapması

class BPPredictionResponse(BaseModel):
    """Tansiyon tahmini yanıtı"""
    success: bool
    systolic: int
    diastolic: int
    category: str
    message: Optional[str] = None

class BPCalibrationRequest(BaseModel):
    """Kalibrasyon için istek"""
    measured_systolic: int  # Manşetle ölçülen sistolik
    measured_diastolic: int  # Manşetle ölçülen diyastolik
    ptt: float  # Ölçüm anındaki PTT
    heart_rate: float  # Ölçüm anındaki HR
    age: Optional[float] = 40


# ==================== XGBoost BP Prediction Endpoints ====================

@router.post("/predict-bp", response_model=BPPredictionResponse)
async def predict_bp(
    request: BPPredictionRequest,
    current_user: User = Depends(get_current_user)
):
    """
    XGBoost ile Tansiyon Tahmini
    
    PTT (Pulse Transit Time) ve diğer parametrelerden
    sistolik ve diyastolik kan basıncı tahmin eder.
    
    - **ptt**: ECG R-peak ile PPG peak arası süre (ms)
    - **heart_rate**: Kalp atış hızı (bpm)
    - **age**: Kullanıcı yaşı (opsiyonel, varsayılan 40)
    - **ptt_std**: PTT değişkenliği (opsiyonel, varsayılan 15)
    """
    try:
        result = predict_blood_pressure(
            ptt=request.ptt,
            heart_rate=request.heart_rate,
            age=request.age or 40,
            ptt_std=request.ptt_std or 15
        )
        
        return BPPredictionResponse(
            success=True,
            systolic=result["systolic"],
            diastolic=result["diastolic"],
            category=result["category"],
            message=f"Tahmin: {result['systolic']}/{result['diastolic']} mmHg - {result['category']}"
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Tahmin hatası: {str(e)}"
        )


@router.post("/calibrate-bp")
async def calibrate_bp(
    request: BPCalibrationRequest,
    current_user: User = Depends(get_current_user)
):
    """
    XGBoost Modelini Kalibre Et
    
    Manşetle (cuff) ölçülen referans değerlerle
    modeli kullanıcıya özel ayarlar.
    
    Kalibrasyon, tahmin doğruluğunu artırır.
    """
    try:
        model = get_bp_model()
        model.calibrate(
            measured_systolic=request.measured_systolic,
            measured_diastolic=request.measured_diastolic,
            ptt=request.ptt,
            heart_rate=request.heart_rate,
            age=request.age or 40
        )
        
        return {
            "success": True,
            "message": f"Kalibrasyon tamamlandı. Referans: {request.measured_systolic}/{request.measured_diastolic} mmHg"
        }
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Kalibrasyon hatası: {str(e)}"
        )


@router.get("/model-info")
async def get_model_info(
    current_user: User = Depends(get_current_user)
):
    """
    XGBoost Model Bilgilerini Getir
    
    Model durumu ve özellik önemlilikleri.
    """
    model = get_bp_model()
    
    return {
        "success": True,
        "model_loaded": model.is_loaded,
        "has_calibration": model.calibration is not None,
        "feature_importance": model.get_feature_importance()
    }


@router.get("", response_model=HealthReadingsResponse)
async def get_health_readings(
    start_date: Optional[int] = None,
    end_date: Optional[int] = None,
    limit: Optional[int] = 50,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Sağlık ölçümlerini listele
    
    - Tarih aralığına göre filtreleme
    - Limit ile sayfa boyutu belirleme
    """
    if settings.use_mock_data:
        # Generate mock historical readings
        mock_readings = generate_historical_readings(current_user.id, days=7, readings_per_day=4)
        
        # Apply filters
        if start_date:
            mock_readings = [r for r in mock_readings if r["timestamp"] >= start_date]
        if end_date:
            mock_readings = [r for r in mock_readings if r["timestamp"] <= end_date]
        if limit:
            mock_readings = mock_readings[:limit]
        
        return HealthReadingsResponse(
            success=True,
            readings=[HealthReadingDto(**r) for r in mock_readings]
        )
    
    # Build query
    query = db.query(HealthReading).filter(HealthReading.user_id == current_user.id)
    
    if start_date:
        query = query.filter(HealthReading.timestamp >= start_date)
    if end_date:
        query = query.filter(HealthReading.timestamp <= end_date)
    
    query = query.order_by(HealthReading.timestamp.desc())
    
    if limit:
        query = query.limit(limit)
    
    readings = query.all()
    
    return HealthReadingsResponse(
        success=True,
        readings=[HealthReadingDto(
            id=r.id,
            user_id=r.user_id,
            timestamp=r.timestamp,
            heart_rate=r.heart_rate,
            systolic_bp=r.systolic_bp,
            diastolic_bp=r.diastolic_bp,
            spo2=r.spo2,
            temperature=r.temperature,
            ecg_data=r.ecg_data,
            ppg_data=r.ppg_data
        ) for r in readings]
    )


@router.post("", response_model=HealthReadingsResponse)
async def submit_health_reading(
    reading: HealthReadingCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Yeni sağlık ölçümü kaydet
    
    Bu endpoint sensörlerden gelen veriyi kaydeder.
    """
    # Create new reading
    new_reading = HealthReading(
        user_id=current_user.id,
        timestamp=reading.timestamp or int(datetime.now().timestamp() * 1000),
        heart_rate=reading.heart_rate,
        systolic_bp=reading.systolic_bp,
        diastolic_bp=reading.diastolic_bp,
        spo2=reading.spo2,
        temperature=reading.temperature,
        ecg_data=reading.ecg_data,
        ppg_data=reading.ppg_data
    )
    
    db.add(new_reading)
    db.commit()
    db.refresh(new_reading)
    
    return HealthReadingsResponse(
        success=True,
        readings=[HealthReadingDto(
            id=new_reading.id,
            user_id=new_reading.user_id,
            timestamp=new_reading.timestamp,
            heart_rate=new_reading.heart_rate,
            systolic_bp=new_reading.systolic_bp,
            diastolic_bp=new_reading.diastolic_bp,
            spo2=new_reading.spo2,
            temperature=new_reading.temperature,
            ecg_data=new_reading.ecg_data,
            ppg_data=new_reading.ppg_data
        )],
        message="Ölçüm başarıyla kaydedildi"
    )


@router.get("/{reading_id}", response_model=HealthReadingDto)
async def get_health_reading(
    reading_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Tek bir sağlık ölçümünün detayını getir
    """
    reading = db.query(HealthReading).filter(
        HealthReading.id == reading_id,
        HealthReading.user_id == current_user.id
    ).first()
    
    if not reading:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Ölçüm bulunamadı"
        )
    
    return HealthReadingDto(
        id=reading.id,
        user_id=reading.user_id,
        timestamp=reading.timestamp,
        heart_rate=reading.heart_rate,
        systolic_bp=reading.systolic_bp,
        diastolic_bp=reading.diastolic_bp,
        spo2=reading.spo2,
        temperature=reading.temperature,
        ecg_data=reading.ecg_data,
        ppg_data=reading.ppg_data
    )

