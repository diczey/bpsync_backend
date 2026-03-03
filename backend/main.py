"""
BPSync Backend - Main Application Entry Point

Bu dosya FastAPI uygulamasının ana giriş noktasıdır.
Tüm router'ları ve middleware'leri burada yapılandırıyoruz.
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from backend.config import settings
from backend.database import create_tables, SensorSessionLocal

# Import routers
from backend.routers import auth, dashboard, readings, trends, reports, notifications, profile
from backend.routers import ble as ble_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan events"""
    # Startup: create PostgreSQL tables
    create_tables()

    # Startup: launch BLE manager + data pipeline
    from ble.manager import init_ble_manager
    from ble.data_manager import DataManager
    dm = DataManager(db_factory=SensorSessionLocal)
    ble = init_ble_manager(dm)
    await ble.start()
    print(f"[OK] {settings.app_name} Backend started!")

    yield

    # Shutdown: stop BLE
    from ble.manager import get_ble_manager
    await get_ble_manager().stop()
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
    allow_origins=settings.cors_origins,
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
app.include_router(ble_router.router, prefix="/ble", tags=["BLE"])


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

