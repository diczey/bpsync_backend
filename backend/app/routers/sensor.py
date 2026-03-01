from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import text

from backend.app.database import get_sensor_db
from backend.app.schemas.sensor import WristbandDataIn, ECGDataIn

router = APIRouter(prefix="/sensor", tags=["Sensor"])


@router.post("/wristband")
def ingest_wristband(payload: WristbandDataIn, db: Session = Depends(get_sensor_db)):
    db.execute(
        text("""
            INSERT INTO wristband_data (time, patient_id, heart_rate, spo2, movement)
            VALUES (:time, :patient_id, :heart_rate, :spo2, :movement)
        """),
        payload.model_dump()
    )
    db.commit()
    return {"success": True}


@router.post("/ecg")
def ingest_ecg(payload: ECGDataIn, db: Session = Depends(get_sensor_db)):
    db.execute(
        text("""
            INSERT INTO ecg_data (time, patient_id, ecg_value)
            VALUES (:time, :patient_id, :ecg_value)
        """),
        payload.model_dump()
    )
    db.commit()
    return {"success": True}