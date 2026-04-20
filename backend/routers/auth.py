"""
 ╔══════════════════════════════════════════════════════════════╗
 ║                 BPSync — Authentication Router               ║
 ╠══════════════════════════════════════════════════════════════╣
 ║  Endpoints: /auth/login, /auth/register, /auth/logout        ║
 ║  Purpose  : User identity, registration, JWT lifecycle       ║
 ╚══════════════════════════════════════════════════════════════╝
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from datetime import timedelta
from typing import Optional
from enum import Enum
from datetime import date

from backend.database import get_db
from backend.models.user import User
from backend.schemas.auth import LoginRequest, LoginResponse, UserDto
from backend.utils.security import (
    verify_password,
    get_password_hash,
    create_access_token,
    get_current_user,
)
from backend.config import settings
from pydantic import BaseModel, EmailStr

router = APIRouter()


# ══════════════════════════════════════════════════════════════
#  DATA MODELS & ENUMS
# ══════════════════════════════════════════════════════════════
# RegisterRequest collects extra profile fields (name, DOB)
class Gender(str, Enum):
    """Dropdown for frontend"""
    MALE = "Male"
    FEMALE = "Female"
    OTHER = "Other"

class BloodType(str, Enum):
    """Dropdown for frontend"""
    A_POSITIVE = "A+"
    A_NEGATIVE = "A-"
    B_POSITIVE = "B+"
    B_NEGATIVE = "B-"
    AB_POSITIVE = "AB+"
    AB_NEGATIVE = "AB-"
    O_POSITIVE = "O+"
    O_NEGATIVE = "O-"

class RegisterRequest(BaseModel):
    """Request body for POST /auth/register — matches Android RegisterRequest"""
    email: EmailStr
    password: str
    name: str                                         # Display name shown in the app
    date_of_birth: Optional[date] = None               # Format: YYYY-MM-DD
    gender: Optional[Gender] = None                      # e.g. "Male", "Female", "Other"
    weight: Optional[float] = None                      # Stored as string, e.g. "70 kg"
    height: Optional[int] = None                      # Stored as string, e.g. "175 cm"
    blood_type: Optional[BloodType] = None                  # e.g. "A+", "O-"
    emergency_contact: Optional[str] = None           # Free-text phone / name


# ══════════════════════════════════════════════════════════════
#  ENDPOINTS
# ══════════════════════════════════════════════════════════════
@router.post("/login", response_model=LoginResponse)
async def login(request: LoginRequest, db: Session = Depends(get_db)):
    """
    User login — validates email + password and returns a JWT access token.

    In mock mode (USE_MOCK_DATA=true) a user is auto-created on first login
    so the mobile app can be tested without a manual registration step.
    """
    # Find user by email
    user = db.query(User).filter(User.email == request.email).first()
    
    if not user:
        # Demo mode: Auto-create user if it doesn't exist
        if settings.use_mock_data:
            user = User(
                email=request.email,
                hashed_password=get_password_hash(request.password),
                name=request.email.split("@")[0].capitalize()
            )
            db.add(user)
            db.commit()
            db.refresh(user)
        else:
            return LoginResponse(
                success=False,
                message="Geçersiz email veya şifre"
            )
    
    # Verify password
    if not verify_password(request.password, user.hashed_password):
        return LoginResponse(
            success=False,
            message="Geçersiz email veya şifre"
        )
    
    # Create access token
    access_token = create_access_token(
        data={"sub": user.id},
        expires_delta=timedelta(minutes=settings.access_token_expire_minutes)
    )
    
    return LoginResponse(
        success=True,
        token=access_token,
        user=UserDto(
            id=user.id,
            email=user.email,
            name=user.name,
            avatar_url=user.avatar_url,
            date_of_birth=user.date_of_birth,
            gender=user.gender,
            weight=user.weight,
            height=user.height,
            blood_type=user.blood_type,
            emergency_contact=user.emergency_contact
        ),
        message="Giriş başarılı"
    )


@router.post("/logout")
async def logout(current_user: User = Depends(get_current_user)):
    """
    User logout — JWT is stateless so no server-side invalidation happens.
    The mobile app is responsible for deleting the stored token on its side.
    This endpoint exists so the mobile can signal logout intent and receive
    a structured success response.
    """
    return {"success": True, "message": "Çıkış başarılı"}


@router.post("/register", response_model=LoginResponse)
async def register(request: RegisterRequest, db: Session = Depends(get_db)):
    """
    New user registration.

    Accepts all profile fields collected in the mobile sign-up form
    (name, date_of_birth, gender, weight, height, blood_type, emergency_contact).
    On success returns a JWT so the mobile can immediately authenticate
    without a separate login step.
    """
    # Reject duplicate emails — each email must map to exactly one account
    existing_user = db.query(User).filter(User.email == request.email).first()
    if existing_user:
        return LoginResponse(
            success=False,
            message="This email address is already registered"
        )

    # Build the new User ORM object with all profile fields supplied at registration
    user = User(
        email=request.email,
        hashed_password=get_password_hash(request.password),
        name=request.name,                            # Use the name the user typed, not a derived value
        date_of_birth=request.date_of_birth,
        gender=request.gender,
        weight=request.weight,
        height=request.height,
        blood_type=request.blood_type,
        emergency_contact=request.emergency_contact,
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    # Issue a JWT immediately so the mobile can skip the login screen after registration
    access_token = create_access_token(
        data={"sub": user.id},
        expires_delta=timedelta(minutes=settings.access_token_expire_minutes)
    )

    return LoginResponse(
        success=True,
        token=access_token,
        user=UserDto(
            id=user.id,
            email=user.email,
            name=user.name,
            avatar_url=user.avatar_url,
            date_of_birth=user.date_of_birth,
            gender=user.gender,
            weight=user.weight,
            height=user.height,
            blood_type=user.blood_type,
            emergency_contact=user.emergency_contact
        ),
        message="Registration successful"
    )

