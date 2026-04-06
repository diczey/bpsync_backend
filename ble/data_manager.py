import asyncio
import logging
import statistics
import time
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Dict, Optional

from sqlalchemy import text

from ble.frame import BLEFrame, FrameProcessResult, InferredReading
from backend.utils.sensor_identity import resolve_user_by_sensor_key

logger = logging.getLogger(__name__)

WINDOW_SIZE = 100
MIN_QUALITY_FRAMES = 60


@dataclass
class UserStreamState:
    window: deque = field(default_factory=lambda: deque(maxlen=WINDOW_SIZE))
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

            state.window.append(frame)
            inferred = None
            if len(state.window) == WINDOW_SIZE:
                inferred = await self._run_ml_window(uid, state)

            return FrameProcessResult(
                success=True,
                seq_num=frame.sq,
                quality=frame.quality_percent,
                buffer_fill=f"{len(state.window)}/{WINDOW_SIZE}",
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

    def get_stats(self, user_id: Optional[str] = None):
        if user_id:
            state = self._user_states.get(user_id)
            return {
                "processed": self._frames_processed,
                "failed": self._frames_failed,
                "total": self._frames_processed + self._frames_failed,
                "success_rate": self._success_rate(),
                "bp_inferences": state.bp_inferences if state else 0,
                "buffer_fill": f"{len(state.window)}/{WINDOW_SIZE}" if state else f"0/{WINDOW_SIZE}",
            }

        default_state = self._user_states.get(self._default_user_id) if self._default_user_id else None
        return {
            "processed": self._frames_processed,
            "failed": self._frames_failed,
            "total": self._frames_processed + self._frames_failed,
            "success_rate": self._success_rate(),
            "bp_inferences": self._bp_inferences,
            "active_users": len(self._user_states),
            "buffer_fill": f"{len(default_state.window)}/{WINDOW_SIZE}" if default_state else f"0/{WINDOW_SIZE}",
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

    def _insert_raw_frame(self, db, frame, user_id):
        db.execute(text("""
            INSERT INTO wristband_data
                (time, user_id, ppg_ir, ppg_red, ax, ay, az, gx, gy, gz,
                 temperature, ep, qi_w, qi_c, qi, battery)
            VALUES
                (:time, :user_id, :ppg_ir, :ppg_red, :ax, :ay, :az,
                 :gx, :gy, :gz, :temperature, :ep, :qi_w, :qi_c, :qi, :battery)
        """), {
            "time": datetime.fromtimestamp(frame.received_at_ms / 1000, tz=timezone.utc),
            "user_id": user_id,
            "ppg_ir": frame.pi,
            "ppg_red": frame.pr,
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

    async def _run_ml_window(self, user_id: str, state: UserStreamState) -> Optional[InferredReading]:
        frames = list(state.window)
        state.window.clear()

        good_count = sum(1 for frame in frames if frame.qi == 1)
        if good_count < MIN_QUALITY_FRAMES:
            logger.info("ML window skipped for %s: %d/%d good frames", user_id, good_count, WINDOW_SIZE)
            return None

        heart_rate = self._calc_heart_rate(frames)
        ptt, ptt_std = self._calc_ptt(frames)
        if heart_rate is None or ptt is None:
            logger.info("ML window skipped for %s: not enough peaks", user_id)
            return None

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
            from backend.services.ml_service import predict_blood_pressure
            result = predict_blood_pressure(
                ptt=ptt,
                heart_rate=heart_rate,
                age=user_age,
                ptt_std=ptt_std,
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
            heart_rate=int(heart_rate),
            ptt=round(ptt, 2),
            quality=avg_quality,
            category=result["category"],
        )

        db = self._db_factory()
        try:
            db.execute(text("""
                INSERT INTO bp_readings
                    (time, user_id, systolic, diastolic, heart_rate, ptt, quality, category)
                VALUES
                    (:time, :user_id, :systolic, :diastolic, :heart_rate, :ptt, :quality, :category)
            """), {
                "time": reading_time,
                "user_id": user_id,
                "systolic": inferred.systolic,
                "diastolic": inferred.diastolic,
                "heart_rate": inferred.heart_rate,
                "ptt": inferred.ptt,
                "quality": inferred.quality,
                "category": inferred.category,
            })
            db.commit()
            state.bp_inferences += 1
            state.last_inferred_reading = inferred
            self._bp_inferences += 1
            logger.info(
                "BP saved for %s: SYS=%d DIA=%d HR=%d PTT=%.1fms",
                user_id,
                inferred.systolic,
                inferred.diastolic,
                inferred.heart_rate,
                inferred.ptt,
            )
            return inferred
        except Exception as exc:
            db.rollback()
            logger.error("bp_readings DB error for %s: %s", user_id, exc)
            return None
        finally:
            db.close()

    @staticmethod
    def _calc_heart_rate(frames):
        r_peak_count = sum(1 for frame in frames if frame.ep == 1)
        if r_peak_count < 2:
            return None
        return r_peak_count * 6.0

    @staticmethod
    def _calc_ptt(frames):
        r_peak_indices = [index for index, frame in enumerate(frames) if frame.ep == 1]
        if len(r_peak_indices) < 2:
            return None, 0.0

        ir_values = [frame.pi for frame in frames]
        ir_mean = sum(ir_values) / len(ir_values)
        ppg_peaks = []
        in_peak = False
        peak_val = peak_idx = 0

        for index, value in enumerate(ir_values):
            if value > ir_mean and not in_peak:
                in_peak, peak_val, peak_idx = True, value, index
            elif in_peak and value > peak_val:
                peak_val, peak_idx = value, index
            elif in_peak and value <= ir_mean:
                ppg_peaks.append(peak_idx)
                in_peak = False

        if not ppg_peaks:
            return None, 0.0

        ptts = []
        for r_peak_index in r_peak_indices:
            next_peak = next((peak for peak in ppg_peaks if peak > r_peak_index), None)
            if next_peak is not None:
                milliseconds = (next_peak - r_peak_index) * 100.0
                if 100.0 <= milliseconds <= 500.0:
                    ptts.append(milliseconds)

        if len(ptts) < 2:
            return None, 0.0

        return statistics.mean(ptts), statistics.stdev(ptts)
