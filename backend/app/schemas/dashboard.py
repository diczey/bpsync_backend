"""
Dashboard Schemas
"""
from pydantic import BaseModel, Field
from typing import Optional


class DashboardSummaryDto(BaseModel):
    """Dashboard summary data"""
    latest_heart_rate: int = Field(..., serialization_alias="latest_heart_rate")
    latest_systolic: int = Field(..., serialization_alias="latest_systolic")
    latest_diastolic: int = Field(..., serialization_alias="latest_diastolic")
    latest_spo2: int = Field(..., serialization_alias="latest_spo2")
    latest_temperature: float = Field(..., serialization_alias="latest_temperature")
    health_status: str = Field(..., serialization_alias="health_status")
    last_updated: int = Field(..., serialization_alias="last_updated")
    
    class Config:
        populate_by_name = True


class DashboardResponse(BaseModel):
    """Dashboard response"""
    success: bool
    summary: Optional[DashboardSummaryDto] = None
    message: Optional[str] = None

