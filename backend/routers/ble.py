"""
BLE Router - Mobile-first BLE ingestion and optional server BLE debug endpoints.

Production flow:
  wrist/chest modules -> Android phone ->
  /ble/mobile/session/* + /ble/mobile/frames/*

Legacy server-side BLE endpoints remain available only for local hardware lab
setups where the backend process can directly access a Bluetooth adapter.

Prefix: /ble  (registered in backend/main.py)
"""

import asyncio
import json
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.orm import Session

from backend.database import SensorSessionLocal, get_db, get_sensor_db
from backend.models.user import User
from backend.utils.security import get_current_user
from backend.utils.sensor_identity import (
    canonical_sensor_user_key,
    sensor_user_clause,
    sensor_user_params,
)
from ble.data_manager import (
    DataManager,
    WAVEFORM_WINDOW_FRAMES,
    get_data_manager,
    init_data_manager,
)
from ble.frame import FrameProcessResult
from ble.manager import BLEManager, SCAN_TIMEOUT_S, WRIST_DEVICE_NAME, get_ble_manager

router = APIRouter()

PARTIAL_FRAME_BUFFER_LIMIT = 80


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
                "from Android and upload frames through /ble/mobile/frames/*. "
                "Enable the optional backend BLE manager only for local hardware lab setups."
            ),
        )
    return mgr


def require_data_manager() -> DataManager:
    """Return the shared DataManager used by BLE uploads and optional server BLE."""
    mgr = get_data_manager()
    if mgr is None:
        mgr = init_data_manager(db_factory=SensorSessionLocal)
    return mgr


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


class MobileDeviceEventRequest(BaseModel):
    role: str = "unknown"
    device_name: Optional[str] = None
    device_address: Optional[str] = None


class MobileSessionStatusResponse(BaseModel):
    success: bool
    wrist_connected: bool = False
    chest_connected: bool = False
    wrist_device_name: Optional[str] = None
    wrist_device_address: Optional[str] = None
    chest_device_name: Optional[str] = None
    chest_device_address: Optional[str] = None
    streaming: bool = False
    last_event_at: Optional[int] = None
    measurement_started_at: Optional[int] = None
    measurement_stopped_at: Optional[int] = None
    data_stats: dict = {}
    message: Optional[str] = None


class RawTimescaleDebugResponse(BaseModel):
    success: bool
    sensor_owner_key: str
    wrist_total_rows: int = 0
    ecg_total_rows: int = 0
    wrist_rows: list[dict[str, Any]] = []
    ecg_rows: list[dict[str, Any]] = []
    message: Optional[str] = None


@dataclass
class MobileFrameSyncState:
    wrist_frames: dict[int, dict] = field(default_factory=dict)
    chest_frames: dict[int, dict] = field(default_factory=dict)
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)


