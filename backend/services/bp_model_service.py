"""
Blood pressure model selection service.

This module keeps XGBoost as a safe fallback while enabling the CNN-LSTM model
whenever live BLE frames carry the waveform batches required by the model.
"""

from __future__ import annotations

import importlib.util
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import joblib
import numpy as np

from backend.config import settings
from backend.services.ml_service import get_bp_model, predict_blood_pressure as predict_xgboost_bp


REPO_ROOT = Path(__file__).resolve().parents[2]

CNN_MODEL_CANDIDATES = [
    REPO_ROOT / "CNNmodels" / "model_ecg_ppg_red_ir.keras",
]
CNN_SCALER_X_CANDIDATES = [
    REPO_ROOT / "scaler_X_red_ir.pkl",
    REPO_ROOT / "CNNmodels" / "scaler_X_red_ir.pkl",
    REPO_ROOT / "CNNmodels" / "scaler_x_red_ir.joblib",
]
CNN_SCALER_Y_CANDIDATES = [
    REPO_ROOT / "scaler_y_red_ir.pkl",
    REPO_ROOT / "CNNmodels" / "scaler_y_red_ir.pkl",
    REPO_ROOT / "CNNmodels" / "scaler_y_red_ir.joblib",
]

LIVE_BLE_PROTOCOL_MESSAGE = (
    "Backend now supports waveform BLE frames. CNN-LSTM activates when incoming "
    "frames include ECG + PPG_RED + PPG_IR batches and the runtime artifacts are available."
)


def _first_existing(candidates: List[Path]) -> Optional[Path]:
    for candidate in candidates:
        if candidate.exists():
            return candidate
    return None


@dataclass
class WaveformWindow:
    ecg: List[float]
    ppg_red: List[float]
    ppg_ir: List[float]

    @property
    def is_valid(self) -> bool:
        return len(self.ecg) == 250 and len(self.ppg_red) == 250 and len(self.ppg_ir) == 250


class CNNBPModel:
    """Lazy runtime wrapper for the CNN-LSTM model artifacts."""

    def __init__(self):
        self._model = None
        self._scaler_x = None
        self._scaler_y = None
        self._load_error: Optional[str] = None

    @staticmethod
    def _tensorflow_available() -> bool:
        return importlib.util.find_spec("tensorflow") is not None

    def model_path(self) -> Optional[Path]:
        return _first_existing(CNN_MODEL_CANDIDATES)

    def scaler_x_path(self) -> Optional[Path]:
        return _first_existing(CNN_SCALER_X_CANDIDATES)

    def scaler_y_path(self) -> Optional[Path]:
        return _first_existing(CNN_SCALER_Y_CANDIDATES)

    def missing_requirements(self) -> List[str]:
        missing: List[str] = []
        if not self._tensorflow_available():
            missing.append("tensorflow is not installed on the backend runtime")
        if self.model_path() is None:
            missing.append("missing CNN model artifact: model_ecg_ppg_red_ir.keras")
        if self.scaler_x_path() is None:
            missing.append("missing scaler artifact: scaler_X_red_ir.pkl")
        if self.scaler_y_path() is None:
            missing.append("missing scaler artifact: scaler_y_red_ir.pkl")
        return missing

    def is_ready(self) -> bool:
        return not self.missing_requirements()

    def status_message(self) -> str:
        missing = self.missing_requirements()
        if missing:
            return "; ".join(missing)
        if self._load_error:
            return self._load_error
        return "CNN model is ready."

    def _lazy_load(self) -> bool:
        if self._model is not None and self._scaler_x is not None and self._scaler_y is not None:
            return True

        model_path = self.model_path()
        scaler_x_path = self.scaler_x_path()
        scaler_y_path = self.scaler_y_path()
        if not self.is_ready() or model_path is None or scaler_x_path is None or scaler_y_path is None:
            self._load_error = self.status_message()
            return False

        try:
            from tensorflow.keras.models import load_model  # type: ignore

            self._model = load_model(model_path)
            self._scaler_x = joblib.load(scaler_x_path)
            self._scaler_y = joblib.load(scaler_y_path)
            self._load_error = None
            return True
        except Exception as exc:  # pragma: no cover - depends on optional runtime deps
            self._load_error = f"could not load CNN runtime artifacts: {exc}"
            self._model = None
            self._scaler_x = None
            self._scaler_y = None
            return False

    def predict(self, waveform_window: WaveformWindow) -> Tuple[int, int]:
        if not waveform_window.is_valid:
            raise ValueError("CNN expects exactly 250 samples for ECG, PPG_RED, and PPG_IR.")
        if not self._lazy_load():
            raise RuntimeError(self.status_message())

        stacked = np.stack(
            (
                np.asarray(waveform_window.ppg_red, dtype=np.float32),
                np.asarray(waveform_window.ppg_ir, dtype=np.float32),
                np.asarray(waveform_window.ecg, dtype=np.float32),
            ),
            axis=-1,
        )
        flat = stacked.reshape(1, -1)
        scaled = self._scaler_x.transform(flat).reshape(1, 250, 3)
        prediction = self._model.predict(scaled, verbose=0)
        restored = self._scaler_y.inverse_transform(prediction)[0]

        systolic = int(np.clip(round(float(restored[0])), 90, 200))
        diastolic = int(np.clip(round(float(restored[1])), 55, 130))
        if systolic <= diastolic + 15:
            systolic = diastolic + 25
        return systolic, diastolic


