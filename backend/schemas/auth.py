"""
Authentication Schemas (DTOs)

Bu şemalar Android uygulamasının beklediği JSON formatına uygun.
HealthData.kt dosyasındaki DTO'larla eşleşiyor.
"""
from pydantic import BaseModel, EmailStr, Field
from typing import Optional


class UserDto(BaseModel):
    """User data transfer object"""
    id: str
    email: str
    name: str
    avatar_url: Optional[str] = Field(None, serialization_alias="avatar_url")
    date_of_birth: Optional[str] = Field(None, serialization_alias="date_of_birth")
    gender: Optional[str] = Field(None, serialization_alias="gender")
    weight: Optional[str] = Field(None, serialization_alias="weight")
    height: Optional[str] = Field(None, serialization_alias="height")
    blood_type: Optional[str] = Field(None, serialization_alias="blood_type")
    emergency_contact: Optional[str] = Field(None, serialization_alias="emergency_contact")
    last_checkup_date: Optional[str] = Field(None, serialization_alias="last_checkup_date")
    
    class Config:
        from_attributes = True
        populate_by_name = True


class LoginRequest(BaseModel):
    """Login request body"""
    email: EmailStr
    password: str = Field(..., min_length=6)


class LoginResponse(BaseModel):
    """Login response"""
    success: bool
    token: Optional[str] = None
    user: Optional[UserDto] = None
    message: Optional[str] = None


class ProfileUpdateRequest(BaseModel):
    """Profile update request body"""
    name: Optional[str] = None
    date_of_birth: Optional[str] = Field(None, serialization_alias="date_of_birth")
    gender: Optional[str] = Field(None, serialization_alias="gender")
    weight: Optional[str] = Field(None, serialization_alias="weight")
    height: Optional[str] = Field(None, serialization_alias="height")
    blood_type: Optional[str] = Field(None, serialization_alias="blood_type")
    emergency_contact: Optional[str] = Field(None, serialization_alias="emergency_contact")
    last_checkup_date: Optional[str] = Field(None, serialization_alias="last_checkup_date")


class ProfileResponse(BaseModel):
    """Profile response"""
    success: bool
    user: Optional[UserDto] = None
    message: Optional[str] = None