class MobileFrameSynchronizer:
    def __init__(self) -> None:
        self._states: dict[str, MobileFrameSyncState] = {}

    def reset_user(self, user_id: str) -> None:
        if not user_id:
            return
        self._states[user_id] = MobileFrameSyncState()

    async def ingest_partial_frame(
        self,
        *,
        role: str,
        raw_frame: str,
        user_id: str,
        data_manager: DataManager,
    ) -> FrameProcessResult:
        try:
            frame_data = json.loads(raw_frame)
        except json.JSONDecodeError as exc:
            return FrameProcessResult(
                success=False,
                seq_num=0,
                quality=0.0,
                message=f"Invalid JSON frame: {exc}",
            )

        if not isinstance(frame_data, dict):
            return FrameProcessResult(
                success=False,
                seq_num=0,
                quality=0.0,
                message="BLE frame payload must be a JSON object.",
            )

        seq_num = self._extract_seq(frame_data)
        if seq_num < 0:
            return FrameProcessResult(
                success=False,
                seq_num=0,
                quality=self._extract_quality(frame_data, role),
                message="BLE frame is missing a valid seq/sq field.",
            )

        state = self._state_for_user(user_id)
        merged_frame: Optional[dict] = None
        buffer_fill = f"0/{WAVEFORM_WINDOW_FRAMES}"

        async with state.lock:
            target_buffer = state.wrist_frames if role == "wrist" else state.chest_frames
            other_buffer = state.chest_frames if role == "wrist" else state.wrist_frames

            target_buffer[seq_num] = dict(frame_data)
            self._trim_buffer(target_buffer)

            matched_peer = other_buffer.get(seq_num)
            matched_count = min(
                sum(1 for seq in state.wrist_frames if seq in state.chest_frames),
                WAVEFORM_WINDOW_FRAMES,
            )
            buffer_fill = f"{matched_count}/{WAVEFORM_WINDOW_FRAMES}"

            if matched_peer is not None:
                wrist_frame = frame_data if role == "wrist" else matched_peer
                chest_frame = frame_data if role == "chest" else matched_peer
                target_buffer.pop(seq_num, None)
                other_buffer.pop(seq_num, None)
                merged_frame = self._merge_frames(wrist_frame=wrist_frame, chest_frame=chest_frame)

        if merged_frame is None:
            return FrameProcessResult(
                success=True,
                seq_num=seq_num,
                quality=self._extract_quality(frame_data, role),
                buffer_fill=buffer_fill,
                reading_created=False,
                message=f"{role.title()} frame buffered, waiting for matching seq.",
            )

        result = await data_manager.process_frame(json.dumps(merged_frame), user_id=user_id)
        if result.success and not result.message:
            result.message = f"Matched wrist/chest seq {seq_num} and forwarded for processing."
        return result

    def _state_for_user(self, user_id: str) -> MobileFrameSyncState:
        state = self._states.get(user_id)
        if state is None:
            state = MobileFrameSyncState()
            self._states[user_id] = state
        return state

    @staticmethod
    def _extract_seq(frame_data: dict) -> int:
        raw_value = frame_data.get("sq", frame_data.get("seq", -1))
        try:
            return int(raw_value)
        except (TypeError, ValueError):
            return -1

    @staticmethod
    def _extract_quality(frame_data: dict, role: str) -> float:
        quality_key = "qi_w" if role == "wrist" else "qi_c"
        raw_value = frame_data.get(quality_key, frame_data.get("qi", 0))
        try:
            quality = float(raw_value)
        except (TypeError, ValueError):
            return 0.0
        return quality * 100.0 if quality <= 1.0 else quality

    @staticmethod
    def _merge_frames(*, wrist_frame: dict, chest_frame: dict) -> dict:
        merged = dict(wrist_frame)
        seq_num = MobileFrameSynchronizer._extract_seq(wrist_frame)
        chest_seq = MobileFrameSynchronizer._extract_seq(chest_frame)
        wrist_qi = int(merged.get("qi_w", merged.get("qi", 0)) or 0)
        chest_qi = int(chest_frame.get("qi_c", chest_frame.get("qi", 0)) or 0)

        merged["sq"] = seq_num
        merged["cs"] = chest_seq if chest_seq >= 0 else seq_num
        merged["ep"] = int(chest_frame.get("ep", chest_frame.get("r_peak", 0)) or 0)
        merged["qi_w"] = wrist_qi
        merged["qi_c"] = chest_qi
        merged["qi"] = 1 if wrist_qi == 1 and chest_qi == 1 else 0
        merged["cx"] = int(chest_frame.get("cx", 0) or 0)
        merged["cy"] = int(chest_frame.get("cy", 0) or 0)
        merged["cz"] = int(chest_frame.get("cz", 0) or 0)

        if "ecg" in chest_frame:
            merged["ecg"] = chest_frame["ecg"]
        elif "ecg_value" in chest_frame:
            merged["ecg"] = chest_frame["ecg_value"]

        merged.setdefault("tp", wrist_frame.get("temperature", 0.0))
        merged.setdefault("ax", 0)
        merged.setdefault("ay", 0)
        merged.setdefault("az", 0)
        merged.setdefault("gx", 0)
        merged.setdefault("gy", 0)
        merged.setdefault("gz", 0)
        merged.setdefault("bt", 100)
        return merged

    @staticmethod
    def _trim_buffer(buffer: dict[int, dict]) -> None:
        while len(buffer) > PARTIAL_FRAME_BUFFER_LIMIT:
            oldest_seq = next(iter(buffer), None)
            if oldest_seq is None:
                return
            buffer.pop(oldest_seq, None)


