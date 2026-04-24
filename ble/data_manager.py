import asyncio
import json
import logging
import math
import statistics
import time
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Optional

import numpy as np
from sqlalchemy import text

from ble.frame import BLEFrame, FrameProcessResult, InferredReading
from backend.utils.sensor_identity import resolve_user_by_sensor_key

logger = logging.getLogger(__name__)

LEGACY_WINDOW_SIZE = 100
WAVEFORM_WINDOW_FRAMES = 25
WINDOW_SIZE = LEGACY_WINDOW_SIZE

LEGACY_FRAME_INTERVAL_MS = 100.0
WAVEFORM_FRAME_INTERVAL_MS = 40.0
ECG_SAMPLE_INTERVAL_MS = 4.0
WAVEFORM_TARGET_SAMPLES = 250
MIN_QUALITY_RATIO = 0.0
DB_SYNC_TOLERANCE_MS = 200
DB_SYNC_RECEIVED_AT_TOLERANCE_MS = 500
DB_SYNC_FETCH_LIMIT = 300
DB_SYNC_MISSING_SEQ_PENALTY = 10_000


@dataclass
class UserStreamState:
    window: deque = field(default_factory=lambda: deque(maxlen=WINDOW_SIZE))
    mode: str = "legacy"
    bp_inferences: int = 0
    last_inferred_reading: Optional[InferredReading] = None
    db_buffer_fill: str = f"0/{WAVEFORM_WINDOW_FRAMES}"
    last_synced_wrist_received_at_ms: int = 0
    last_synced_chest_received_at_ms: int = 0


_data_manager_instance: Optional["DataManager"] = None


def get_data_manager() -> Optional["DataManager"]:
    return _data_manager_instance


def init_data_manager(db_factory, default_user_id: str = "", user_age: float = 40.0) -> "DataManager":
    global _data_manager_instance
    if _data_manager_instance is None:
        _data_manager_instance = DataManager(
            db_factory=db_factory,
            default_user_id=default_user_id,
            user_age=user_age,
        )
    return _data_manager_instance


