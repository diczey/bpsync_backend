from pydantic import BaseModel
from datetime import datetime
from typing import Optional


class WristbandDataIn(BaseModel):
    time: datetime
    patient_id: int
    heart_rate: Optional[int] = None
    spo2: Optional[float] = None
    movement: Optional[float] = None


class ECGDataIn(BaseModel):
    time: datetime
    patient_id: int
    ecg_value: float