mobile_frame_sync = MobileFrameSynchronizer()


@dataclass
class MobileSessionState:
    wrist_connected: bool = False
    chest_connected: bool = False
    wrist_device_name: Optional[str] = None
    wrist_device_address: Optional[str] = None
    chest_device_name: Optional[str] = None
    chest_device_address: Optional[str] = None
    streaming: bool = False
    last_event_at: Optional[datetime] = None
    measurement_started_at: Optional[datetime] = None
    measurement_stopped_at: Optional[datetime] = None
    last_message: Optional[str] = None
    last_error: Optional[str] = None
    wrist_frames_uploaded: int = 0
    chest_frames_uploaded: int = 0
    merged_frames_uploaded: int = 0
    readings_created: int = 0
    last_seq: int = 0
    buffer_fill: str = f"0/{WAVEFORM_WINDOW_FRAMES}"


class MobileSessionTracker:
    def __init__(self) -> None:
        self._states: dict[str, MobileSessionState] = {}

    def _state_for_user(self, user_id: str) -> MobileSessionState:
        state = self._states.get(user_id)
        if state is None:
            state = MobileSessionState()
            self._states[user_id] = state
        return state

    def mark_connected(
        self,
        user_id: str,
        *,
        role: str,
        device_name: Optional[str],
        device_address: Optional[str],
    ) -> None:
        state = self._state_for_user(user_id)
        now = datetime.utcnow()
        normalized_role = role.lower()
        if normalized_role == "wrist":
            state.wrist_connected = True
            state.wrist_device_name = device_name
            state.wrist_device_address = device_address
        elif normalized_role == "chest":
            state.chest_connected = True
            state.chest_device_name = device_name
            state.chest_device_address = device_address
        state.last_event_at = now
        state.last_message = f"{normalized_role.title()} connected."
        state.last_error = None

    def mark_disconnected(
        self,
        user_id: str,
        *,
        role: str,
    ) -> None:
        state = self._state_for_user(user_id)
        now = datetime.utcnow()
        normalized_role = role.lower()
        if normalized_role == "wrist":
            state.wrist_connected = False
        elif normalized_role == "chest":
            state.chest_connected = False
        state.streaming = False
        state.last_event_at = now
        state.last_message = f"{normalized_role.title()} disconnected."

    def mark_measurement_started(self, user_id: str) -> None:
        state = self._state_for_user(user_id)
        now = datetime.utcnow()
        state.streaming = True
        state.measurement_started_at = now
        state.measurement_stopped_at = None
        state.last_event_at = now
        state.last_message = "Measurement session started."
        state.last_error = None
        state.wrist_frames_uploaded = 0
        state.chest_frames_uploaded = 0
        state.merged_frames_uploaded = 0
        state.readings_created = 0
        state.last_seq = 0
        state.buffer_fill = f"0/{WAVEFORM_WINDOW_FRAMES}"

    def mark_measurement_stopped(self, user_id: str) -> None:
        state = self._state_for_user(user_id)
        now = datetime.utcnow()
        state.streaming = False
        state.measurement_stopped_at = now
        state.last_event_at = now
        state.last_message = "Measurement session stopped."

    def record_frame_result(self, user_id: str, *, role: str, result: FrameProcessResult) -> None:
        state = self._state_for_user(user_id)
        state.last_event_at = datetime.utcnow()
        state.last_seq = result.seq_num
        if result.buffer_fill:
            state.buffer_fill = result.buffer_fill
        state.last_message = result.message
        state.last_error = None
        normalized_role = role.lower()
        if normalized_role == "wrist":
            state.wrist_frames_uploaded += 1
        elif normalized_role == "chest":
            state.chest_frames_uploaded += 1
        else:
            state.merged_frames_uploaded += 1
        if result.reading_created:
            state.readings_created += 1

    def record_error(self, user_id: str, *, message: str) -> None:
        state = self._state_for_user(user_id)
        state.last_event_at = datetime.utcnow()
        state.last_error = message
        state.last_message = message

    def snapshot(self, user_id: str, *, data_stats: dict) -> MobileSessionStatusResponse:
        state = self._state_for_user(user_id)
        merged_stats = dict(data_stats)
        merged_stats.update(
            {
                "buffer_fill": state.buffer_fill,
                "wrist_frames_uploaded": state.wrist_frames_uploaded,
                "chest_frames_uploaded": state.chest_frames_uploaded,
                "merged_frames_uploaded": state.merged_frames_uploaded,
                "readings_created": state.readings_created,
                "last_seq": state.last_seq,
            }
        )
        if state.last_error:
            merged_stats["last_error"] = state.last_error
        if state.last_message:
            merged_stats["last_message"] = state.last_message

        return MobileSessionStatusResponse(
            success=True,
            wrist_connected=state.wrist_connected,
            chest_connected=state.chest_connected,
            wrist_device_name=state.wrist_device_name,
            wrist_device_address=state.wrist_device_address,
            chest_device_name=state.chest_device_name,
            chest_device_address=state.chest_device_address,
            streaming=state.streaming,
            last_event_at=_dt_to_ms(state.last_event_at),
            measurement_started_at=_dt_to_ms(state.measurement_started_at),
            measurement_stopped_at=_dt_to_ms(state.measurement_stopped_at),
            data_stats=merged_stats,
            message=state.last_message,
        )


