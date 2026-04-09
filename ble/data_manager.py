import asyncio
import json
import logging
import math
import statistics
import time
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Dict, Optional

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
MIN_QUALITY_RATIO = 0.6


@dataclass
class UserStreamState:
    window: deque = field(default_factory=lambda: deque(maxlen=WINDOW_SIZE))
    mode: str = "legacy"
    bp_inferences: int = 0
    last_inferred_reading: Optional[InferredReading] = None


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
            frame = BLEFrame.parse_raw_json(json_str)
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

        state.window = deque(maxlen=self._window_target_for_mode("legacy"))
        state.mode = "legacy"
        logger.info("DataManager stream reset for %s", user_id)

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
                "buffer_fill": f"{len(state.window)}/{target}" if state else f"0/{WINDOW_SIZE}",
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

    def _insert_raw_frame(self, db, frame, user_id):
        received_at = datetime.fromtimestamp(frame.received_at_ms / 1000, tz=timezone.utc)
        db.execute(text("""
            INSERT INTO wristband_data
                (
                    time, user_id, device_timestamp_ms, received_at_ms, frame_seq,
                    chest_seq, frame_mode, ppg_ir, ppg_red, ppg_ir_batch,
                    ppg_red_batch, ax, ay, az, gx, gy, gz, temperature, ep,
                    qi_w, qi_c, qi, battery
                )
            VALUES
                (
                    :time, :user_id, :device_timestamp_ms, :received_at_ms, :frame_seq,
                    :chest_seq, :frame_mode, :ppg_ir, :ppg_red,
                    CAST(:ppg_ir_batch AS JSONB), CAST(:ppg_red_batch AS JSONB),
                    :ax, :ay, :az, :gx, :gy, :gz, :temperature, :ep,
                    :qi_w, :qi_c, :qi, :battery
                )
        """), {
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
        })

        ecg_batch = frame.ecg_batch
        if ecg_batch:
            start_at = received_at - timedelta(milliseconds=ECG_SAMPLE_INTERVAL_MS * (len(ecg_batch) - 1))
            db.execute(
                text("""
                    INSERT INTO ecg_data (
                        time, user_id, device_timestamp_ms, received_at_ms,
                        frame_seq, sample_index, ecg_value, ep, qi_c
                    )
                    VALUES (
                        :time, :user_id, :device_timestamp_ms, :received_at_ms,
                        :frame_seq, :sample_index, :ecg_value, :ep, :qi_c
                    )
                """),
                [
                    {
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
                    for index, value in enumerate(ecg_batch)
                ],
            )

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

        waveform_window = self._build_waveform_window(frames) if state.mode == "waveform" else None
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

        avg_quality = round(sum(frame.qi for frame in frames) / len(frames) * 100)
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
            db.execute(text("""
                INSERT INTO bp_readings
                    (
                        time, user_id, systolic, diastolic, heart_rate, spo2, ptt,
                        quality, category, model_name, stream_mode, window_frames,
                        source_seq_start, source_seq_end
                    )
                VALUES
                    (
                        :time, :user_id, :systolic, :diastolic, :heart_rate, :spo2, :ptt,
                        :quality, :category, :model_name, :stream_mode, :window_frames,
                        :source_seq_start, :source_seq_end
                    )
            """), {
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
