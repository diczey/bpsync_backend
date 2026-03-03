"""
BPSync Backend - Quick Start Script

Kullanim:
    python run.py

Bu script backend sunucusunu baslatir.
API dokumantasyonu: http://localhost:8000/docs
"""
import uvicorn
from backend.config import settings


if __name__ == "__main__":
    print("")
    print("=" * 60)
    print("                  BPSync Backend")
    print("=" * 60)
    print(f"  Starting server...")
    print(f"  URL: http://{settings.host}:{settings.port}")
    print(f"  Docs: http://{settings.host}:{settings.port}/docs")
    print(f"  Mock Data: {'ENABLED' if settings.use_mock_data else 'DISABLED'}")
    print("=" * 60)
    print("")
    
    uvicorn.run(
        "backend.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug
    )
