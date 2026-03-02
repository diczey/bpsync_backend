"""
BPSync Backend - Main Application Entry Point

Bu dosya FastAPI uygulamasının ana giriş noktasıdır.
Tüm router'ları ve middleware'leri burada yapılandırıyoruz.
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from backend.app.config import settings
from backend.app.database import create_tables

# Import routers
from backend.app.routers import auth, dashboard, readings, trends, reports, notifications, profile, sensor


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan events"""
    # Startup: Create database tables
    create_tables()
    print(f"[OK] {settings.app_name} Backend started!")
    print(f"[INFO] Mock data mode: {'ENABLED' if settings.use_mock_data else 'DISABLED'}")
    yield
    # Shutdown
    print(f"[BYE] {settings.app_name} Backend shutting down...")


# Create FastAPI application
app = FastAPI(
    title=f"{settings.app_name} API",
    description="""
## BPSync Backend API

Kan basinci takip uygulamasi icin RESTful API.

### Ozellikler:
- JWT tabanli kimlik dogrulama
- Saglik verileri yonetimi (ECG, PPG, BP, SpO2)
- Trend analizi ve haftalik raporlar
- Bildirim sistemi
- Kullanici profil yonetimi

### Gelistirme Modu:
Gercek sensor verileri olmadan mock verilerle calisabilirsiniz.
    """,
    version="1.0.0",
    lifespan=lifespan
)

# Configure CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins + ["*"],  # Allow all in development
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(auth.router, prefix="/auth", tags=["Authentication"])
app.include_router(dashboard.router, prefix="/dashboard", tags=["Dashboard"])
app.include_router(readings.router, prefix="/readings", tags=["Health Readings"])
app.include_router(trends.router, prefix="/trends", tags=["Trends"])
app.include_router(reports.router, prefix="/reports", tags=["Reports"])
app.include_router(notifications.router, prefix="/notifications", tags=["Notifications"])
app.include_router(profile.router, prefix="/profile", tags=["Profile"])
app.include_router(sensor.router)


@app.get("/", tags=["Root"])
async def root():
    """API health check endpoint"""
    return {
        "message": f"Welcome to {settings.app_name} API",
        "version": "1.0.0",
        "status": "running",
        "docs": "/docs",
        "mock_mode": settings.use_mock_data
    }


@app.get("/health", tags=["Root"])
async def health_check():
    """Health check for load balancers"""
    return {"status": "healthy"}

