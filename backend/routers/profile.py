"""
Profile Router - User profile management

Exposes CRUD operations for the user's own profile data:
  GET    /profile — read current profile
  PUT    /profile — update one or more profile fields
  DELETE /profile — permanently delete the account

All three endpoints are protected by JWT authentication and operate only
on the currently authenticated user's record (no admin override here).
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from backend.database import get_db
from backend.models.user import User
from backend.schemas.auth import ProfileResponse, ProfileUpdateRequest, UserDto
from backend.utils.security import get_current_user

router = APIRouter()


@router.get("", response_model=ProfileResponse)
async def get_profile(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Return the authenticated user's profile data.
    The mobile Profile screen calls this on load to populate all fields.
    """
    return ProfileResponse(
        success=True,
        user=UserDto(
            id=current_user.id,
            email=current_user.email,
            name=current_user.name,
            avatar_url=current_user.avatar_url,
            date_of_birth=current_user.date_of_birth,
            gender=current_user.gender,
            weight=current_user.weight,
            height=current_user.height,
            blood_type=current_user.blood_type,
            emergency_contact=current_user.emergency_contact,
            last_checkup_date=current_user.last_checkup_date
        )
    )


@router.put("", response_model=ProfileResponse)
async def update_profile(
    request: ProfileUpdateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Update writable profile fields for the authenticated user.

    Only fields explicitly provided in the request body are written;
    omitted fields retain their existing values (partial update / PATCH semantics
    exposed as PUT because the mobile uses PUT with the full profile form).
    """
    # Update fields if provided
    if request.name is not None:
        current_user.name = request.name
    if request.date_of_birth is not None:
        current_user.date_of_birth = request.date_of_birth
    if request.gender is not None:
        current_user.gender = request.gender
    if request.weight is not None:
        current_user.weight = request.weight
    if request.height is not None:
        current_user.height = request.height
    if request.blood_type is not None:
        current_user.blood_type = request.blood_type
    if request.emergency_contact is not None:
        current_user.emergency_contact = request.emergency_contact
    if request.last_checkup_date is not None:
        current_user.last_checkup_date = request.last_checkup_date
    
    db.commit()
    db.refresh(current_user)
    
    return ProfileResponse(
        success=True,
        user=UserDto(
            id=current_user.id,
            email=current_user.email,
            name=current_user.name,
            avatar_url=current_user.avatar_url,
            date_of_birth=current_user.date_of_birth,
            gender=current_user.gender,
            weight=current_user.weight,
            height=current_user.height,
            blood_type=current_user.blood_type,
            emergency_contact=current_user.emergency_contact,
            last_checkup_date=current_user.last_checkup_date
        ),
        message="Profil güncellendi"
    )


@router.delete("")
async def delete_account(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Permanently delete the authenticated user's account.

    WARNING: This action is irreversible. All user data in PostgreSQL is removed.
    Sensor data in TimescaleDB (bp_readings, wristband_data) is NOT cascaded
    because TimescaleDB tables have no foreign-key constraint to users.
    If full data removal is required, a separate cleanup job is needed.
    """
    db.delete(current_user)
    db.commit()
    
    return {"success": True, "message": "Hesap silindi"}

