"""
Trend Schemas
"""
from pydantic import BaseModel, Field
from typing import Optional, List


class TrendDataPoint(BaseModel):
    """Single trend data point"""
    timestamp: int
    value: float


class TrendDataDto(BaseModel):
    """Trend data transfer object"""
    type: str
    data_points: List[TrendDataPoint] = Field(..., serialization_alias="data_points")
    average: float
    min: float
    max: float
    
    class Config:
        populate_by_name = True


class TrendResponse(BaseModel):
    """Trend response"""
    success: bool
    trends: List[TrendDataDto]
    message: Optional[str] = None

