"""
BLE frame schemas for wrist-module JSON packets.

The backend now accepts two live firmware shapes:
- Legacy scalar frames with single pi/pr values at 10 Hz
- New waveform batch frames with ECG[10] + PPG IR/RED[8] batches at 25 Hz
"""

from __future__ import annotations

import json
from typing import Any, Optional

from pydantic import BaseModel, Field, model_validator


RawPpgValue = int | list[int]


class BLEFrame(BaseModel):
    """Raw BLE JSON frame received from the wrist module."""

    ts: int = Field(..., description="Device uptime in milliseconds")
    sq: int = Field(..., description="Frame sequence number")

    pi: RawPpgValue = Field(..., description="PPG IR sample or batch")
    pr: RawPpgValue = Field(..., description="PPG RED sample or batch")

    ax: int = Field(..., description="Wrist accelerometer X")
    ay: int = Field(..., description="Wrist accelerometer Y")
    az: int = Field(..., description="Wrist accelerometer Z")
    gx: int = Field(..., description="Wrist gyroscope X")
    gy: int = Field(..., description="Wrist gyroscope Y")
    gz: int = Field(..., description="Wrist gyroscope Z")

    tp: float = Field(..., description="Skin temperature in Celsius")

    ep: int = Field(0, ge=0, le=1, description="ECG R-peak flag")
    cs: Optional[int] = Field(None, description="Chest packet sequence number")
    ecg: Optional[list[int]] = Field(None, description="Raw ECG batch")

    qi_w: int = Field(0, ge=0, le=1, description="Wrist SQI")
    qi_c: int = Field(0, ge=0, le=1, description="Chest SQI")
    qi: int = Field(0, ge=0, le=1, description="Combined SQI")
    bt: int = Field(100, ge=0, le=100, description="Battery percentage")

    received_at_ms: Optional[int] = Field(
        None,
        description="Unix timestamp in milliseconds when the backend received the frame",
    )

    @model_validator(mode="before")
    @classmethod
    def normalize_batch_fields(cls, data: Any) -> Any:
        """Accept array fields either as native JSON arrays or array strings."""
        if not isinstance(data, dict):
            return data

        normalized = dict(data)
        for key in ("pi", "pr", "ecg"):
            value = normalized.get(key)
            if isinstance(value, str):
                stripped = value.strip()
                if stripped.startswith("[") and stripped.endswith("]"):
                    normalized[key] = json.loads(stripped)
        return normalized

    @model_validator(mode="after")
    def validate_qi_consistency(self) -> "BLEFrame":
        if self.qi_c == 0 and self.qi == self.qi_w:
            return self
        return self

    @classmethod
    def parse_raw_json(cls, json_str: str) -> "BLEFrame":
        data = json.loads(json_str)
        return cls(**data)

    @staticmethod
    def _as_series(value: RawPpgValue | None) -> list[int]:
        if value is None:
            return []
        if isinstance(value, list):
            return [int(item) for item in value]
        return [int(value)]

    @property
    def ppg_ir_batch(self) -> list[int]:
        return self._as_series(self.pi)

    @property
    def ppg_red_batch(self) -> list[int]:
        return self._as_series(self.pr)

    @property
    def ecg_batch(self) -> list[int]:
        return [int(item) for item in (self.ecg or [])]

    @property
    def ppg_ir_latest(self) -> int:
        batch = self.ppg_ir_batch
        return batch[-1] if batch else 0

    @property
    def ppg_red_latest(self) -> int:
        batch = self.ppg_red_batch
        return batch[-1] if batch else 0

    @property
    def ppg_pair(self) -> tuple[int, int]:
        return (self.ppg_ir_latest, self.ppg_red_latest)

    @property
    def wrist_accel(self) -> tuple[int, int, int]:
        return (self.ax, self.ay, self.az)

    @property
    def wrist_gyro(self) -> tuple[int, int, int]:
        return (self.gx, self.gy, self.gz)

    @property
    def quality_percent(self) -> float:
        return float(self.qi * 100)

    @property
    def chest_connected(self) -> bool:
        return self.qi_c == 1

    @property
    def has_waveform_batch(self) -> bool:
        return (
            isinstance(self.pi, list)
            and isinstance(self.pr, list)
            and len(self.ecg_batch) > 0
        )

    @property
    def frame_mode(self) -> str:
        return "waveform" if self.has_waveform_batch else "legacy"

    @property
    def waveform_lengths(self) -> tuple[int, int, int]:
        return (len(self.ecg_batch), len(self.ppg_red_batch), len(self.ppg_ir_batch))

    def to_dict(self) -> dict:
        return self.model_dump()


class FrameProcessResult(BaseModel):
    """Result of DataManager.process_frame()."""

    success: bool
    reading_id: Optional[str] = None
    seq_num: int
    quality: float
    buffer_fill: Optional[str] = None
    reading_created: bool = False
    reading: Optional["InferredReading"] = None
    message: Optional[str] = None


class InferredReading(BaseModel):
    """A BP reading inferred from a completed BLE frame window."""

    timestamp: int
    systolic: int
    diastolic: int
    heart_rate: int
    ptt: float
    quality: int
    category: str
    model: Optional[str] = None
    message: Optional[str] = None


FrameProcessResult.model_rebuild()
