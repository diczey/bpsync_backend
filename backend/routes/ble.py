"""
BLE Router — BLE Connection Control Endpoints

This router provides REST API endpoints for querying the BLE Manager status
and starting/stopping streaming.

Prefix: /ble
Tags: ["BLE"]

INTEGRATION NOTE:
  For this router to work, add the following to main.py:
    from routes import ble
    app.include_router(ble.router, prefix="/ble", tags=["BLE"])

  The lifespan function must also initialize and stop the BLEManager.
  (Details: docs/ble_data_manager.md)
"""

from fastapi import APIRouter, HTTPException, Depends
from typing import Optional
from pydantic import BaseModel

from services.ble_manager import get_ble_manager, BLEManager

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


# ──────────────────────────────────────────────────────────────────────────────
#  ENDPOINTS
# ──────────────────────────────────────────────────────────────────────────────

@router.get("/status", response_model=BLEStatusResponse, summary="BLE connection status")
async def get_ble_status(ble: BLEManager = Depends(require_ble)):
    """
    Returns the current status of the BLE connection to the wrist module.

    Response fields:
    - **available**: Is the `bleak` library installed?
    - **connected**: Is the device currently connected?
    - **device_name**: Name of the target / connected device
    - **device_address**: Connected BLE MAC address (null if not connected)
    - **connected_at**: Connection establishment time (ISO 8601)
    - **data_stats**: DataManager statistics (processed/failed frame counts)
    """
    return ble.get_status()


@router.post(
    "/start",
    response_model=CommandResponse,
    summary="Start BLE streaming"
)
async def start_streaming(ble: BLEManager = Depends(require_ble)):
    """
    Sends a `START` command to the wrist module.

    After receiving this command, the device begins sending JSON frames
    via BLE Notify at 10 Hz.

    Returns 409 Conflict if the device is not connected.
    """
    status = ble.get_status()
    if not status["connected"]:
        raise HTTPException(
            status_code=409,
            detail="Device is not connected. Wait for the BLE connection to be established."
        )

    ok = await ble.send_command("START")
    if ok:
        return CommandResponse(success=True, message="START command sent.")
    return CommandResponse(success=False, message="Command could not be sent.")


@router.post(
    "/stop",
    response_model=CommandResponse,
    summary="Stop BLE streaming"
)
async def stop_streaming(ble: BLEManager = Depends(require_ble)):
    """
    Sends a `STOP` command to the wrist module.

    The device stops streaming; the BLE connection remains open.
    """
    status = ble.get_status()
    if not status["connected"]:
        raise HTTPException(
            status_code=409,
            detail="Device is not connected."
        )

    ok = await ble.send_command("STOP")
    if ok:
        return CommandResponse(success=True, message="STOP command sent.")
    return CommandResponse(success=False, message="Command could not be sent.")


@router.get(
    "/stats",
    summary="DataManager statistics"
)
async def get_data_stats(ble: BLEManager = Depends(require_ble)):
    """
    Returns frame processing statistics from DataManager.

    Response fields:
    - **processed**: Frames successfully written to the DB
    - **failed**: Frames with errors or that failed to parse
    - **total**: Total frames received
    - **success_rate**: Success rate (%)
    """
    status = ble.get_status()
    return status.get("data_stats", {})
