"""
Data Manager — BLE Frame → Database Writer

Responsibilities:
  1. BLEFrame (validated Pydantic object) → HealthReading (SQLAlchemy model)
  2. Handles required field mappings (abbreviated firmware names ↔ DB columns)
  3. Manages DB session lifecycle (commit / rollback)
  4. Calculates quality score
  5. Stores the raw frame in a JSON column (for debugging / re-processing)

NOTE — Current HealthReading model columns:
  id, user_id, timestamp, heart_rate, systolic_bp, diastolic_bp,
  spo2, temperature, ecg_data, ppg_data, ptt, quality_score

  This DataManager uses those existing columns.
  Adding raw_frame, seq_num, battery columns to health_reading.py is
  recommended in a future migration (see integration note below).
"""

import logging
import time
from typing import Optional, Callable

from sqlalchemy.orm import Session

from schemas.ble_frame import BLEFrame, FrameProcessResult

logger = logging.getLogger(__name__)


# ──────────────────────────────────────────────────────────────────────────────
#  DATA MANAGER
# ──────────────────────────────────────────────────────────────────────────────
class DataManager:
    """
    Service class that writes BLE frames to the database.

    Usage:
        db_factory = lambda: SessionLocal()  # Or FastAPI Depends
        dm = DataManager(db_factory=db_factory, default_user_id="usr-123")
        result = await dm.process_frame(json_str)

    Args:
        db_factory: Callable that returns a new SQLAlchemy Session on each call.
        default_user_id: Default user ID for this BLE connection.
    """

    def __init__(
        self,
        db_factory: Callable[[], Session],
        default_user_id: str = "",
    ) -> None:
        self._db_factory = db_factory
        self._default_user_id = default_user_id
        self._frames_processed: int = 0
        self._frames_failed: int = 0
        logger.info("DataManager initialized (user_id=%s)", default_user_id)

    # ── Public API ────────────────────────────────────────────────────────────

    async def process_frame(
        self,
        json_str: str,
        user_id: Optional[str] = None,
    ) -> FrameProcessResult:
        """
        Processes a raw JSON string from the firmware and saves it to the DB.

        Steps:
          1. JSON parse + Pydantic validation (BLEFrame)
          2. Create HealthReading ORM object
          3. DB commit
          4. Return FrameProcessResult

        Args:
            json_str: Firmware buildJSON() output (UTF-8 JSON string).
            user_id: User to assign the record to. None → default_user_id.

        Returns:
            FrameProcessResult — success/error status, reading id, quality.
        """
        uid = user_id or self._default_user_id

        # ── 1. Parse & Validate ───────────────────────────────────────────────
        try:
            frame = BLEFrame.parse_raw_json(json_str)
            frame.received_at_ms = int(time.time() * 1000)
        except Exception as exc:
            self._frames_failed += 1
            logger.warning("Frame parse error: %s | raw=%s", exc, json_str[:80])
            return FrameProcessResult(
                success=False,
                seq_num=0,
                quality=0.0,
                message=f"Parse error: {exc}",
            )

        # ── 2. Create ORM object ──────────────────────────────────────────────
        try:
            reading = self._frame_to_reading(frame, uid)
        except Exception as exc:
            self._frames_failed += 1
            logger.error("Frame→Reading conversion error: %s", exc)
            return FrameProcessResult(
                success=False,
                seq_num=frame.sq,
                quality=frame.quality_percent,
                message=f"Conversion error: {exc}",
            )

        # ── 3. Save to DB ─────────────────────────────────────────────────────
        db: Session = self._db_factory()
        try:
            db.add(reading)
            db.commit()
            db.refresh(reading)
            self._frames_processed += 1

            logger.debug(
                "Frame saved: seq=%d qi=%.0f%% id=%s",
                frame.sq,
                frame.quality_percent,
                reading.id,
            )

            return FrameProcessResult(
                success=True,
                reading_id=reading.id,
                seq_num=frame.sq,
                quality=frame.quality_percent,
            )

        except Exception as exc:
            db.rollback()
            self._frames_failed += 1
            logger.error("DB write error (seq=%d): %s", frame.sq, exc)
            return FrameProcessResult(
                success=False,
                seq_num=frame.sq,
                quality=frame.quality_percent,
                message=f"DB error: {exc}",
            )
        finally:
            db.close()

    def get_stats(self) -> dict:
        """Returns processed/failed frame counts (for monitoring)."""
        total = self._frames_processed + self._frames_failed
        return {
            "processed": self._frames_processed,
            "failed": self._frames_failed,
            "total": total,
            "success_rate": (
                round(self._frames_processed / total * 100, 1) if total else 0.0
            ),
        }

    def set_user_id(self, user_id: str) -> None:
        """Change the active user ID (called on session change)."""
        self._default_user_id = user_id
        logger.info("DataManager: active user_id=%s", user_id)

    # ── Private Helpers ───────────────────────────────────────────────────────

    def _frame_to_reading(self, frame: BLEFrame, user_id: str):
        """
        BLEFrame → HealthReading ORM object.

        Mapping to current HealthReading columns:
          timestamp   ← frame.received_at_ms  (backend receive time)
          temperature ← frame.tp
          ppg_data    ← [pi, pr]  (IR, Red pair packed into a list)
          ecg_data    ← [ep]      (R-peak flag; will be full ECG array in future)
          quality_score ← qi * 100

        NOTE: Heart rate, SpO2, and blood pressure (systolic/diastolic) are NOT
        calculated here. They are derived from the PPG signal by MLService
        (ml_service.py) and written to the DB in a separate step.

        Future columns (activated once health_reading.py is updated):
          raw_frame   ← frame.to_dict()
          seq_num     ← frame.sq
          battery     ← frame.bt
        """
        from app.models.health_reading import HealthReading
        import uuid

        reading = HealthReading(
            id=str(uuid.uuid4()),
            user_id=user_id,
            timestamp=frame.received_at_ms or int(time.time() * 1000),
            temperature=frame.tp,
            ppg_data=[frame.pi, frame.pr],
            ecg_data=[frame.ep],
            quality_score=frame.quality_percent,
        )

        # Automatically activated once new columns are added to health_reading.py
        _safe_set(reading, "raw_frame", frame.to_dict())
        _safe_set(reading, "seq_num", frame.sq)
        _safe_set(reading, "battery", frame.bt)

        return reading


# ──────────────────────────────────────────────────────────────────────────────
#  HELPER
# ──────────────────────────────────────────────────────────────────────────────
def _safe_set(obj, attr: str, value) -> None:
    """
    Safely sets an attribute on an ORM object.
    Silently skips if the corresponding SQLAlchemy column is not yet defined on
    the model (for backwards compatibility before running migrations).
    """
    try:
        mapper = type(obj).__mapper__
        if attr in [c.key for c in mapper.columns]:
            setattr(obj, attr, value)
    except Exception:
        pass
