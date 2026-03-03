"""
Profile Router - User Profile Management
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
    Kullanıcı profilini getir
    """
    return ProfileResponse(
        success=True,
        user=UserDto(
            id=current_user.id,
            email=current_user.email,
            name=current_user.name,
            avatar_url=current_user.avatar_url,
            date_of_birth=current_user.date_of_birth,
            blood_type=current_user.blood_type,
            emergency_contact=current_user.emergency_contact
        )
    )


@router.put("", response_model=ProfileResponse)
async def update_profile(
    request: ProfileUpdateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Kullanıcı profilini güncelle
    """
    # Update fields if provided
    if request.name is not None:
        current_user.name = request.name
    if request.date_of_birth is not None:
        current_user.date_of_birth = request.date_of_birth
    if request.blood_type is not None:
        current_user.blood_type = request.blood_type
    if request.emergency_contact is not None:
        current_user.emergency_contact = request.emergency_contact
    
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
            blood_type=current_user.blood_type,
            emergency_contact=current_user.emergency_contact
        ),
        message="Profil güncellendi"
    )


@router.delete("")
async def delete_account(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Kullanıcı hesabını sil
    
    DİKKAT: Bu işlem geri alınamaz!
    """
    db.delete(current_user)
    db.commit()
    
    return {"success": True, "message": "Hesap silindi"}

