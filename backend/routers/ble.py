"""
BLE Router — BLE Connection Control Endpoints

Endpoints for the mobile app to scan, connect, start/stop streaming,
and monitor the BLE connection to the wrist module.

Prefix: /ble  (registered in backend/main.py)
"""

from fastapi import APIRouter, HTTPException, Depends
from typing import List, Optional
from pydantic import BaseModel

from ble.manager import get_ble_manager, BLEManager, WRIST_DEVICE_NAME, SCAN_TIMEOUT_S
from backend.utils.security import get_current_user
from backend.models.user import User

router = APIRouter()


# ──────────────────────────────────────────────────────────────────────────────
#  HELPER — Dependency Injection
# ──────────────────────────────────────────────────────────────────────────────

def require_ble() -> BLEManager:
    """
    Returns the BLEManager singleton.
    Raises 503 Service Unavailable if it has not been initialized yet.
    """
    mgr = get_ble_manager()
    if mgr is None:
        raise HTTPException(
            status_code=503,
            detail=(
                "BLEManager is not initialized. "
                "Add an init_ble_manager() call to the main.py lifespan function."
            )
        )
    return mgr


# ──────────────────────────────────────────────────────────────────────────────
#  REQUEST / RESPONSE MODELS
# ──────────────────────────────────────────────────────────────────────────────

class CommandResponse(BaseModel):
    success: bool
    message: str


class BLEStatusResponse(BaseModel):
    available: bool
    connected: bool
    device_name: str
    device_address: Optional[str] = None
    connected_at: Optional[str] = None
    data_stats: dict


class ScanResult(BaseModel):
    found: bool
    device_name: Optional[str] = None
    device_address: Optional[str] = None
    message: str


# ──────────────────────────────────────────────────────────────────────────────
#  ENDPOINTS
# ──────────────────────────────────────────────────────────────────────────────

@router.get("/status", response_model=BLEStatusResponse, summary="BLE connection status")
async def get_ble_status(
    ble: BLEManager = Depends(require_ble),
    current_user: User = Depends(get_current_user),
):
    """Current BLE connection status. Used by mobile 'Find Sensor' screen."""
    return ble.get_status()


@router.post("/scan", response_model=ScanResult, summary="Scan for wrist device")
async def scan_for_device(
    ble: BLEManager = Depends(require_ble),
    current_user: User = Depends(get_current_user),
):
    """
    Triggers a BLE scan for the wrist module (BPSync-Wrist).
    Called by the mobile 'Find Sensor' / 'Scan' button.
    Scan duration: up to SCAN_TIMEOUT_S seconds.
    """
    try:
        from bleak import BleakScanner
        devices = await BleakScanner.discover(timeout=SCAN_TIMEOUT_S)
        for d in devices:
            if d.name and WRIST_DEVICE_NAME.lower() in d.name.lower():
                return ScanResult(
                    found=True,
                    device_name=d.name,
                    device_address=d.address,
                    message=f"Device found: {d.name} ({d.address})",
                )
        return ScanResult(found=False, message="BPSync-Wrist not found. Make sure the device is on and nearby.")
    except ImportError:
        raise HTTPException(status_code=503, detail="bleak library not installed on server.")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Scan error: {exc}")


@router.post("/start", response_model=CommandResponse, summary="Start BLE streaming")
async def start_streaming(
    ble: BLEManager = Depends(require_ble),
    current_user: User = Depends(get_current_user),
):
    """
    Sends START command to the wrist module. Device begins 10 Hz JSON streaming.
    Also wires the current user's ID and age into DataManager so readings are
    correctly attributed in TimescaleDB.
    """
    status = ble.get_status()
    if not status["connected"]:
        raise HTTPException(status_code=409, detail="Device is not connected.")

    # Assign current user to DataManager so bp_readings are written with correct user_id
    ble._dm.set_user_id(current_user.id)

    # Set user age for ML inference directly from user's age property
    ble._dm.set_user_age(current_user.age)

    ok = await ble.send_command("START")
    if ok:
        return CommandResponse(success=True, message="START command sent.")
    return CommandResponse(success=False, message="Command could not be sent.")


@router.post("/stop", response_model=CommandResponse, summary="Stop BLE streaming")
async def stop_streaming(
    ble: BLEManager = Depends(require_ble),
    current_user: User = Depends(get_current_user),
):
    """Sends STOP command. Device stops streaming; BLE connection stays open."""
    status = ble.get_status()
    if not status["connected"]:
        raise HTTPException(status_code=409, detail="Device is not connected.")

    ok = await ble.send_command("STOP")
    if ok:
        return CommandResponse(success=True, message="STOP command sent.")
    return CommandResponse(success=False, message="Command could not be sent.")


@router.get("/stats", summary="DataManager frame statistics")
async def get_data_stats(
    ble: BLEManager = Depends(require_ble),
    current_user: User = Depends(get_current_user),
):
    """Frame processing stats: processed, failed, success_rate, bp_inferences, buffer_fill."""
    status = ble.get_status()
    return status.get("data_stats", {})