mobile_session_tracker = MobileSessionTracker()


def _dt_to_ms(value: Optional[datetime]) -> Optional[int]:
    if value is None:
        return None
    return int(value.timestamp() * 1000)


def _row_to_dict(row) -> dict[str, Any]:
    serialized: dict[str, Any] = {}
    for key, value in row._mapping.items():
        if isinstance(value, datetime):
            serialized[key] = int(value.timestamp() * 1000)
        else:
            serialized[key] = value
    return serialized


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
        for device in devices:
            if device.name and WRIST_DEVICE_NAME.lower() in device.name.lower():
                return ScanResult(
                    found=True,
                    device_name=device.name,
                    device_address=device.address,
                    message=f"Device found: {device.name} ({device.address})",
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

    ble._dm.set_user_id(canonical_sensor_user_key(current_user))
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
@router.post("/mobile/session/connected", response_model=CommandResponse, summary="Track a mobile BLE device connection")
async def mark_mobile_connected(
    request: Optional[MobileDeviceEventRequest] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Mark the authenticated user's BLE calibration window as started.

    The Android app calls this right after a successful GATT connection so the
    3-day personalization countdown can start immediately.
    """
    ensure_ble_calibration_started(current_user, db, touch_last_connected=True)
    if request is not None:
        sensor_owner_key = canonical_sensor_user_key(current_user)
        mobile_session_tracker.mark_connected(
            sensor_owner_key,
            role=request.role,
            device_name=request.device_name,
            device_address=request.device_address,
        )
        role_name = request.role.lower()
        return CommandResponse(success=True, message=f"{role_name.title()} BLE connection recorded.")
    return CommandResponse(success=True, message="BLE connection recorded.")


@router.post("/mobile-measurement/start", response_model=CommandResponse, summary="Reset mobile BLE measurement session")
@router.post("/mobile/session/start", response_model=CommandResponse, summary="Start a mobile BLE measurement session")
async def start_mobile_measurement_session(
    data_manager: DataManager = Depends(require_data_manager),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Reset backend-side sync buffers before a new dual-device measurement starts.
    """
    ensure_ble_calibration_started(current_user, db, touch_last_connected=False)
    sensor_owner_key = canonical_sensor_user_key(current_user)
    data_manager.set_user_context(sensor_owner_key, current_user.age)
    data_manager.reset_user_stream(sensor_owner_key)
    mobile_frame_sync.reset_user(sensor_owner_key)
    mobile_session_tracker.mark_measurement_started(sensor_owner_key)
    return CommandResponse(success=True, message="Mobile BLE measurement session reset.")


@router.post("/mobile/session/stop", response_model=CommandResponse, summary="Stop a mobile BLE measurement session")
async def stop_mobile_measurement_session(
    data_manager: DataManager = Depends(require_data_manager),
    current_user: User = Depends(get_current_user),
):
    sensor_owner_key = canonical_sensor_user_key(current_user)
    data_manager.reset_user_stream(sensor_owner_key)
    mobile_frame_sync.reset_user(sensor_owner_key)
    mobile_session_tracker.mark_measurement_stopped(sensor_owner_key)
    return CommandResponse(success=True, message="Mobile BLE measurement session stopped.")


@router.post("/mobile/session/disconnected", response_model=CommandResponse, summary="Track a mobile BLE device disconnect")
async def mark_mobile_disconnected(
    request: Optional[MobileDeviceEventRequest] = None,
    current_user: User = Depends(get_current_user),
):
    sensor_owner_key = canonical_sensor_user_key(current_user)
    if request is not None:
        mobile_session_tracker.mark_disconnected(sensor_owner_key, role=request.role)
        return CommandResponse(success=True, message=f"{request.role.title()} BLE disconnect recorded.")
    mobile_session_tracker.mark_measurement_stopped(sensor_owner_key)
    return CommandResponse(success=True, message="BLE disconnect recorded.")


@router.get("/mobile/session/status", response_model=MobileSessionStatusResponse, summary="Inspect mobile BLE session state")
async def get_mobile_session_status(
    data_manager: DataManager = Depends(require_data_manager),
    current_user: User = Depends(get_current_user),
):
    sensor_owner_key = canonical_sensor_user_key(current_user)
    return mobile_session_tracker.snapshot(
        sensor_owner_key,
        data_stats=data_manager.get_stats(sensor_owner_key),
    )


@router.get(
    "/mobile/raw/recent",
    response_model=RawTimescaleDebugResponse,
    summary="Inspect recent raw Timescale rows (debug-only)",
)
async def get_recent_mobile_raw_rows(
    wrist_limit: int = Query(10, ge=1, le=100),
    ecg_limit: int = Query(25, ge=1, le=250),
    current_user: User = Depends(get_current_user),
    sensor_db: Session = Depends(get_sensor_db),
):
    """
    Return the latest raw Timescale rows written for the authenticated user.

    This is meant for lab/debug verification so we can confirm the mobile BLE
    pipeline is persisting wristband_data and ecg_data rows in TimescaleDB.
    """
    params = sensor_user_params(current_user)
    sensor_owner_key = canonical_sensor_user_key(current_user)

    wrist_total = sensor_db.execute(
        text(f"""
            SELECT COUNT(*) AS total
            FROM wristband_data
            WHERE {sensor_user_clause()}
        """),
        params,
    ).scalar() or 0

    ecg_total = sensor_db.execute(
        text(f"""
            SELECT COUNT(*) AS total
            FROM ecg_data
            WHERE {sensor_user_clause()}
        """),
        params,
    ).scalar() or 0

    wrist_rows = sensor_db.execute(
        text(f"""
            SELECT
                time,
                user_id,
                device_timestamp_ms,
                received_at_ms,
                frame_seq,
                chest_seq,
                frame_mode,
                ppg_ir,
                ppg_red,
                ppg_ir_batch,
                ppg_red_batch,
                ax,
                ay,
                az,
                gx,
                gy,
                gz,
                temperature,
                ep,
                qi_w,
                qi_c,
                qi,
                battery
            FROM wristband_data
            WHERE {sensor_user_clause()}
            ORDER BY time DESC
            LIMIT :wrist_limit
        """),
        {
            **params,
            "wrist_limit": wrist_limit,
        },
    ).fetchall()

    ecg_rows = sensor_db.execute(
        text(f"""
            SELECT
                time,
                user_id,
                device_timestamp_ms,
                received_at_ms,
                frame_seq,
                sample_index,
                ecg_value,
                ep,
                qi_c
            FROM ecg_data
            WHERE {sensor_user_clause()}
            ORDER BY time DESC
            LIMIT :ecg_limit
        """),
        {
            **params,
            "ecg_limit": ecg_limit,
        },
    ).fetchall()

    message = None
    if wrist_total == 0 and ecg_total == 0:
        message = "No raw Timescale rows found yet for this user."

    return RawTimescaleDebugResponse(
        success=True,
        sensor_owner_key=sensor_owner_key,
        wrist_total_rows=int(wrist_total),
        ecg_total_rows=int(ecg_total),
        wrist_rows=[_row_to_dict(row) for row in wrist_rows],
        ecg_rows=[_row_to_dict(row) for row in ecg_rows],
        message=message,
    )


@router.post("/mobile-frame", response_model=FrameProcessResult, summary="Upload a BLE frame from the mobile app")
@router.post("/mobile/frames/merged", response_model=FrameProcessResult, summary="Upload a merged mobile BLE frame")
async def ingest_mobile_frame(
    request: MobileFrameUploadRequest,
    data_manager: DataManager = Depends(require_data_manager),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Accept a fully merged BLE frame uploaded by the Android app.

    This remains available for backward compatibility and single-payload flows.
    """
    raw_frame = (request.raw_frame or "").strip()
    if not raw_frame:
        raise HTTPException(status_code=400, detail="raw_frame is required.")

    ensure_ble_calibration_started(current_user, db, touch_last_connected=False)
    sensor_owner_key = canonical_sensor_user_key(current_user)
    data_manager.set_user_context(sensor_owner_key, current_user.age)
    result = await data_manager.process_frame(raw_frame, user_id=sensor_owner_key)
    if not result.success:
        mobile_session_tracker.record_error(sensor_owner_key, message=result.message or "Merged BLE frame processing failed.")
        raise HTTPException(status_code=400, detail=result.message or "BLE frame processing failed.")
    mobile_session_tracker.record_frame_result(sensor_owner_key, role="merged", result=result)
    return result


@router.post("/mobile-frame/wrist", response_model=FrameProcessResult, summary="Upload a wrist BLE frame from the mobile app")
@router.post("/mobile/frames/wrist", response_model=FrameProcessResult, summary="Upload a wrist mobile BLE frame")
async def ingest_mobile_wrist_frame(
    request: MobileFrameUploadRequest,
    data_manager: DataManager = Depends(require_data_manager),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    raw_frame = (request.raw_frame or "").strip()
    if not raw_frame:
        raise HTTPException(status_code=400, detail="raw_frame is required.")

    ensure_ble_calibration_started(current_user, db, touch_last_connected=False)
    sensor_owner_key = canonical_sensor_user_key(current_user)
    data_manager.set_user_context(sensor_owner_key, current_user.age)
    result = await data_manager.process_wrist_frame(raw_frame, user_id=sensor_owner_key)
    if not result.success:
        mobile_session_tracker.record_error(sensor_owner_key, message=result.message or "Wrist BLE frame processing failed.")
        raise HTTPException(status_code=400, detail=result.message or "Wrist BLE frame processing failed.")
    mobile_session_tracker.record_frame_result(sensor_owner_key, role="wrist", result=result)
    return result


@router.post("/mobile-frame/chest", response_model=FrameProcessResult, summary="Upload a chest BLE frame from the mobile app")
@router.post("/mobile/frames/chest", response_model=FrameProcessResult, summary="Upload a chest mobile BLE frame")
async def ingest_mobile_chest_frame(
    request: MobileFrameUploadRequest,
    data_manager: DataManager = Depends(require_data_manager),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    raw_frame = (request.raw_frame or "").strip()
    if not raw_frame:
        raise HTTPException(status_code=400, detail="raw_frame is required.")

    ensure_ble_calibration_started(current_user, db, touch_last_connected=False)
    sensor_owner_key = canonical_sensor_user_key(current_user)
    data_manager.set_user_context(sensor_owner_key, current_user.age)
    result = await data_manager.process_chest_frame(raw_frame, user_id=sensor_owner_key)
    if not result.success:
        mobile_session_tracker.record_error(sensor_owner_key, message=result.message or "Chest BLE frame processing failed.")
        raise HTTPException(status_code=400, detail=result.message or "Chest BLE frame processing failed.")
    mobile_session_tracker.record_frame_result(sensor_owner_key, role="chest", result=result)
    return result


@router.get("/stats", summary="DataManager frame statistics")
async def get_data_stats(
    data_manager: DataManager = Depends(require_data_manager),
    current_user: User = Depends(get_current_user),
):
    """Frame processing stats: processed, failed, success_rate, bp_inferences, buffer_fill."""
    return data_manager.get_stats(canonical_sensor_user_key(current_user))
