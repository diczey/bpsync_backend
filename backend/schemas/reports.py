"""
Report Schemas
"""
from pydantic import BaseModel, Field
from typing import Optional, List


class DailySummaryDto(BaseModel):
    """Daily summary for reports"""
    date: str
    avg_heart_rate: float = Field(..., serialization_alias="avg_heart_rate")
    avg_bp: str = Field(..., serialization_alias="avg_bp")
    readings_count: int = Field(..., serialization_alias="readings_count")
    
    class Config:
        populate_by_name = True


class WeeklyReportDto(BaseModel):
    """Weekly report data"""
    week_start: str = Field(..., serialization_alias="week_start")
    week_end: str = Field(..., serialization_alias="week_end")
    avg_heart_rate: float = Field(..., serialization_alias="avg_heart_rate")
    avg_systolic: float = Field(..., serialization_alias="avg_systolic")
    avg_diastolic: float = Field(..., serialization_alias="avg_diastolic")
    avg_spo2: float = Field(..., serialization_alias="avg_spo2")
    readings_count: int = Field(..., serialization_alias="readings_count")
    health_score: int = Field(..., serialization_alias="health_score")
    recommendations: List[str]
    daily_summaries: List[DailySummaryDto] = Field(..., serialization_alias="daily_summaries")
    
    class Config:
        populate_by_name = True


class WeeklyReportResponse(BaseModel):
    """Weekly report response"""
    success: bool
    report: Optional[WeeklyReportDto] = None
    message: Optional[str] = None

