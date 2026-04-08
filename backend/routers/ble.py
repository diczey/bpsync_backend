"""
BLE Router — Mobile-first BLE ingestion and optional server BLE debug endpoints

Production flow:
  wrist module -> Android phone -> /ble/mobile-connected + /ble/mobile-frame

Legacy server-side BLE endpoints remain available only for local hardware lab
setups where the backend process can directly access a Bluetooth adapter.

Prefix: /ble  (registered in backend/main.py)
"""

from datetime import datetime

from fastapi import APIRouter, HTTPException, Depends
from typing import Optional
from pydantic import BaseModel

from ble.manager import get_ble_manager, BLEManager, WRIST_DEVICE_NAME, SCAN_TIMEOUT_S
from ble.data_manager import get_data_manager, init_data_manager, DataManager
from ble.frame import FrameProcessResult
from backend.database import SensorSessionLocal, get_db
from backend.utils.security import get_current_user
from backend.models.user import User
from backend.utils.sensor_identity import canonical_sensor_user_key
from sqlalchemy.orm import Session

router = APIRouter()


# ──────────────────────────────────────────────────────────────────────────────
#  HELPER — Dependency Injection
# ──────────────────────────────────────────────────────────────────────────────

def require_ble() -> BLEManager:
    """
    Returns the optional server-side BLE manager used for local lab debugging.
    Raises 503 when server BLE is disabled or not initialized.
    """
    mgr = get_ble_manager()
    if mgr is None:
        raise HTTPException(
            status_code=503,
            detail=(
                "Server-side BLE is unavailable. Production mobile BLE should connect "
                "from Android and upload frames through /ble/mobile-frame. "
                "Enable the optional backend BLE manager only for local hardware lab setups."
            )
        )
    return mgr


def require_data_manager() -> DataManager:
    """Return the shared DataManager used by BLE uploads and optional server BLE."""
    mgr = get_data_manager()
    if mgr is None:
        mgr = init_data_manager(db_factory=SensorSessionLocal)
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


class MobileFrameUploadRequest(BaseModel):
    raw_frame: str
    source_device_name: Optional[str] = None
    source_device_address: Optional[str] = None


def ensure_ble_calibration_started(
    current_user: User,
    db: Session,
    *,
    touch_last_connected: bool,
) -> None:
    now = datetime.utcnow()
    changed = False
    if current_user.ble_calibration_started_at is None:
        current_user.ble_calibration_started_at = now
        changed = True
    if touch_last_connected:
        current_user.last_ble_connected_at = now
        changed = True
    if not changed:
        return
    db.add(current_user)
    db.commit()
    db.refresh(current_user)


# ──────────────────────────────────────────────────────────────────────────────
#  ENDPOINTS
# ──────────────────────────────────────────────────────────────────────────────

@router.get("/status", response_model=BLEStatusResponse, summary="Server BLE status (debug-only)")
async def get_ble_status(
    ble: BLEManager = Depends(require_ble),
    current_user: User = Depends(get_current_user),
):
    """Return backend-controlled BLE status for local lab debugging only."""
    return ble.get_status()


@router.post("/scan", response_model=ScanResult, summary="Server BLE scan (debug-only)")
async def scan_for_device(
    ble: BLEManager = Depends(require_ble),
    current_user: User = Depends(get_current_user),
):
    """
    Legacy backend-side BLE scan for local hardware lab setups.
    The production Android app scans directly on-device instead of using this endpoint.
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


@router.post("/start", response_model=CommandResponse, summary="Server BLE start (debug-only)")
async def start_streaming(
    ble: BLEManager = Depends(require_ble),
    current_user: User = Depends(get_current_user),
):
    """
    Legacy backend-side START command for local hardware lab setups.
    Production mobile BLE sends START directly from Android after GATT connection.
    """
    status = ble.get_status()
    if not status["connected"]:
        raise HTTPException(status_code=409, detail="Device is not connected.")

    # Use email as the stable sensor owner key so historical and new rows stay grouped.
    ble._dm.set_user_id(canonical_sensor_user_key(current_user))

    # Set user age for ML inference directly from user's age property
    ble._dm.set_user_age(current_user.age)

    ok = await ble.send_command("START")
    if ok:
        return CommandResponse(success=True, message="START command sent.")
    return CommandResponse(success=False, message="Command could not be sent.")


@router.post("/stop", response_model=CommandResponse, summary="Server BLE stop (debug-only)")
async def stop_streaming(
    ble: BLEManager = Depends(require_ble),
    current_user: User = Depends(get_current_user),
):
    """Legacy backend-side STOP command for local hardware lab setups."""
    status = ble.get_status()
    if not status["connected"]:
        raise HTTPException(status_code=409, detail="Device is not connected.")

    ok = await ble.send_command("STOP")
    if ok:
        return CommandResponse(success=True, message="STOP command sent.")
    return CommandResponse(success=False, message="Command could not be sent.")


@router.post("/mobile-connected", response_model=CommandResponse, summary="Mark mobile BLE connection as active")
async def mark_mobile_connected(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Mark the authenticated user's BLE calibration window as started.

    The Android app calls this right after a successful GATT connection so the
    3-day personalization countdown can start immediately.
    """
    ensure_ble_calibration_started(current_user, db, touch_last_connected=True)
    return CommandResponse(success=True, message="BLE connection recorded.")


@router.post("/mobile-frame", response_model=FrameProcessResult, summary="Upload a BLE frame from the mobile app")
async def ingest_mobile_frame(
    request: MobileFrameUploadRequest,
    data_manager: DataManager = Depends(require_data_manager),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Accept a raw JSON BLE frame uploaded by the Android app after it receives a
    Notify packet from the wrist module.

    This is the primary production flow:
      wrist module -> Android phone -> POST /ble/mobile-frame -> DataManager
    """
    raw_frame = (request.raw_frame or "").strip()
    if not raw_frame:
        raise HTTPException(status_code=400, detail="raw_frame is required.")

    ensure_ble_calibration_started(current_user, db, touch_last_connected=False)
    sensor_owner_key = canonical_sensor_user_key(current_user)
    data_manager.set_user_context(sensor_owner_key, current_user.age)
    result = await data_manager.process_frame(raw_frame, user_id=sensor_owner_key)
    if not result.success:
        raise HTTPException(status_code=400, detail=result.message or "BLE frame processing failed.")
    return result


@router.get("/stats", summary="DataManager frame statistics")
async def get_data_stats(
    data_manager: DataManager = Depends(require_data_manager),
    current_user: User = Depends(get_current_user),
):
    """Frame processing stats: processed, failed, success_rate, bp_inferences, buffer_fill."""
    return data_manager.get_stats(canonical_sensor_user_key(current_user))
