# -*- coding: utf-8 -*-
"""XGBoost Model Test Script"""

from backend.app.services.ml_service import get_bp_model

model = get_bp_model()
print("=== XGBoost Model Durumu ===")
print(f"Model Yuklu: {model.is_loaded}")
print(f"Kalibrasyon: {model.calibration}")
print()

# Test tahminleri
test_cases = [
    {"ptt": 350, "hr": 60, "age": 30, "desc": "Yuksek PTT, dusuk HR -> Dusuk BP beklenir"},
    {"ptt": 275, "hr": 70, "age": 40, "desc": "Normal PTT, normal HR -> Normal BP beklenir"},
    {"ptt": 200, "hr": 90, "age": 60, "desc": "Dusuk PTT, yuksek HR -> Yuksek BP beklenir"},
    {"ptt": 150, "hr": 100, "age": 70, "desc": "Cok dusuk PTT -> Cok yuksek BP beklenir"},
]

print("=== Test Tahminleri ===")
for tc in test_cases:
    sys_bp, dia_bp = model.predict(tc["ptt"], tc["hr"], tc["age"])
    print(f"PTT={tc['ptt']}ms, HR={tc['hr']}bpm, Yas={tc['age']}")
    print(f"  => Tahmin: {sys_bp}/{dia_bp} mmHg")
    print(f"  => Beklenen: {tc['desc']}")
    print()

# Feature importance
importance = model.get_feature_importance()
print("=== Ozellik Onemlilikleri ===")
for i, feat in enumerate(importance["features"]):
    sys_imp = importance["systolic_importance"][i]
    dia_imp = importance["diastolic_importance"][i]
    bar_sys = "#" * int(sys_imp * 50)
    bar_dia = "#" * int(dia_imp * 50)
    print(f"{feat}:")
    print(f"  Sistolik:   {bar_sys} ({sys_imp:.3f})")
    print(f"  Diyastolik: {bar_dia} ({dia_imp:.3f})")