class DataManager:
    def __init__(self, db_factory, default_user_id: str = "", user_age: float = 40.0):
        self._db_factory = db_factory
        self._default_user_id = default_user_id
        self._user_age = user_age
        self._frames_processed = 0
        self._frames_failed = 0
        self._bp_inferences = 0
        self._user_states: Dict[str, UserStreamState] = {}
        self._user_locks: Dict[str, asyncio.Lock] = {}
        self._user_ages: Dict[str, float] = {}
        self._sensor_table_columns: Dict[str, set[str]] = {}
        logger.info("DataManager initialized (default_user_id=%s)", default_user_id)

    async def process_frame(self, json_str, user_id=None):
        uid = user_id or self._default_user_id
        if not uid:
            self._frames_failed += 1
            return FrameProcessResult(
                success=False,
                seq_num=0,
                quality=0.0,
                message="Missing user_id for BLE frame processing.",
            )

        try:
            frame = BLEFrame.model_validate(self._normalize_frame_payload(json_str))
            frame.received_at_ms = int(time.time() * 1000)
        except Exception as exc:
            self._frames_failed += 1
            logger.warning("Frame parse error: %s", exc)
            return FrameProcessResult(
                success=False,
                seq_num=0,
                quality=0.0,
                message=f"Parse error: {exc}",
            )

        state = self._state_for_user(uid)
        lock = self._lock_for_user(uid)

        async with lock:
            db = self._db_factory()
            try:
                self._insert_raw_frame(db, frame, uid)
                db.commit()
                self._frames_processed += 1
            except Exception as exc:
                db.rollback()
                self._frames_failed += 1
                logger.error("Raw frame DB error (user=%s seq=%d): %s", uid, frame.sq, exc)
                return FrameProcessResult(
                    success=False,
                    seq_num=frame.sq,
                    quality=frame.quality_percent,
                    message=f"DB error: {exc}",
                )
            finally:
                db.close()

            target_frames = self._ensure_state_mode(state, frame.frame_mode)
            state.window.append(frame)

            inferred = None
            if len(state.window) == target_frames:
                inferred = await self._run_ml_window(uid, state)

            return FrameProcessResult(
                success=True,
                seq_num=frame.sq,
                quality=frame.quality_percent,
                buffer_fill=f"{len(state.window)}/{target_frames}",
                reading_created=inferred is not None,
                reading=inferred,
            )

    async def process_wrist_frame(self, json_str, user_id=None):
        uid = user_id or self._default_user_id
        if not uid:
            self._frames_failed += 1
            return FrameProcessResult(
                success=False,
                seq_num=0,
                quality=0.0,
                message="Missing user_id for wrist frame processing.",
            )

        try:
            normalized_payload = self._normalize_wrist_partial_payload(json_str)
            frame = BLEFrame.model_validate(normalized_payload)
            frame.received_at_ms = int(time.time() * 1000)  # always server clock
        except Exception as exc:
            self._frames_failed += 1
            logger.warning("Wrist frame parse error: %s", exc)
            return FrameProcessResult(
                success=False,
                seq_num=0,
                quality=0.0,
                message=f"Parse error: {exc}",
            )

        state = self._state_for_user(uid)
        lock = self._lock_for_user(uid)
        async with lock:
            db = self._db_factory()
            try:
                self._insert_wrist_raw(db, frame, uid)
                db.commit()
                self._frames_processed += 1
            except Exception as exc:
                db.rollback()
                self._frames_failed += 1
                logger.error("Wrist raw DB error (user=%s seq=%d): %s", uid, frame.sq, exc)
                return FrameProcessResult(
                    success=False,
                    seq_num=frame.sq,
                    quality=frame.quality_percent,
                    message=f"DB error: {exc}",
                )
            finally:
                db.close()

            inferred, buffer_fill = await self._attempt_db_synced_inference(uid, state)
            return FrameProcessResult(
                success=True,
                seq_num=frame.sq,
                quality=frame.quality_percent,
                buffer_fill=buffer_fill,
                reading_created=inferred is not None,
                reading=inferred,
                message="Wrist frame stored to Timescale.",
            )

    async def process_chest_frame(self, json_str, user_id=None):
        uid = user_id or self._default_user_id
        if not uid:
            self._frames_failed += 1
            return FrameProcessResult(
                success=False,
                seq_num=0,
                quality=0.0,
                message="Missing user_id for chest frame processing.",
            )

        try:
            payload = self._normalize_chest_partial_payload(json_str)
            received_at_ms = int(time.time() * 1000)  # always server clock
        except Exception as exc:
            self._frames_failed += 1
            logger.warning("Chest frame parse error: %s", exc)
            return FrameProcessResult(
                success=False,
                seq_num=0,
                quality=0.0,
                message=f"Parse error: {exc}",
            )

        state = self._state_for_user(uid)
        lock = self._lock_for_user(uid)
        async with lock:
            db = self._db_factory()
            try:
                self._insert_chest_raw(db, payload, uid, received_at_ms)
                db.commit()
                self._frames_processed += 1
            except Exception as exc:
                db.rollback()
                self._frames_failed += 1
                logger.error("Chest raw DB error (user=%s seq=%d): %s", uid, payload["sq"], exc)
                return FrameProcessResult(
                    success=False,
                    seq_num=payload["sq"],
                    quality=float(payload["qi_c"] * 100),
                    message=f"DB error: {exc}",
                )
            finally:
                db.close()

            inferred, buffer_fill = await self._attempt_db_synced_inference(uid, state)
            return FrameProcessResult(
                success=True,
                seq_num=payload["sq"],
                quality=float(payload["qi_c"] * 100),
                buffer_fill=buffer_fill,
                reading_created=inferred is not None,
                reading=inferred,
                message="Chest frame stored to Timescale.",
            )

    def set_user_id(self, user_id):
        self._default_user_id = user_id
        logger.info("DataManager: active sensor owner key=%s", user_id)

    def set_user_context(self, user_id: str, age: float):
        if user_id:
            self._user_ages[user_id] = age
            if not self._default_user_id:
                self._default_user_id = user_id

    def set_user_age(self, age):
        if self._default_user_id:
            self._user_ages[self._default_user_id] = age
        self._user_age = age

    def reset_user_stream(self, user_id: str) -> None:
        if not user_id:
            return

        state = self._user_states.get(user_id)
        if state is None:
            return

        now_ms = int(time.time() * 1000)
        watermark_ms = now_ms - 10_000  # 10s buffer for first frames
        state.window = deque(maxlen=self._window_target_for_mode("legacy"))
        state.mode = "legacy"
        state.db_buffer_fill = f"0/{WAVEFORM_WINDOW_FRAMES}"
        state.last_synced_wrist_received_at_ms = watermark_ms
        state.last_synced_chest_received_at_ms = watermark_ms
        logger.info("DataManager stream reset for %s (watermark=%d)", user_id, watermark_ms)

    def get_stats(self, user_id: Optional[str] = None):
        if user_id:
            state = self._user_states.get(user_id)
            target = self._window_target_for_mode(state.mode) if state else WINDOW_SIZE
            return {
                "processed": self._frames_processed,
                "failed": self._frames_failed,
                "total": self._frames_processed + self._frames_failed,
                "success_rate": self._success_rate(),
                "bp_inferences": state.bp_inferences if state else 0,
                "buffer_fill": (
                    state.db_buffer_fill
                    if state and state.mode == "waveform"
                    else f"{len(state.window)}/{target}" if state else f"0/{WINDOW_SIZE}"
                ),
                "stream_mode": state.mode if state else "legacy",
            }

        default_state = self._user_states.get(self._default_user_id) if self._default_user_id else None
        target = self._window_target_for_mode(default_state.mode) if default_state else WINDOW_SIZE
        return {
            "processed": self._frames_processed,
            "failed": self._frames_failed,
            "total": self._frames_processed + self._frames_failed,
            "success_rate": self._success_rate(),
            "bp_inferences": self._bp_inferences,
            "active_users": len(self._user_states),
            "buffer_fill": f"{len(default_state.window)}/{target}" if default_state else f"0/{WINDOW_SIZE}",
            "stream_mode": default_state.mode if default_state else "legacy",
        }

    @property
    def _window(self):
        if not self._default_user_id:
            return deque(maxlen=WINDOW_SIZE)
        return self._state_for_user(self._default_user_id).window

    def _success_rate(self) -> float:
        total = self._frames_processed + self._frames_failed
        return round(self._frames_processed / total * 100, 1) if total else 0.0

    def _state_for_user(self, user_id: str) -> UserStreamState:
        state = self._user_states.get(user_id)
        if state is None:
            state = UserStreamState()
            self._user_states[user_id] = state
        return state

    def _lock_for_user(self, user_id: str) -> asyncio.Lock:
        lock = self._user_locks.get(user_id)
        if lock is None:
            lock = asyncio.Lock()
            self._user_locks[user_id] = lock
        return lock

    @staticmethod
    def _window_target_for_mode(mode: str) -> int:
        return WAVEFORM_WINDOW_FRAMES if mode == "waveform" else WINDOW_SIZE

    def _ensure_state_mode(self, state: UserStreamState, mode: str) -> int:
        target = self._window_target_for_mode(mode)
        if state.mode != mode or state.window.maxlen != target:
            state.mode = mode
            state.window = deque(maxlen=target)
        return target

    @staticmethod
    def _min_quality_frames(total_frames: int) -> int:
        return max(1, math.ceil(total_frames * MIN_QUALITY_RATIO))

    @staticmethod
    def _coerce_flag(value: Any, default: int = 0) -> int:
        try:
            numeric = int(value)
        except (TypeError, ValueError):
            return default
        return 1 if numeric else 0

    @staticmethod
    def _extract_optional_ms(value: Any) -> Optional[int]:
        if value is None:
            return None
        try:
            ms = int(value)
        except (TypeError, ValueError):
            return None
        return ms if ms > 0 else None

    def _normalize_frame_payload(self, raw_payload: str | dict[str, Any]) -> dict[str, Any]:
        data = json.loads(raw_payload) if isinstance(raw_payload, str) else dict(raw_payload)
        if not isinstance(data, dict):
            raise ValueError("BLE frame payload must be a JSON object.")

        normalized = dict(data)
        normalized["ts"] = int(normalized.get("ts", normalized.get("timestamp", int(time.time() * 1000))) or 0)
        normalized["sq"] = int(normalized.get("sq", normalized.get("seq", -1)) or -1)

        if "pi" not in normalized and "ppg_ir" in normalized:
            normalized["pi"] = normalized["ppg_ir"]
        if "pr" not in normalized and "ppg_red" in normalized:
            normalized["pr"] = normalized["ppg_red"]
        if "ecg" not in normalized and "ecg_value" in normalized:
            normalized["ecg"] = normalized["ecg_value"]

        normalized["tp"] = float(normalized.get("tp", normalized.get("temperature", 0.0)) or 0.0)
        normalized["ep"] = self._coerce_flag(normalized.get("ep", normalized.get("r_peak", 0)))
        normalized["qi_w"] = self._coerce_flag(normalized.get("qi_w", normalized.get("qi", 0)))
        normalized["qi_c"] = self._coerce_flag(normalized.get("qi_c", 0))
        normalized["qi"] = self._coerce_flag(
            normalized.get(
                "qi",
                normalized["qi_w"] if normalized["qi_c"] == 0 else (normalized["qi_w"] and normalized["qi_c"]),
            )
        )
        normalized["bt"] = int(normalized.get("bt", normalized.get("battery", 100)) or 100)

        for key in ("ax", "ay", "az", "gx", "gy", "gz", "cx", "cy", "cz"):
            normalized[key] = int(normalized.get(key, 0) or 0)

        return normalized

    def _normalize_wrist_partial_payload(self, raw_payload: str | dict[str, Any]) -> dict[str, Any]:
        normalized = self._normalize_frame_payload(raw_payload)
        normalized["ep"] = 0
        normalized["qi_c"] = 0
        normalized["qi"] = self._coerce_flag(normalized.get("qi_w", normalized.get("qi", 0)))
        normalized["ecg"] = []
        normalized["cs"] = int(normalized.get("cs", normalized.get("sq", -1)) or -1)
        return normalized

    def _normalize_chest_partial_payload(self, raw_payload: str | dict[str, Any]) -> dict[str, Any]:
        data = json.loads(raw_payload) if isinstance(raw_payload, str) else dict(raw_payload)
        if not isinstance(data, dict):
            raise ValueError("Chest frame payload must be a JSON object.")

        seq_value = data.get("sq", data.get("seq", -1))
        try:
            seq_num = int(seq_value)
        except (TypeError, ValueError):
            seq_num = -1
        if seq_num < 0:
            raise ValueError("Chest frame must include a valid sq/seq number.")

        timestamp_value = data.get("ts", data.get("timestamp"))
        device_timestamp_ms: Optional[int] = None
        if timestamp_value is not None:
            try:
                device_timestamp_ms = int(timestamp_value)
            except (TypeError, ValueError):
                device_timestamp_ms = None
        mobile_timestamp_ms = self._extract_optional_ms(data.get("mobile_ts"))

        ecg_values = [int(round(value)) for value in self._decode_numeric_series(data.get("ecg", data.get("ecg_value")))]
        if not ecg_values:
            raise ValueError("Chest frame is missing ECG batch values.")

        return {
            "sq": seq_num,
            "ts": device_timestamp_ms,
            "mobile_ts": mobile_timestamp_ms,
            "ep": self._coerce_flag(data.get("ep", data.get("r_peak", 0))),
            "qi_c": self._coerce_flag(data.get("qi_c", data.get("qi", 0))),
            "ecg": ecg_values,
        }

    def _get_table_columns(self, db, table_name: str) -> set[str]:
        cached = self._sensor_table_columns.get(table_name)
        if cached is not None:
            return cached

        rows = db.execute(
            text(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = :table_name
                """
            ),
            {"table_name": table_name},
        ).fetchall()
        columns = {str(row[0]) for row in rows}
        self._sensor_table_columns[table_name] = columns
        return columns

    def _dynamic_insert(self, db, table_name: str, payload: dict[str, Any], *, jsonb_columns: set[str] | None = None) -> None:
        jsonb_columns = jsonb_columns or set()
        available_columns = self._get_table_columns(db, table_name)
        insertable = {key: value for key, value in payload.items() if key in available_columns}
        if not insertable:
            raise ValueError(f"No matching columns available for {table_name}.")

        column_names = list(insertable.keys())
        rendered_values = [
            f"CAST(:{column} AS JSONB)" if column in jsonb_columns else f":{column}"
            for column in column_names
        ]
        db.execute(
            text(
                f"""
                INSERT INTO {table_name} ({", ".join(column_names)})
                VALUES ({", ".join(rendered_values)})
                """
            ),
            insertable,
        )

    @staticmethod
    def _decode_numeric_series(value: Any) -> list[float]:
        if value is None:
            return []
        if isinstance(value, str):
            stripped = value.strip()
            if stripped.startswith("[") and stripped.endswith("]"):
                try:
                    value = json.loads(stripped)
                except json.JSONDecodeError:
                    return []
            else:
                try:
                    return [float(stripped)]
                except ValueError:
                    return []
        if isinstance(value, (list, tuple)):
            decoded: list[float] = []
            for item in value:
                try:
                    decoded.append(float(item))
                except (TypeError, ValueError):
                    continue
            return decoded
        try:
            return [float(value)]
        except (TypeError, ValueError):
            return []

    def _build_waveform_window_from_timescale(self, db, user_id: str, frames) -> Optional["WaveformWindow"]:
        if not frames:
            return None

        seq_start = min(frame.sq for frame in frames)
        seq_end = max(frame.sq for frame in frames)

        wrist_columns = self._get_table_columns(db, "wristband_data")
        ecg_columns = self._get_table_columns(db, "ecg_data")
        if "frame_seq" not in wrist_columns or "ecg_value" not in ecg_columns:
            return None

        wrist_select = ["frame_seq"]
        if "ppg_ir_batch" in wrist_columns:
            wrist_select.append("ppg_ir_batch")
        elif "ppg_ir" in wrist_columns:
            wrist_select.append("ppg_ir")
        if "ppg_red_batch" in wrist_columns:
            wrist_select.append("ppg_red_batch")
        elif "ppg_red" in wrist_columns:
            wrist_select.append("ppg_red")

        wrist_rows = db.execute(
            text(
                f"""
                SELECT {", ".join(wrist_select)}
                FROM wristband_data
                WHERE user_id = :user_id
                  AND frame_seq BETWEEN :seq_start AND :seq_end
                ORDER BY frame_seq ASC
                """
            ),
            {
                "user_id": user_id,
                "seq_start": seq_start,
                "seq_end": seq_end,
            },
        ).mappings().all()

        ppg_ir: list[float] = []
        ppg_red: list[float] = []
        for row in wrist_rows:
            ppg_ir.extend(
                self._decode_numeric_series(
                    row.get("ppg_ir_batch") if "ppg_ir_batch" in row else row.get("ppg_ir")
                )
            )
            ppg_red.extend(
                self._decode_numeric_series(
                    row.get("ppg_red_batch") if "ppg_red_batch" in row else row.get("ppg_red")
                )
            )

        ecg_order = "frame_seq ASC, sample_index ASC" if "sample_index" in ecg_columns else "time ASC"
        ecg_rows = db.execute(
            text(
                f"""
                SELECT ecg_value
                FROM ecg_data
                WHERE user_id = :user_id
                  AND frame_seq BETWEEN :seq_start AND :seq_end
                ORDER BY {ecg_order}
                """
            ),
            {
                "user_id": user_id,
                "seq_start": seq_start,
                "seq_end": seq_end,
            },
        ).fetchall()
        ecg = [float(row[0]) for row in ecg_rows if row and row[0] is not None]

        if len(ecg) < WAVEFORM_TARGET_SAMPLES or len(ppg_ir) < 2 or len(ppg_red) < 2:
            return None

        ecg = ecg[:WAVEFORM_TARGET_SAMPLES]
        ppg_ir_resampled = self._resample_signal(ppg_ir, WAVEFORM_TARGET_SAMPLES)
        ppg_red_resampled = self._resample_signal(ppg_red, WAVEFORM_TARGET_SAMPLES)
        if len(ppg_ir_resampled) != WAVEFORM_TARGET_SAMPLES or len(ppg_red_resampled) != WAVEFORM_TARGET_SAMPLES:
            return None

        from backend.services.bp_model_service import WaveformWindow

        return WaveformWindow(
            ecg=ecg,
            ppg_red=ppg_red_resampled,
            ppg_ir=ppg_ir_resampled,
        )

    @staticmethod
    def _to_utc_datetime_from_ms(value: int) -> datetime:
        return datetime.fromtimestamp(value / 1000.0, tz=timezone.utc)

    def _insert_wrist_raw(self, db, frame: BLEFrame, user_id: str) -> None:
        received_at = self._to_utc_datetime_from_ms(frame.received_at_ms or int(time.time() * 1000))
        self._dynamic_insert(
            db,
            "wristband_data",
            {
                "time": received_at,
                "user_id": user_id,
                "device_timestamp_ms": frame.ts,
                "received_at_ms": frame.received_at_ms,
                "frame_seq": frame.sq,
                "chest_seq": frame.cs,
                "frame_mode": "waveform",
                "ppg_ir": frame.ppg_ir_latest,
                "ppg_red": frame.ppg_red_latest,
                "ppg_ir_batch": json.dumps(frame.ppg_ir_batch),
                "ppg_red_batch": json.dumps(frame.ppg_red_batch),
                "ax": frame.ax,
                "ay": frame.ay,
                "az": frame.az,
                "gx": frame.gx,
                "gy": frame.gy,
                "gz": frame.gz,
                "temperature": frame.tp,
                "ep": 0,
                "qi_w": frame.qi_w,
                "qi_c": 0,
                "qi": frame.qi_w,
                "battery": frame.bt,
            },
            jsonb_columns={"ppg_ir_batch", "ppg_red_batch"},
        )

    def _insert_chest_raw(self, db, payload: dict[str, Any], user_id: str, received_at_ms: int) -> None:
        ecg_batch = [float(value) for value in payload.get("ecg", [])]
        if not ecg_batch:
            return

        received_at = self._to_utc_datetime_from_ms(received_at_ms)
        start_at = received_at - timedelta(milliseconds=ECG_SAMPLE_INTERVAL_MS * (len(ecg_batch) - 1))
        available_columns = self._get_table_columns(db, "ecg_data")
        rows = []

        for index, value in enumerate(ecg_batch):
            row = {
                "time": start_at + timedelta(milliseconds=ECG_SAMPLE_INTERVAL_MS * index),
                "user_id": user_id,
                "device_timestamp_ms": payload.get("ts"),
                "received_at_ms": received_at_ms,
                "frame_seq": payload["sq"],
                "sample_index": index,
                "ecg_value": float(value),
                "ep": payload.get("ep", 0),
                "qi_c": payload.get("qi_c", 0),
            }
            rows.append({key: val for key, val in row.items() if key in available_columns})

        if not rows:
            return

        column_names = list(rows[0].keys())
        db.execute(
            text(
                f"""
                INSERT INTO ecg_data ({", ".join(column_names)})
                VALUES ({", ".join(f":{column}" for column in column_names)})
                """
            ),
            rows,
        )

    def _insert_raw_frame(self, db, frame, user_id):
        received_at = datetime.fromtimestamp(frame.received_at_ms / 1000, tz=timezone.utc)
        self._dynamic_insert(db, "wristband_data", {
            "time": received_at,
            "user_id": user_id,
            "device_timestamp_ms": frame.ts,
            "received_at_ms": frame.received_at_ms,
            "frame_seq": frame.sq,
            "chest_seq": frame.cs,
            "frame_mode": frame.frame_mode,
            "ppg_ir": frame.ppg_ir_latest,
            "ppg_red": frame.ppg_red_latest,
            "ppg_ir_batch": json.dumps(frame.ppg_ir_batch),
            "ppg_red_batch": json.dumps(frame.ppg_red_batch),
            "ax": frame.ax,
            "ay": frame.ay,
            "az": frame.az,
            "gx": frame.gx,
            "gy": frame.gy,
            "gz": frame.gz,
            "temperature": frame.tp,
            "ep": frame.ep,
            "qi_w": frame.qi_w,
            "qi_c": frame.qi_c,
            "qi": frame.qi,
            "battery": frame.bt,
        }, jsonb_columns={"ppg_ir_batch", "ppg_red_batch"})

        ecg_batch = frame.ecg_batch
        if ecg_batch:
            start_at = received_at - timedelta(milliseconds=ECG_SAMPLE_INTERVAL_MS * (len(ecg_batch) - 1))
            available_columns = self._get_table_columns(db, "ecg_data")
            rows = []
            for index, value in enumerate(ecg_batch):
                row = {
                    "time": start_at + timedelta(milliseconds=ECG_SAMPLE_INTERVAL_MS * index),
                    "user_id": user_id,
                    "device_timestamp_ms": frame.ts,
                    "received_at_ms": frame.received_at_ms,
                    "frame_seq": frame.sq,
                    "sample_index": index,
                    "ecg_value": float(value),
                    "ep": frame.ep,
                    "qi_c": frame.qi_c,
                }
                rows.append({key: val for key, val in row.items() if key in available_columns})

            if rows:
                column_names = list(rows[0].keys())
                db.execute(
                    text(
                        f"""
                        INSERT INTO ecg_data ({", ".join(column_names)})
                        VALUES ({", ".join(f":{column}" for column in column_names)})
                        """
                    ),
                    rows,
                )

    def _load_wrist_sync_rows(self, db, user_id: str, after_received_at_ms: int) -> list[dict[str, Any]]:
        wrist_columns = self._get_table_columns(db, "wristband_data")
        select_cols = ["time", "frame_seq", "qi_w", "qi", "temperature"]
        if "received_at_ms" in wrist_columns:
            select_cols.append("received_at_ms")
        if "device_timestamp_ms" in wrist_columns:
            select_cols.append("device_timestamp_ms")
        if "ppg_ir_batch" in wrist_columns:
            select_cols.append("ppg_ir_batch")
        elif "ppg_ir" in wrist_columns:
            select_cols.append("ppg_ir")
        if "ppg_red_batch" in wrist_columns:
            select_cols.append("ppg_red_batch")
        elif "ppg_red" in wrist_columns:
            select_cols.append("ppg_red")

        where_clauses = ["user_id = :user_id"]
        params: dict[str, Any] = {"user_id": user_id, "row_limit": DB_SYNC_FETCH_LIMIT}
        order_by = "time DESC" if after_received_at_ms <= 0 else "time ASC"
        if "received_at_ms" in wrist_columns and after_received_at_ms > 0:
            where_clauses.append("received_at_ms > :after_ms")
            params["after_ms"] = after_received_at_ms

        rows = db.execute(
            text(
                f"""
                SELECT {", ".join(select_cols)}
                FROM wristband_data
                WHERE {" AND ".join(where_clauses)}
                ORDER BY {order_by}
                LIMIT :row_limit
                """
            ),
            params,
        ).mappings().all()

        normalized_rows: list[dict[str, Any]] = []
        for row in rows:
            raw_received_ms = row.get("received_at_ms")
            if raw_received_ms is None:
                timestamp_value = row.get("time")
                raw_received_ms = int(timestamp_value.timestamp() * 1000) if timestamp_value is not None else 0
            raw_device_ts = row.get("device_timestamp_ms")
            device_ts_ms: Optional[int]
            try:
                device_ts_ms = int(raw_device_ts) if raw_device_ts is not None else None
            except (TypeError, ValueError):
                device_ts_ms = None

            ppg_ir = self._decode_numeric_series(row.get("ppg_ir_batch") if "ppg_ir_batch" in row else row.get("ppg_ir"))
            ppg_red = self._decode_numeric_series(row.get("ppg_red_batch") if "ppg_red_batch" in row else row.get("ppg_red"))
            if len(ppg_ir) < 2 or len(ppg_red) < 2:
                continue

            normalized_rows.append(
                {
                    "sq": int(row.get("frame_seq") or 0),
                    "received_at_ms": int(raw_received_ms),
                    "device_timestamp_ms": device_ts_ms,
                    "qi_w": self._coerce_flag(row.get("qi_w", row.get("qi", 0))),
                    "qi": self._coerce_flag(row.get("qi", row.get("qi_w", 0))),
                    "tp": float(row.get("temperature") or 0.0),
                    "ppg_ir_batch": [int(round(v)) for v in ppg_ir],
                    "ppg_red_batch": [int(round(v)) for v in ppg_red],
                }
            )

        normalized_rows.sort(key=lambda item: (int(item["received_at_ms"]), int(item.get("sq", 0))))
        return normalized_rows

    def _load_chest_sync_rows(self, db, user_id: str, after_received_at_ms: int) -> list[dict[str, Any]]:
        ecg_columns = self._get_table_columns(db, "ecg_data")
        select_cols = ["time", "frame_seq", "ecg_value"]
        if "sample_index" in ecg_columns:
            select_cols.append("sample_index")
        if "received_at_ms" in ecg_columns:
            select_cols.append("received_at_ms")
        if "device_timestamp_ms" in ecg_columns:
            select_cols.append("device_timestamp_ms")
        if "ep" in ecg_columns:
            select_cols.append("ep")
        if "qi_c" in ecg_columns:
            select_cols.append("qi_c")

        where_clauses = ["user_id = :user_id"]
        params: dict[str, Any] = {"user_id": user_id, "row_limit": DB_SYNC_FETCH_LIMIT * 16}
        if "received_at_ms" in ecg_columns and after_received_at_ms > 0:
            where_clauses.append("received_at_ms > :after_ms")
            params["after_ms"] = after_received_at_ms

        if after_received_at_ms <= 0:
            order_by = "time DESC, sample_index DESC" if "sample_index" in ecg_columns else "time DESC"
        else:
            order_by = "time ASC, sample_index ASC" if "sample_index" in ecg_columns else "time ASC"
        rows = db.execute(
            text(
                f"""
                SELECT {", ".join(select_cols)}
                FROM ecg_data
                WHERE {" AND ".join(where_clauses)}
                ORDER BY {order_by}
                LIMIT :row_limit
                """
            ),
            params,
        ).mappings().all()

        grouped: dict[int, dict[str, Any]] = {}
        fallback_seq = 0
        for row in rows:
            raw_seq = row.get("frame_seq")
            seq_num = int(raw_seq) if raw_seq is not None else fallback_seq
            if raw_seq is None:
                fallback_seq += 1

            item = grouped.get(seq_num)
            if item is None:
                raw_received_ms = row.get("received_at_ms")
                if raw_received_ms is None:
                    timestamp_value = row.get("time")
                    raw_received_ms = int(timestamp_value.timestamp() * 1000) if timestamp_value is not None else 0
                raw_device_ts = row.get("device_timestamp_ms")
                try:
                    device_ts_ms = int(raw_device_ts) if raw_device_ts is not None else None
                except (TypeError, ValueError):
                    device_ts_ms = None
                item = {
                    "sq": seq_num,
                    "received_at_ms": int(raw_received_ms),
                    "device_timestamp_ms": device_ts_ms,
                    "ep": self._coerce_flag(row.get("ep", 0)),
                    "qi_c": self._coerce_flag(row.get("qi_c", 0)),
                    "ecg": [],
                }
                grouped[seq_num] = item

            ecg_value = row.get("ecg_value")
            if ecg_value is not None:
                item["ecg"].append(float(ecg_value))
            item["ep"] = max(item["ep"], self._coerce_flag(row.get("ep", 0)))
            item["qi_c"] = max(item["qi_c"], self._coerce_flag(row.get("qi_c", 0)))

        chest_rows = [row for row in grouped.values() if row["ecg"]]
        chest_rows.sort(key=lambda item: (int(item["received_at_ms"]), int(item.get("sq", 0))))
        return chest_rows

    @staticmethod
    def _row_seq_distance(wrist_row: dict[str, Any], chest_row: dict[str, Any]) -> int:
        try:
            wrist_seq = int(wrist_row.get("sq", -1))
            chest_seq = int(chest_row.get("sq", -1))
        except (TypeError, ValueError):
            return DB_SYNC_MISSING_SEQ_PENALTY
        if wrist_seq < 0 or chest_seq < 0:
            return DB_SYNC_MISSING_SEQ_PENALTY
        return abs(wrist_seq - chest_seq)

    @staticmethod
    def _pair_timestamps_ms(wrist_row: dict[str, Any], chest_row: dict[str, Any]) -> tuple[int, int, int, int]:
        wrist_device_ts = wrist_row.get("device_timestamp_ms")
        chest_device_ts = chest_row.get("device_timestamp_ms")
        if wrist_device_ts is not None and chest_device_ts is not None:
            return int(wrist_device_ts), int(chest_device_ts), 0, DB_SYNC_TOLERANCE_MS
        return int(wrist_row["received_at_ms"]), int(chest_row["received_at_ms"]), 1, DB_SYNC_RECEIVED_AT_TOLERANCE_MS

    @classmethod
    def _match_rows_by_timestamp(cls, wrist_rows: list[dict[str, Any]], chest_rows: list[dict[str, Any]]) -> list[tuple[dict[str, Any], dict[str, Any]]]:
        if not wrist_rows or not chest_rows:
            return []

        # Build seq → chest_row index for O(1) exact seq lookup
        chest_by_seq: dict[int, int] = {}
        for idx, chest_row in enumerate(chest_rows):
            seq = chest_row.get("sq", -1)
            if seq >= 0 and seq not in chest_by_seq:
                chest_by_seq[seq] = idx

        used_chest: set[int] = set()
        pairs: list[tuple[dict[str, Any], dict[str, Any]]] = []

        for wrist in wrist_rows:
            wrist_seq = wrist.get("sq", -1)

            # 1. Exact seq match — always preferred
            if wrist_seq >= 0 and wrist_seq in chest_by_seq:
                chest_idx = chest_by_seq[wrist_seq]
                if chest_idx not in used_chest:
                    used_chest.add(chest_idx)
                    pairs.append((wrist, chest_rows[chest_idx]))
                    continue

            # 2. Timestamp fallback — wider tolerance to handle upload jitter
            best_index: Optional[int] = None
            best_delta = float("inf")
            for idx, chest_row in enumerate(chest_rows):
                if idx in used_chest:
                    continue
                wrist_ts, chest_ts, _, tolerance_ms = cls._pair_timestamps_ms(wrist, chest_row)
                # Use 2× tolerance to compensate for async upload delays
                delta = abs(chest_ts - wrist_ts)
                if delta <= tolerance_ms * 2 and delta < best_delta:
                    best_delta = delta
                    best_index = idx

            if best_index is not None:
                used_chest.add(best_index)
                pairs.append((wrist, chest_rows[best_index]))

        return pairs

    async def _attempt_db_synced_inference(self, user_id: str, state: UserStreamState) -> tuple[Optional[InferredReading], str]:
        db = self._db_factory()
        try:
            wrist_rows = self._load_wrist_sync_rows(db, user_id, state.last_synced_wrist_received_at_ms)
            chest_rows = self._load_chest_sync_rows(db, user_id, state.last_synced_chest_received_at_ms)
        finally:
            db.close()

        matched_pairs = self._match_rows_by_timestamp(wrist_rows, chest_rows)
        matched_count = len(matched_pairs)
        state.mode = "waveform"
        state.db_buffer_fill = f"{min(matched_count, WAVEFORM_WINDOW_FRAMES)}/{WAVEFORM_WINDOW_FRAMES}"

        if matched_count < WAVEFORM_WINDOW_FRAMES:
            return None, state.db_buffer_fill

        selected_pairs = matched_pairs[:WAVEFORM_WINDOW_FRAMES]
        frames: list[BLEFrame] = []
        for wrist, chest in selected_pairs:
            payload = {
                "ts": int(wrist.get("device_timestamp_ms") or wrist["received_at_ms"]),
                "sq": int(wrist["sq"]),
                "pi": wrist["ppg_ir_batch"],
                "pr": wrist["ppg_red_batch"],
                "ax": 0,
                "ay": 0,
                "az": 0,
                "gx": 0,
                "gy": 0,
                "gz": 0,
                "tp": float(wrist.get("tp", 0.0)),
                "ep": int(chest.get("ep", 0)),
                "cs": int(chest["sq"]),
                "ecg": [int(round(v)) for v in chest["ecg"]],
                "qi_w": int(wrist.get("qi_w", 0)),
                "qi_c": int(chest.get("qi_c", 0)),
                "qi": 1 if int(wrist.get("qi_w", 0)) == 1 and int(chest.get("qi_c", 0)) == 1 else 0,
                "bt": 100,
            }
            frame = BLEFrame.model_validate(payload)
            frame.received_at_ms = int(wrist["received_at_ms"])
            frames.append(frame)

        waveform_window = self._build_waveform_window(frames)
        inferred = None

        if waveform_window is not None:
            good_count = sum(1 for frame in frames if frame.qi == 1)
            if good_count >= self._min_quality_frames(len(frames)):
                inferred = await self._run_inference_and_store(user_id, state, frames, waveform_window)
            else:
                logger.info(
                    "DB synced window skipped for %s: %d/%d good frames",
                    user_id,
                    good_count,
                    len(frames),
                )
        else:
            logger.info("DB synced window for %s did not produce a valid waveform window.", user_id)

        last_wrist_ms = int(selected_pairs[-1][0]["received_at_ms"])
        last_chest_ms = int(selected_pairs[-1][1]["received_at_ms"])
        state.last_synced_wrist_received_at_ms = max(state.last_synced_wrist_received_at_ms, last_wrist_ms)
        state.last_synced_chest_received_at_ms = max(state.last_synced_chest_received_at_ms, last_chest_ms)
        state.db_buffer_fill = f"0/{WAVEFORM_WINDOW_FRAMES}"
        return inferred, state.db_buffer_fill

    async def _run_inference_and_store(
        self,
        user_id: str,
        state: UserStreamState,
        frames: list[BLEFrame],
        waveform_window: Optional["WaveformWindow"],
    ) -> Optional[InferredReading]:
        heart_rate = self._calc_heart_rate(frames, waveform_window=waveform_window)
        ptt, ptt_std = self._calc_ptt(frames, waveform_window=waveform_window)
        user_age = self._user_ages.get(user_id, self._user_age)
        try:
            from backend.database import UserSessionLocal

            with UserSessionLocal() as pg_db:
                user_record = resolve_user_by_sensor_key(pg_db, user_id)
                if user_record:
                    user_age = user_record.age
                    self._user_ages[user_id] = user_age
        except Exception as exc:
            logger.warning("Could not fetch user age for %s, using %s: %s", user_id, user_age, exc)

        try:
            from backend.services.bp_model_service import predict_live_blood_pressure

            result = predict_live_blood_pressure(
                ptt=ptt,
                heart_rate=heart_rate,
                age=user_age,
                ptt_std=ptt_std,
                waveform_window=waveform_window,
            )
        except Exception as exc:
            logger.error("ML inference error for %s: %s", user_id, exc)
            return None

        avg_quality = round(sum(frame.qi for frame in frames) / len(frames) * 100) if frames else 0
        reading_time = datetime.now(timezone.utc)
        inferred = InferredReading(
            timestamp=int(reading_time.timestamp() * 1000),
            systolic=result["systolic"],
            diastolic=result["diastolic"],
            heart_rate=int(round(heart_rate or 0.0)),
            ptt=round(float(ptt or 0.0), 2),
            quality=avg_quality,
            category=result["category"],
            model=result.get("model"),
            message=result.get("message"),
        )

        db = self._db_factory()
        try:
            self._dynamic_insert(db, "bp_readings", {
                "time": reading_time,
                "user_id": user_id,
                "systolic": inferred.systolic,
                "diastolic": inferred.diastolic,
                "heart_rate": inferred.heart_rate,
                "spo2": None,
                "ptt": inferred.ptt,
                "quality": inferred.quality,
                "category": inferred.category,
                "model_name": inferred.model,
                "stream_mode": state.mode,
                "window_frames": len(frames),
                "source_seq_start": frames[0].sq if frames else None,
                "source_seq_end": frames[-1].sq if frames else None,
            })
            db.commit()
            state.bp_inferences += 1
            state.last_inferred_reading = inferred
            self._bp_inferences += 1
            logger.info(
                "BP saved for %s: SYS=%d DIA=%d HR=%d PTT=%.1fms model=%s",
                user_id,
                inferred.systolic,
                inferred.diastolic,
                inferred.heart_rate,
                inferred.ptt,
                inferred.model,
            )
            return inferred
        except Exception as exc:
            db.rollback()
            logger.error("bp_readings DB error for %s: %s", user_id, exc)
            return None
        finally:
            db.close()

    async def _run_ml_window(self, user_id: str, state: UserStreamState) -> Optional[InferredReading]:
        frames = list(state.window)
        state.window.clear()

        good_count = sum(1 for frame in frames if frame.qi == 1)
        if good_count < self._min_quality_frames(len(frames)):
            logger.info(
                "ML window skipped for %s: %d/%d good frames in %s mode",
                user_id,
                good_count,
                len(frames),
                state.mode,
            )
            return None

        waveform_window = None
        if state.mode == "waveform":
            raw_db = self._db_factory()
            try:
                waveform_window = self._build_waveform_window_from_timescale(raw_db, user_id, frames)
            except Exception as exc:
                logger.warning("Timescale waveform load failed for %s: %s", user_id, exc)
            finally:
                raw_db.close()

            if waveform_window is None:
                waveform_window = self._build_waveform_window(frames)

        return await self._run_inference_and_store(user_id, state, frames, waveform_window)

    @staticmethod
    def _frame_duration_seconds(frames, waveform_window=None) -> float:
        if waveform_window is not None or any(frame.has_waveform_batch for frame in frames):
            return max(len(frames) * WAVEFORM_FRAME_INTERVAL_MS / 1000.0, 1e-6)
        return max(len(frames) * LEGACY_FRAME_INTERVAL_MS / 1000.0, 1e-6)

    @classmethod
    def _calc_heart_rate(cls, frames, waveform_window=None):
        duration_seconds = cls._frame_duration_seconds(frames, waveform_window=waveform_window)
        r_peak_count = sum(1 for frame in frames if frame.ep == 1)
        min_peaks = 1 if duration_seconds < 3.0 else 2

        if r_peak_count >= min_peaks:
            bpm = (r_peak_count / duration_seconds) * 60.0
            if 35.0 <= bpm <= 220.0:
                return bpm

        if waveform_window is not None:
            return cls._calc_heart_rate_from_signal(waveform_window.ppg_ir)

        return None

    @classmethod
    def _calc_ptt(cls, frames, waveform_window=None):
        if waveform_window is not None:
            ptt, ptt_std = cls._calc_waveform_ptt(frames, waveform_window)
            if ptt is not None:
                return ptt, ptt_std
        return cls._calc_legacy_ptt(frames)

    @staticmethod
    def _calc_legacy_ptt(frames):
        r_peak_indices = [index for index, frame in enumerate(frames) if frame.ep == 1]
        if len(r_peak_indices) < 2:
            return None, 0.0

        ir_values = [frame.ppg_ir_latest for frame in frames]
        ir_mean = sum(ir_values) / len(ir_values)
        ppg_peaks = []
        in_peak = False
        peak_val = 0
        peak_idx = 0

        for index, value in enumerate(ir_values):
            if value > ir_mean and not in_peak:
                in_peak = True
                peak_val = value
                peak_idx = index
            elif in_peak and value > peak_val:
                peak_val = value
                peak_idx = index
            elif in_peak and value <= ir_mean:
                ppg_peaks.append(peak_idx)
                in_peak = False

        if not ppg_peaks:
            return None, 0.0

        ptts = []
        for r_peak_index in r_peak_indices:
            next_peak = next((peak for peak in ppg_peaks if peak > r_peak_index), None)
            if next_peak is not None:
                milliseconds = (next_peak - r_peak_index) * LEGACY_FRAME_INTERVAL_MS
                if 100.0 <= milliseconds <= 500.0:
                    ptts.append(milliseconds)

        if len(ptts) < 2:
            return None, 0.0

        return statistics.mean(ptts), statistics.stdev(ptts)

    @classmethod
    def _calc_waveform_ptt(cls, frames, waveform_window):
        ppg_peaks = cls._find_signal_peaks(waveform_window.ppg_ir, minimum_distance=45)
        if not ppg_peaks:
            return None, 0.0

        r_peak_indices = [min(index * 10 + 9, len(waveform_window.ecg) - 1) for index, frame in enumerate(frames) if frame.ep == 1]
        if not r_peak_indices:
            r_peak_indices = cls._find_signal_peaks(waveform_window.ecg, minimum_distance=35, invert=False)

        if not r_peak_indices:
            return None, 0.0

        ptts = []
        for r_index in r_peak_indices:
            next_peak = next((peak for peak in ppg_peaks if peak > r_index), None)
            if next_peak is None:
                continue
            milliseconds = (next_peak - r_index) * ECG_SAMPLE_INTERVAL_MS
            if 100.0 <= milliseconds <= 500.0:
                ptts.append(milliseconds)

        if not ptts:
            return None, 0.0
        if len(ptts) == 1:
            return ptts[0], 0.0
        return statistics.mean(ptts), statistics.stdev(ptts)

    @staticmethod
    def _find_signal_peaks(samples, minimum_distance: int, invert: bool = False):
        if len(samples) < 3:
            return []

        values = np.asarray(samples, dtype=np.float32)
        baseline = float(np.mean(values))
        spread = float(np.std(values))
        if invert:
            values = -values
            baseline = float(np.mean(values))

        threshold = baseline + max(spread * 0.35, 1.0)
        peaks = []

        for index in range(1, len(values) - 1):
            value = float(values[index])
            if value < threshold:
                continue
            if value >= float(values[index - 1]) and value > float(values[index + 1]):
                if not peaks or index - peaks[-1] >= minimum_distance:
                    peaks.append(index)
                elif value > float(values[peaks[-1]]):
                    peaks[-1] = index
        return peaks

    @classmethod
    def _calc_heart_rate_from_signal(cls, samples):
        peaks = cls._find_signal_peaks(samples, minimum_distance=45)
        if not peaks:
            return None

        duration_seconds = len(samples) / 250.0
        if len(peaks) == 1:
            bpm = 60.0 / max(duration_seconds, 1e-6)
            return bpm if 35.0 <= bpm <= 220.0 else None

        intervals = np.diff(np.asarray(peaks, dtype=np.float32)) / 250.0
        if len(intervals) == 0:
            return None
        mean_interval = float(np.mean(intervals))
        if mean_interval <= 0:
            return None
        bpm = 60.0 / mean_interval
        return bpm if 35.0 <= bpm <= 220.0 else None

    @staticmethod
    def _resample_signal(values, target_size: int):
        if len(values) == target_size:
            return [float(value) for value in values]
        if len(values) < 2:
            return []
        source_x = np.linspace(0.0, 1.0, num=len(values), endpoint=True)
        target_x = np.linspace(0.0, 1.0, num=target_size, endpoint=True)
        return np.interp(target_x, source_x, np.asarray(values, dtype=np.float32)).astype(float).tolist()

    @classmethod
    def _build_waveform_window(cls, frames):
        ecg = []
        ppg_ir = []
        ppg_red = []

        for frame in frames:
            if not frame.has_waveform_batch:
                return None
            ecg.extend(frame.ecg_batch)
            ppg_ir.extend(frame.ppg_ir_batch)
            ppg_red.extend(frame.ppg_red_batch)

        if len(ecg) < WAVEFORM_TARGET_SAMPLES or len(ppg_ir) < 2 or len(ppg_red) < 2:
            return None

        ecg = ecg[:WAVEFORM_TARGET_SAMPLES]
        ppg_ir_resampled = cls._resample_signal(ppg_ir, WAVEFORM_TARGET_SAMPLES)
        ppg_red_resampled = cls._resample_signal(ppg_red, WAVEFORM_TARGET_SAMPLES)

        if len(ppg_ir_resampled) != WAVEFORM_TARGET_SAMPLES or len(ppg_red_resampled) != WAVEFORM_TARGET_SAMPLES:
            return None

        from backend.services.bp_model_service import WaveformWindow

        return WaveformWindow(
            ecg=[float(value) for value in ecg],
            ppg_red=ppg_red_resampled,
            ppg_ir=ppg_ir_resampled,
        )