class BloodPressurePredictionService:
    def __init__(self):
        self._cnn = CNNBPModel()

    def requested_model(self) -> str:
        raw_value = (settings.bp_model_backend or "auto").strip().lower()
        return raw_value if raw_value in {"auto", "xgboost", "cnn"} else "auto"

    @staticmethod
    def _categorize(systolic: int, diastolic: int) -> str:
        if systolic < 120 and diastolic < 80:
            return "Normal"
        if systolic < 130 and diastolic < 80:
            return "Yuksek Normal"
        if systolic < 140 or diastolic < 90:
            return "Evre 1 Hipertansiyon"
        if systolic < 180 or diastolic < 120:
            return "Evre 2 Hipertansiyon"
        return "Hipertansif Kriz"

    def live_ble_cnn_reason(self, waveform_window: Optional[WaveformWindow]) -> Optional[str]:
        if waveform_window is None:
            return "Incoming BLE frames do not yet include a complete waveform window."
        if not waveform_window.is_valid:
            return "CNN expects 250 samples per channel, but the provided live window shape does not match."
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
        requested_model = self.requested_model()
        cnn_reason = self.live_ble_cnn_reason(waveform_window)

        if requested_model in {"auto", "cnn"} and cnn_reason is None and waveform_window is not None:
            systolic, diastolic = self._cnn.predict(waveform_window)
            category = self._categorize(systolic, diastolic)
            return {
                "systolic": systolic,
                "diastolic": diastolic,
                "category": category,
                "model": "cnn_lstm",
                "model_label": "CNN-LSTM",
                "message": "Using Goksu's CNN-LSTM waveform model.",
                "requested_model": requested_model,
            }

        if ptt is None or heart_rate is None:
            raise RuntimeError(
                "XGBoost fallback requires derived PTT and heart rate, but they were not available."
            )

        fallback = predict_xgboost_bp(
            ptt=ptt,
            heart_rate=heart_rate,
            age=age,
            ptt_std=ptt_std,
        )

        fallback_message: Optional[str] = None
        if requested_model == "cnn":
            fallback_message = f"CNN was requested, but live BLE fell back to XGBoost: {cnn_reason}"
        elif requested_model == "auto" and cnn_reason:
            fallback_message = f"Using XGBoost for live BLE: {cnn_reason}"

        fallback["model"] = "xgboost"
        fallback["model_label"] = "XGBoost"
        fallback["message"] = fallback_message
        fallback["requested_model"] = requested_model
        return fallback

    def model_status(self) -> Dict:
        requested_model = self.requested_model()
        cnn_ready = self._cnn.is_ready()
        model_path = self._cnn.model_path()

        active_model = "xgboost"
        active_model_label = "XGBoost"
        message = LIVE_BLE_PROTOCOL_MESSAGE

        if requested_model in {"auto", "cnn"} and cnn_ready:
            active_model = "cnn_lstm"
            active_model_label = "CNN-LSTM"
            message = "Backend is ready to use CNN-LSTM for waveform BLE batches."
        elif not cnn_ready:
            message = self._cnn.status_message()

        if requested_model == "xgboost":
            active_model = "xgboost"
            active_model_label = "XGBoost"
            message = "Backend is pinned to XGBoost by configuration."

        return {
            "requested_model": requested_model,
            "active_model": active_model,
            "active_model_label": active_model_label,
            "cnn_model_ready": cnn_ready,
            "cnn_model_path": str(model_path) if model_path else str(CNN_MODEL_CANDIDATES[0]),
            "cnn_missing_requirements": self._cnn.missing_requirements(),
            "live_ble_supports_cnn": True,
            "message": message,
            "feature_importance": get_bp_model().get_feature_importance(),
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
