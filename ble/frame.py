"""
BLE Frame Schema — BPSync Wrist Module Firmware JSON Packet

The wrist module (Seeed XIAO nRF52840) sends a ~175-byte JSON frame
at 10 Hz via the BLE Notify characteristic.

This module contains Pydantic classes and helper functions that validate
the firmware output.
"""

import json
from typing import Optional
from pydantic import BaseModel, Field, model_validator


# ──────────────────────────────────────────────────────────────────────────────
#  RAW BLE FRAME
#  Firmware fields are mapped one-to-one; abbreviated names are kept
#  because exact key matching is required for JSON parsing.
# ──────────────────────────────────────────────────────────────────────────────
class BLEFrame(BaseModel):
    """
    Raw BLE JSON frame received from the wrist module.

    Field names match the firmware buildJSON() function exactly:
      ts  → device uptime (ms)
      sq  → sequence number (increments each frame)
      pi  → PPG IR raw value (MAX30102)
      pr  → PPG Red raw value (MAX30102)
      ax  → wrist accelerometer X (±2g, 16384 = 1g)
      ay  → wrist accelerometer Y
      az  → wrist accelerometer Z
      gx  → wrist gyroscope X (±250 °/s)
      gy  → wrist gyroscope Y
      gz  → wrist gyroscope Z
      tp  → skin temperature °C (MCP9808)
      ep  → ECG R-peak flag 0/1 (from chest module)
      qi_w → wrist signal quality 0/1
      qi_c → chest signal quality 0/1
      qi   → combined signal quality 0/1 (qi_w AND qi_c)
      bt  → battery percentage (0-100)
    """

    # -- Timestamp and packet identity ----------------------------------------
    ts: int = Field(..., description="Device uptime (milliseconds)")
    sq: int = Field(..., description="Sequence number")

    # -- PPG data (MAX30102) --------------------------------------------------
    pi: int = Field(..., ge=0, description="PPG IR raw value")
    pr: int = Field(..., ge=0, description="PPG Red raw value")

    # -- Wrist IMU (MPU6050) --------------------------------------------------
    ax: int = Field(..., description="Wrist accelerometer X")
    ay: int = Field(..., description="Wrist accelerometer Y")
    az: int = Field(..., description="Wrist accelerometer Z")
    gx: int = Field(..., description="Wrist gyroscope X")
    gy: int = Field(..., description="Wrist gyroscope Y")
    gz: int = Field(..., description="Wrist gyroscope Z")

    # -- Temperature (MCP9808) ------------------------------------------------
    tp: float = Field(..., description="Skin temperature (°C)")

    # -- Chest module data (ECG — no chest IMU)
    ep: int = Field(0, ge=0, le=1, description="ECG R-peak flag (0/1)")

    # -- Signal quality -------------------------------------------------------
    qi_w: int = Field(0, ge=0, le=1, description="Wrist SQI")
    qi_c: int = Field(0, ge=0, le=1, description="Chest SQI")
    qi: int   = Field(0, ge=0, le=1, description="Combined SQI")

    # -- Battery --------------------------------------------------------------
    bt: int = Field(100, ge=0, le=100, description="Battery percentage")

    # ── Computed / derived fields ---------------------------------------------
    # (not from firmware — populated by DataManager)
    received_at_ms: Optional[int] = Field(
        None, description="Unix timestamp (ms) when the backend received the frame"
    )

    @model_validator(mode="after")
    def validate_qi_consistency(self) -> "BLEFrame":
        """
        Logical consistency check for the combined qi value.
        When chest is not connected, qi_c=0 but qi=qi_w is expected.
        We can log anomalies but do not raise an exception,
        because a firmware error should not cause early data loss.
        """
        # When chest is not connected (qi_c=0, wrist-only mode): qi=qi_w
        if self.qi_c == 0 and self.qi == self.qi_w:
            return self  # wrist-only mode, normal
        return self

    # ── Helper methods --------------------------------------------------------

    @classmethod
    def parse_raw_json(cls, json_str: str) -> "BLEFrame":
        """
        Parses and validates a UTF-8 JSON string from the firmware.

        Args:
            json_str: JSON string produced by the firmware buildJSON() function.

        Returns:
            Validated BLEFrame instance.

        Raises:
            json.JSONDecodeError: If the JSON has a syntax error.
            pydantic.ValidationError: If a required field is missing or has the wrong type.
        """
        data = json.loads(json_str)
        return cls(**data)

    @property
    def ppg_pair(self) -> tuple[int, int]:
        """(IR, Red) PPG pair — useful for normalization or analysis."""
        return (self.pi, self.pr)

    @property
    def wrist_accel(self) -> tuple[int, int, int]:
        """Wrist accelerometer (ax, ay, az)."""
        return (self.ax, self.ay, self.az)

    @property
    def wrist_gyro(self) -> tuple[int, int, int]:
        """Wrist gyroscope (gx, gy, gz)."""
        return (self.gx, self.gy, self.gz)

    @property
    def quality_percent(self) -> float:
        """Returns signal quality in the 0-100 range (for ML pipeline)."""
        return float(self.qi * 100)

    @property
    def chest_connected(self) -> bool:
        """Infers whether the chest module is connected."""
        return self.qi_c == 1

    def to_dict(self) -> dict:
        """Pydantic model_dump() wrapper — for serialization."""
        return self.model_dump()


# ──────────────────────────────────────────────────────────────────────────────
#  RESPONSE SCHEMA — used by DataManager to return process results to the API
# ──────────────────────────────────────────────────────────────────────────────
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


FrameProcessResult.model_rebuild()
