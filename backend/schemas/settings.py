from typing import Optional

from pydantic import BaseModel, Field


class UserSettingsDto(BaseModel):
    language: str = "en"
    push_notifications_enabled: bool = Field(
        True,
        serialization_alias="push_notifications_enabled",
    )
    weekly_reports_enabled: bool = Field(
        False,
        serialization_alias="weekly_reports_enabled",
    )

    class Config:
        populate_by_name = True


class SettingsUpdateRequest(BaseModel):
    language: Optional[str] = None
    push_notifications_enabled: Optional[bool] = Field(
        None,
        serialization_alias="push_notifications_enabled",
    )
    weekly_reports_enabled: Optional[bool] = Field(
        None,
        serialization_alias="weekly_reports_enabled",
    )

    class Config:
        populate_by_name = True


class SettingsResponse(BaseModel):
    success: bool
    settings: Optional[UserSettingsDto] = None
    message: Optional[str] = None
