"""
Machine Learning Service - XGBoost Blood Pressure Estimation (OFFLINE)

Bu modül PTT (Pulse Transit Time) ve diğer özelliklerden kan basıncı tahmini yapar.
Model önceden eğitilmiş (offline) olarak yüklenir, sadece tahmin yapar.

Özellikler (Features):
- PTT (Pulse Transit Time) - ms
- Heart Rate (Kalp Atış Hızı) - bpm
- Age (Yaş) - yıl
- PTT Variability (PTT standart sapması) - ms

Hedefler (Targets):
- Sistolik BP (mmHg)
- Diyastolik BP (mmHg)
"""
import os
import json
from typing import Tuple, Optional, Dict, List
from pathlib import Path

import numpy as np
from xgboost import XGBRegressor
import joblib


# Model dosyalarının kaydedileceği klasör
MODEL_DIR = Path(__file__).parent.parent.parent / "models"
MODEL_DIR.mkdir(exist_ok=True)

SYSTOLIC_MODEL_PATH = MODEL_DIR / "xgb_systolic.joblib"
DIASTOLIC_MODEL_PATH = MODEL_DIR / "xgb_diastolic.joblib"
CALIBRATION_PATH = MODEL_DIR / "user_calibration.json"


class XGBoostBPModel:
    """
    XGBoost tabanlı Kan Basıncı Tahmin Modeli (Offline)
    
    Model önceden eğitilmiş olarak yüklenir.
    Yeni verilerle yeniden eğitilebilir.
    Kullanıcıya özel kalibrasyon destekler.
    """
    
    def __init__(self):
        self.model_systolic: Optional[XGBRegressor] = None
        self.model_diastolic: Optional[XGBRegressor] = None
        self.is_loaded = False
        self.calibration: Optional[Dict] = None
        
        # Model varsa yükle, yoksa varsayılan modeli eğit
        self._initialize_model()
    
    def _initialize_model(self):
        """Model dosyalarını yükle veya yeni model oluştur"""
        if SYSTOLIC_MODEL_PATH.exists() and DIASTOLIC_MODEL_PATH.exists():
            self._load_model()
        else:
            print("[ML] Kayıtlı model bulunamadı, varsayılan model eğitiliyor...")
            self._train_default_model()
        
        # Kalibrasyon varsa yükle
        if CALIBRATION_PATH.exists():
            self._load_calibration()
    
    def _load_model(self):
        """Kayıtlı modeli yükle"""
        try:
            self.model_systolic = joblib.load(SYSTOLIC_MODEL_PATH)
            self.model_diastolic = joblib.load(DIASTOLIC_MODEL_PATH)
            self.is_loaded = True
            print(f"[ML] XGBoost modelleri yüklendi: {MODEL_DIR}")
        except Exception as e:
            print(f"[ML] Model yükleme hatası: {e}")
            self._train_default_model()
    
    def _train_default_model(self):
        """
        Varsayılan modeli sentetik veriyle eğit
        
        Gerçek kullanımda bu model, gerçek hasta verileriyle
        yeniden eğitilmelidir.
        """
        print("[ML] Sentetik veri ile varsayılan model eğitiliyor...")
        
        # Sentetik eğitim verisi oluştur
        np.random.seed(42)
        n_samples = 1000
        
        # Özellikler: [PTT, Heart Rate, Age, PTT_std]
        # PTT: 150-400 ms arası
        ptt = np.random.uniform(150, 400, n_samples)
        # Heart Rate: 50-120 bpm arası
        heart_rate = np.random.uniform(50, 120, n_samples)
        # Age: 20-80 yaş arası
        age = np.random.uniform(20, 80, n_samples)
        # PTT variability: 5-30 ms
        ptt_std = np.random.uniform(5, 30, n_samples)
        
        X = np.column_stack([ptt, heart_rate, age, ptt_std])
        
        # Hedefler: PTT ile ters orantılı BP
        # Düşük PTT = Yüksek BP (arterler sert)
        # Formül: Literatürdeki PTT-BP ilişkisine dayalı
        base_systolic = 120
        base_diastolic = 80
        
        # PTT etkisi (ters orantı)
        ptt_effect_sys = -0.15 * (ptt - 275)  # 275ms referans
        ptt_effect_dia = -0.08 * (ptt - 275)
        
        # HR etkisi
        hr_effect_sys = 0.12 * (heart_rate - 70)
        hr_effect_dia = 0.06 * (heart_rate - 70)
        
        # Yaş etkisi
        age_effect_sys = 0.4 * (age - 40)
        age_effect_dia = 0.2 * (age - 40)
        
        # PTT variability etkisi (yüksek variabilite = yüksek BP riski)
        var_effect_sys = 0.3 * (ptt_std - 15)
        var_effect_dia = 0.15 * (ptt_std - 15)
        
        # Gürültü ekle
        noise_sys = np.random.normal(0, 5, n_samples)
        noise_dia = np.random.normal(0, 3, n_samples)
        
        y_systolic = base_systolic + ptt_effect_sys + hr_effect_sys + age_effect_sys + var_effect_sys + noise_sys
        y_diastolic = base_diastolic + ptt_effect_dia + hr_effect_dia + age_effect_dia + var_effect_dia + noise_dia
        
        # Değerleri sınırla
        y_systolic = np.clip(y_systolic, 90, 200)
        y_diastolic = np.clip(y_diastolic, 55, 130)
        
        # XGBoost modelleri oluştur ve eğit
        self.model_systolic = XGBRegressor(
            n_estimators=100,
            max_depth=4,
            learning_rate=0.1,
            objective='reg:squarederror',
            random_state=42
        )
        self.model_diastolic = XGBRegressor(
            n_estimators=100,
            max_depth=4,
            learning_rate=0.1,
            objective='reg:squarederror',
            random_state=42
        )
        
        self.model_systolic.fit(X, y_systolic)
        self.model_diastolic.fit(X, y_diastolic)
        
        self.is_loaded = True
        print("[ML] Varsayılan model eğitimi tamamlandı")
        
        # Modeli kaydet
        self._save_model()
    
    def _save_model(self):
        """Modeli dosyaya kaydet"""
        try:
            joblib.dump(self.model_systolic, SYSTOLIC_MODEL_PATH)
            joblib.dump(self.model_diastolic, DIASTOLIC_MODEL_PATH)
            print(f"[ML] Model kaydedildi: {MODEL_DIR}")
        except Exception as e:
            print(f"[ML] Model kaydetme hatası: {e}")
    
    def _load_calibration(self):
        """Kullanıcı kalibrasyonunu yükle"""
        try:
            with open(CALIBRATION_PATH, 'r') as f:
                self.calibration = json.load(f)
            print(f"[ML] Kalibrasyon yüklendi")
        except Exception as e:
            print(f"[ML] Kalibrasyon yükleme hatası: {e}")
    
    def _save_calibration(self):
        """Kullanıcı kalibrasyonunu kaydet"""
        try:
            with open(CALIBRATION_PATH, 'w') as f:
                json.dump(self.calibration, f)
            print(f"[ML] Kalibrasyon kaydedildi")
        except Exception as e:
            print(f"[ML] Kalibrasyon kaydetme hatası: {e}")
    
    def predict(
        self,
        ptt: float,
        heart_rate: float,
        age: float = 40,
        ptt_std: float = 15
    ) -> Tuple[int, int]:
        """
        Kan basıncı tahmin et
        
        Args:
            ptt: Pulse Transit Time (ms)
            heart_rate: Kalp atış hızı (bpm)
            age: Yaş (varsayılan 40)
            ptt_std: PTT standart sapması (varsayılan 15)
        
        Returns:
            (systolic, diastolic) tuple
        """
        if not self.is_loaded:
            print("[ML] Model yüklenmedi, varsayılan değerler döndürülüyor")
            return 120, 80
        
        # Özellik vektörü
        X = np.array([[ptt, heart_rate, age, ptt_std]])
        
        # Tahmin
        systolic = float(self.model_systolic.predict(X)[0])
        diastolic = float(self.model_diastolic.predict(X)[0])
        
        # Kalibrasyon uygula
        if self.calibration:
            systolic = self._apply_calibration(systolic, 'systolic')
            diastolic = self._apply_calibration(diastolic, 'diastolic')
        
        # Sınırla ve tam sayıya çevir
        systolic = int(np.clip(systolic, 90, 200))
        diastolic = int(np.clip(diastolic, 55, 130))
        
        # Sistolik > Diyastolik olmalı
        if systolic <= diastolic + 15:
            systolic = diastolic + 25
        
        return systolic, diastolic
    
    def _apply_calibration(self, value: float, bp_type: str) -> float:
        """Kalibrasyon offset uygula"""
        if not self.calibration:
            return value
        offset_key = f"{bp_type}_offset"
        if offset_key in self.calibration:
            return value + self.calibration[offset_key]
        return value
    
    def calibrate(
        self,
        measured_systolic: int,
        measured_diastolic: int,
        ptt: float,
        heart_rate: float,
        age: float = 40
    ):
        """
        Modeli kullanıcıya göre kalibre et
        
        Cuff (manşet) ile ölçülen referans değerlerle
        modelin tahminlerini düzeltir.
        
        Args:
            measured_systolic: Manşetle ölçülen sistolik BP
            measured_diastolic: Manşetle ölçülen diyastolik BP
            ptt: Kalibrasyon anındaki PTT
            heart_rate: Kalibrasyon anındaki HR
            age: Kullanıcı yaşı
        """
        # Şu anki tahmin
        predicted_sys, predicted_dia = self.predict(ptt, heart_rate, age)
        
        # Offset hesapla
        systolic_offset = measured_systolic - predicted_sys
        diastolic_offset = measured_diastolic - predicted_dia
        
        self.calibration = {
            "systolic_offset": systolic_offset,
            "diastolic_offset": diastolic_offset,
            "reference_systolic": measured_systolic,
            "reference_diastolic": measured_diastolic,
            "reference_ptt": ptt,
            "reference_hr": heart_rate,
            "age": age
        }
        
        self._save_calibration()
        print(f"[ML] Kalibrasyon tamamlandı: Offset SYS={systolic_offset}, DIA={diastolic_offset}")
    
    def train_with_data(self, training_data: List[Dict]):
        """
        Gerçek verilerle modeli yeniden eğit
        
        Args:
            training_data: [{"ptt": float, "heart_rate": float, "age": float,
                            "ptt_std": float, "systolic": int, "diastolic": int}, ...]
        """
        if len(training_data) < 10:
            print("[ML] Eğitim için en az 10 veri noktası gerekli")
            return False
        
        X = np.array([[
            d["ptt"], d["heart_rate"], d.get("age", 40), d.get("ptt_std", 15)
        ] for d in training_data])
        
        y_systolic = np.array([d["systolic"] for d in training_data])
        y_diastolic = np.array([d["diastolic"] for d in training_data])
        
        # Yeniden eğit
        self.model_systolic.fit(X, y_systolic)
        self.model_diastolic.fit(X, y_diastolic)
        
        self._save_model()
        print(f"[ML] Model {len(training_data)} veri noktasıyla yeniden eğitildi")
        return True
    
    def get_feature_importance(self) -> Dict[str, List[float]]:
        """
        Özellik önemliliklerini döndür (model yorumlama için)
        """
        if not self.is_loaded:
            return {}
        
        feature_names = ["PTT", "Heart Rate", "Age", "PTT Variability"]
        
        return {
            "features": feature_names,
            "systolic_importance": self.model_systolic.feature_importances_.tolist(),
            "diastolic_importance": self.model_diastolic.feature_importances_.tolist()
        }
    
    def calculate_ptt(
        self,
        ecg_r_peak_time: float,
        ppg_peak_time: float
    ) -> Optional[float]:
        """
        ECG R-peak ve PPG peak zamanlarından PTT hesapla
        
        Args:
            ecg_r_peak_time: ECG R-peak zamanı (ms)
            ppg_peak_time: PPG peak zamanı (ms)
        
        Returns:
            PTT (ms) veya None
        """
        ptt = ppg_peak_time - ecg_r_peak_time
        
        # PTT makul aralıkta olmalı (100-500 ms)
        if 100 <= ptt <= 500:
            return round(ptt, 2)
        
        return None


# Global model instance (uygulama başladığında yüklenir)
bp_model = XGBoostBPModel()


def get_bp_model() -> XGBoostBPModel:
    """Global BP model instance'ı döndür"""
    return bp_model


def predict_blood_pressure(
    ptt: float,
    heart_rate: float,
    age: float = 40,
    ptt_std: float = 15
) -> Dict:
    """
    Kan basıncı tahmin et - API için kısa fonksiyon
    
    Returns:
        {"systolic": int, "diastolic": int, "category": str}
    """
    systolic, diastolic = bp_model.predict(ptt, heart_rate, age, ptt_std)
    
    # Kategori belirle
    if systolic < 120 and diastolic < 80:
        category = "Normal"
    elif systolic < 130 and diastolic < 80:
        category = "Yüksek Normal"
    elif systolic < 140 or diastolic < 90:
        category = "Evre 1 Hipertansiyon"
    elif systolic < 180 or diastolic < 120:
        category = "Evre 2 Hipertansiyon"
    else:
        category = "Hipertansif Kriz"
    
    return {
        "systolic": systolic,
        "diastolic": diastolic,
        "category": category
    }
