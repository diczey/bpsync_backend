"""
Authentication Router - Login, Logout, Register
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from datetime import timedelta

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

router = APIRouter()


@router.post("/login", response_model=LoginResponse)
async def login(request: LoginRequest, db: Session = Depends(get_db)):
    """
    Kullanıcı girişi
    
    - Email ve şifre ile kimlik doğrulama
    - JWT token döndürür
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
            blood_type=user.blood_type,
            emergency_contact=user.emergency_contact
        ),
        message="Giriş başarılı"
    )


@router.post("/logout")
async def logout(current_user: User = Depends(get_current_user)):
    """
    Kullanıcı çıkışı
    
    Not: JWT stateless olduğu için sunucu tarafında token geçersiz kılma yoktur.
    Mobil uygulama token'ı silmelidir.
    """
    return {"success": True, "message": "Çıkış başarılı"}


@router.post("/register", response_model=LoginResponse)
async def register(request: LoginRequest, db: Session = Depends(get_db)):
    """
    Yeni kullanıcı kaydı
    """
    # Check if user already exists
    existing_user = db.query(User).filter(User.email == request.email).first()
    if existing_user:
        return LoginResponse(
            success=False,
            message="Bu email adresi zaten kayıtlı"
        )
    
    # Create new user
    user = User(
        email=request.email,
        hashed_password=get_password_hash(request.password),
        name=request.email.split("@")[0].capitalize()
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    
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
            blood_type=user.blood_type,
            emergency_contact=user.emergency_contact
        ),
        message="Kayıt başarılı"
    )

