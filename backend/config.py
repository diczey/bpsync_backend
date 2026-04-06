"""
BPSync Backend Configuration
"""
from pydantic_settings import BaseSettings
from typing import List
import os


class Settings(BaseSettings):
    """Application settings loaded from environment variables"""
    
    # Application
    app_name: str = "BPSync"
    app_env: str = "development"
    debug: bool = True
    
    # Server
    host: str = "0.0.0.0"
    port: int = 8000
    
    # Database (PostgreSQL - database klasorundan)
    database_url: str = "postgresql://bpsync_app:bpsync_password@localhost:5432/bpsync"
    # TimescaleDB (Sensor DB)
    sensor_database_url: str = "postgresql://bpsync_sensor_app:bpsync_sensor_password@timescaledb:5432/bpsync_sensor"

    # JWT Authentication — must be set in .env as SECRET_KEY=<random-32+-char-string>
    secret_key: str
    algorithm: str = "HS256"
    access_token_expire_minutes: int = 1440  # 24 hours
    
    # CORS
    allowed_origins: str = "http://localhost:3000,http://localhost:8080"
    
    # Mock Data (for development without real sensors)
    use_mock_data: bool = False

    # BLE topology
    # The production architecture is wrist -> phone -> backend.
    # Keep server-side BLE scanning opt-in for local lab setups only.
    enable_server_ble: bool = False
    
    @property
    def cors_origins(self) -> List[str]:
        """Parse comma-separated CORS origins"""
        return [origin.strip() for origin in self.allowed_origins.split(",")]
    
    class Config:
        import os
        env_file = os.path.join(os.path.dirname(__file__), ".env")
        env_file_encoding = "utf-8"


# Global settings instance
settings = Settings()

