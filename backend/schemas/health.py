"""
Health Reading Schemas
"""
from pydantic import BaseModel, Field
from typing import Optional, List


class HealthReadingDto(BaseModel):
    """Health reading data transfer object"""
    id: str
    user_id: str = Field(..., serialization_alias="user_id")
    timestamp: int
    heart_rate: Optional[int] = Field(None, serialization_alias="heart_rate")
    systolic_bp: Optional[int] = Field(None, serialization_alias="systolic_bp")
    diastolic_bp: Optional[int] = Field(None, serialization_alias="diastolic_bp")
    spo2: Optional[int] = None
    temperature: Optional[float] = None
    ecg_data: Optional[List[float]] = Field(None, serialization_alias="ecg_data")
    ppg_data: Optional[List[float]] = Field(None, serialization_alias="ppg_data")
    
    class Config:
        from_attributes = True
        populate_by_name = True


class HealthReadingCreate(BaseModel):
    """Create health reading request"""
    timestamp: int
    heart_rate: Optional[int] = None
    systolic_bp: Optional[int] = None
    diastolic_bp: Optional[int] = None
    spo2: Optional[int] = None
    temperature: Optional[float] = None
    ecg_data: Optional[List[float]] = None
    ppg_data: Optional[List[float]] = None


class HealthReadingsResponse(BaseModel):
    """Health readings list response"""
    success: bool
    readings: List[HealthReadingDto]
    message: Optional[str] = None

