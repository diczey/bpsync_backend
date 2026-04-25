"""
Blood pressure model service — ResBlock-BiLSTM fusion model.

Input : (batch, 1250, 3) — [ECG, PPG_RED, PPG_IR] at 125 Hz, 10 s window
Output: [SBP, DBP] in mmHg directly (no inverse transform needed)
Normalization: per-segment min-max per channel, applied before inference
"""

from __future__ import annotations

import importlib.util
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np

from backend.config import settings

REPO_ROOT = Path(__file__).resolve().parents[2]

CNN_MODEL_CANDIDATES = [
    REPO_ROOT / "model_fused_resblock_bilstm1.keras",
    REPO_ROOT / "CNNmodels" / "model_fused_resblock_bilstm1.keras",
]

EXPECTED_SAMPLES = 1250  # 125 Hz × 10 s


def _first_existing(candidates: List[Path]) -> Optional[Path]:
    for candidate in candidates:
        if candidate.exists():
            return candidate
    return None


def _minmax_normalize(signal: np.ndarray) -> np.ndarray:
    """Per-segment min-max normalization to [0, 1]."""
    mn = signal.min()
    mx = signal.max()
    rng = mx - mn
    if rng == 0:
        return np.zeros_like(signal)
    return (signal - mn) / rng


@dataclass
class WaveformWindow:
    ecg: List[float]
    ppg_red: List[float]
    ppg_ir: List[float]

    @property
    def is_valid(self) -> bool:
        return (
            len(self.ecg) == EXPECTED_SAMPLES
            and len(self.ppg_red) == EXPECTED_SAMPLES
            and len(self.ppg_ir) == EXPECTED_SAMPLES
        )


class CNNBPModel:
    """Lazy-load wrapper for the ResBlock-BiLSTM fusion model."""

    def __init__(self):
        self._model = None
        self._load_error: Optional[str] = None

    @staticmethod
    def _tensorflow_available() -> bool:
        return importlib.util.find_spec("tensorflow") is not None

    def model_path(self) -> Optional[Path]:
        return _first_existing(CNN_MODEL_CANDIDATES)

    def missing_requirements(self) -> List[str]:
        missing: List[str] = []
        if not self._tensorflow_available():
            missing.append("tensorflow is not installed")
        if self.model_path() is None:
            missing.append("missing model: model_fused_resblock_bilstm1.keras")
        return missing

    def is_ready(self) -> bool:
        return not self.missing_requirements()

    def status_message(self) -> str:
        missing = self.missing_requirements()
        if missing:
            return "; ".join(missing)
        if self._load_error:
            return self._load_error
        return "ResBlock-BiLSTM model is ready."

    def _lazy_load(self) -> bool:
        if self._model is not None:
            return True
        model_path = self.model_path()
        if not self.is_ready() or model_path is None:
            self._load_error = self.status_message()
            return False
        try:
            from tensorflow.keras.models import load_model  # type: ignore
            self._model = load_model(model_path)
            self._load_error = None
            return True
        except Exception as exc:
            self._load_error = f"could not load model: {exc}"
            self._model = None
            return False

    def predict(self, waveform_window: WaveformWindow) -> Tuple[int, int]:
        if not waveform_window.is_valid:
            raise ValueError(
                f"Model expects {EXPECTED_SAMPLES} samples per channel "
                f"(ECG={len(waveform_window.ecg)}, "
                f"RED={len(waveform_window.ppg_red)}, "
                f"IR={len(waveform_window.ppg_ir)})"
            )
        if not self._lazy_load():
            raise RuntimeError(self.status_message())

        ecg = _minmax_normalize(np.asarray(waveform_window.ecg, dtype=np.float32))
        red = _minmax_normalize(np.asarray(waveform_window.ppg_red, dtype=np.float32))
        ir  = _minmax_normalize(np.asarray(waveform_window.ppg_ir,  dtype=np.float32))

        # Channel order expected by the model: [ECG, PPG_RED, PPG_IR]
        stacked = np.stack((ecg, red, ir), axis=-1)          # (1250, 3)
        tensor  = stacked.reshape(1, EXPECTED_SAMPLES, 3)    # (1, 1250, 3)

        prediction = self._model.predict(tensor, verbose=0)  # [[SBP, DBP]]
        sbp = int(np.clip(round(float(prediction[0][0])), 70, 200))
        dbp = int(np.clip(round(float(prediction[0][1])), 40, 130))
        if sbp <= dbp + 15:
            sbp = dbp + 25
        return sbp, dbp


class BloodPressurePredictionService:
    def __init__(self):
        self._cnn = CNNBPModel()

    @staticmethod
    def _categorize(systolic: int, diastolic: int) -> str:
        if systolic < 120 and diastolic < 80:
            return "Normal"
        if systolic < 130 and diastolic < 80:
            return "Elevated"
        if systolic < 140 or diastolic < 90:
            return "Stage 1 Hypertension"
        if systolic < 180 or diastolic < 120:
            return "Stage 2 Hypertension"
        return "Hypertensive Crisis"

    def live_ble_cnn_reason(self, waveform_window: Optional[WaveformWindow]) -> Optional[str]:
        if waveform_window is None:
            return "No waveform window provided."
        if not waveform_window.is_valid:
            return (
                f"Waveform window invalid — expected {EXPECTED_SAMPLES} samples "
                f"(ECG={len(waveform_window.ecg)}, "
                f"RED={len(waveform_window.ppg_red)}, "
                f"IR={len(waveform_window.ppg_ir)})"
            )
        if not self._cnn.is_ready():
            return self._cnn.status_message()
        return None

    def predict_live(
        self,
        *,
        ptt: Optional[float] = None,
        heart_rate: Optional[float] = None,
        age: float = 40,
        ptt_std: float = 15,
        waveform_window: Optional[WaveformWindow] = None,
    ) -> Dict:
        reason = self.live_ble_cnn_reason(waveform_window)
        if reason is not None or waveform_window is None:
            raise RuntimeError(f"ResBlock-BiLSTM requires valid waveform: {reason}")

        systolic, diastolic = self._cnn.predict(waveform_window)
        return {
            "systolic": systolic,
            "diastolic": diastolic,
            "category": self._categorize(systolic, diastolic),
            "model": "resblock_bilstm",
            "model_label": "ResBlock-BiLSTM",
            "message": "ResBlock-BiLSTM fusion model (125Hz, 10s window).",
            "requested_model": "cnn",
        }

    def model_status(self) -> Dict:
        cnn_ready = self._cnn.is_ready()
        model_path = self._cnn.model_path()
        return {
            "requested_model": "cnn",
            "active_model": "resblock_bilstm",
            "active_model_label": "ResBlock-BiLSTM",
            "cnn_model_ready": cnn_ready,
            "cnn_model_path": str(model_path) if model_path else str(CNN_MODEL_CANDIDATES[0]),
            "cnn_missing_requirements": self._cnn.missing_requirements(),
            "live_ble_supports_cnn": True,
            "message": self._cnn.status_message(),
        }


prediction_service = BloodPressurePredictionService()


def get_prediction_service() -> BloodPressurePredictionService:
    return prediction_service


def predict_live_blood_pressure(
    *,
    ptt: Optional[float] = None,
    heart_rate: Optional[float] = None,
    age: float = 40,
    ptt_std: float = 15,
    waveform_window: Optional[WaveformWindow] = None,
) -> Dict:
    return prediction_service.predict_live(
        ptt=ptt,
        heart_rate=heart_rate,
        age=age,
        ptt_std=ptt_std,
        waveform_window=waveform_window,
    )
