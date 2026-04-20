from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from backend.database import get_db
from backend.models.user import User
from backend.schemas.settings import SettingsResponse, SettingsUpdateRequest, UserSettingsDto
from backend.utils.security import get_current_user

router = APIRouter()

SUPPORTED_LANGUAGES = {"en", "tr"}


def _build_settings(current_user: User) -> UserSettingsDto:
    return UserSettingsDto(
        language=(current_user.preferred_language or "en").lower(),
        push_notifications_enabled=bool(current_user.push_notifications_enabled),
        weekly_reports_enabled=bool(current_user.weekly_reports_enabled),
    )


@router.get("", response_model=SettingsResponse)
async def get_settings(
    current_user: User = Depends(get_current_user),
):
    return SettingsResponse(
        success=True,
        settings=_build_settings(current_user),
    )


@router.put("", response_model=SettingsResponse)
async def update_settings(
    request: SettingsUpdateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if request.language is not None:
        normalized_language = request.language.strip().lower()
        if normalized_language not in SUPPORTED_LANGUAGES:
            return SettingsResponse(
                success=False,
                settings=None,
                message="Supported languages are 'en' and 'tr'.",
            )
        current_user.preferred_language = normalized_language

    if request.push_notifications_enabled is not None:
        current_user.push_notifications_enabled = request.push_notifications_enabled

    if request.weekly_reports_enabled is not None:
        current_user.weekly_reports_enabled = request.weekly_reports_enabled

    db.commit()
    db.refresh(current_user)

    return SettingsResponse(
        success=True,
        settings=_build_settings(current_user),
        message="Settings updated successfully.",
    )
