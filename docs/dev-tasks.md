# BPSync — Developer Task List

> Branch: `devfixed`
> Dosya yapısı yeniden düzenlendi. Aşağıdaki kod değişiklikleri henüz yapılmadı.
> Her madde atanabilir bağımsız bir görevdir.

---

## BACKEND

### 1. `backend/main.py` — Import path düzeltmeleri + BLE entegrasyonu

| Satır | Mevcut | Olması Gereken |
|-------|--------|----------------|
| 11 | `from backend.app.config import settings` | `from backend.config import settings` |
| 12 | `from backend.app.database import create_tables` | `from backend.database import create_tables` |
| 15 | `from backend.app.routers import auth, dashboard, ...` | `from backend.routers import auth, dashboard, ...` |
| 55 | `allow_origins=settings.cors_origins + ["*"]` | `allow_origins=settings.cors_origins` |
| 69 | `app.include_router(sensor.router)` | Altına ekle: BLE router kaydı (madde 13'e bak) |

**Ayrıca** `lifespan` fonksiyonuna BLE başlatma eklenecek:

```python
# startup'a ekle:
from ble.manager import init_ble_manager
from ble.data_manager import DataManager
from backend.database import UserSessionLocal

dm  = DataManager(db_factory=UserSessionLocal, default_user_id="")
ble = init_ble_manager(dm)
await ble.start()

# shutdown'a ekle:
from ble.manager import get_ble_manager
await get_ble_manager().stop()

# router kaydına ekle:
from backend.routers import ble as ble_router
app.include_router(ble_router.router, prefix="/ble", tags=["BLE"])
```

---

### 2. `backend/database.py` — Import düzeltme + duplicate kaldırma

| Satır | Mevcut | Olması Gereken |
|-------|--------|----------------|
| 8 | `from backend.app.config import settings` | `from backend.config import settings` |
| 30–32 | `sensor_engine` ve `SensorSessionLocal` 2. kez tanımlı | Bu 3 satırı tamamen **sil** |

---

### 3. `backend/run.py` — Import ve uvicorn path düzeltmesi

| Satır | Mevcut | Olması Gereken |
|-------|--------|----------------|
| 11 | `from backend.app.config import settings` | `from backend.config import settings` |
| 27 | `"app.main:app"` | `"backend.main:app"` |

---

### 4. `backend/routers/__init__.py` — Circular import kaldırma

| Satır | Mevcut | Olması Gereken |
|-------|--------|----------------|
| 1 | `from backend.app.routers import auth, dashboard, ...` | Satırı tamamen **sil** |

---

### 5. `backend/routers/auth.py` — Import path düzeltmeleri (satır 8–17)

```
backend.app.database       → backend.database
backend.app.models.user    → backend.models.user
backend.app.schemas.auth   → backend.schemas.auth
backend.app.utils.security → backend.utils.security
backend.app.config         → backend.config
```

---

### 6. `backend/routers/dashboard.py` — Import path düzeltmeleri (satır 7–13)

```
backend.app.database              → backend.database
backend.app.models.user           → backend.models.user
backend.app.models.health_reading → backend.models.health_reading
backend.app.schemas.dashboard     → backend.schemas.dashboard
backend.app.utils.security        → backend.utils.security
backend.app.utils.mock_data       → backend.utils.mock_data
backend.app.config                → backend.config
```

---

### 7. `backend/routers/readings.py` — Import path düzeltmeleri (satır 10–21)

```
backend.app.database              → backend.database
backend.app.models.user           → backend.models.user
backend.app.models.health_reading → backend.models.health_reading
backend.app.schemas.health        → backend.schemas.health
backend.app.utils.security        → backend.utils.security
backend.app.utils.mock_data       → backend.utils.mock_data
backend.app.config                → backend.config
backend.app.services.bp_model_service   → backend.services.bp_model_service
```

---

### 8. `backend/routers/trends.py` — Import path düzeltmeleri (satır 8–13)

```
backend.app.database        → backend.database
backend.app.models.user     → backend.models.user
backend.app.schemas.trends  → backend.schemas.trends
backend.app.utils.security  → backend.utils.security
backend.app.utils.mock_data → backend.utils.mock_data
backend.app.config          → backend.config
```

---

### 9. `backend/routers/reports.py` — Import path düzeltmeleri (satır 7–16)

```
backend.app.database        → backend.database
backend.app.models.user     → backend.models.user
backend.app.schemas.reports → backend.schemas.reports
backend.app.utils.security  → backend.utils.security
backend.app.utils.mock_data → backend.utils.mock_data
backend.app.config          → backend.config
```

---

### 10. `backend/routers/notifications.py` — Import path düzeltmeleri (satır 8–14)

```
backend.app.database              → backend.database
backend.app.models.user           → backend.models.user
backend.app.models.notification   → backend.models.notification
backend.app.schemas.notifications → backend.schemas.notifications
backend.app.utils.security        → backend.utils.security
backend.app.utils.mock_data       → backend.utils.mock_data
backend.app.config                → backend.config
```

---

### 11. `backend/routers/profile.py` — Import path düzeltmeleri (satır 7–10)

```
backend.app.database       → backend.database
backend.app.models.user    → backend.models.user
backend.app.schemas.auth   → backend.schemas.auth
backend.app.utils.security → backend.utils.security
```

---

### 12. `backend/routers/sensor.py` — Import düzeltme + JWT + SQL güncelleme

| Satır | Mevcut | Olması Gereken |
|-------|--------|----------------|
| 5 | `from backend.app.database import get_sensor_db` | `from backend.database import get_sensor_db` |
| 6 | `from backend.app.schemas.sensor import ...` | `from backend.schemas.sensor import ...` |
| — | JWT import yok | `from backend.utils.security import get_current_user` ekle |
| 12 | `def ingest_wristband(payload, db=...)` | `current_user=Depends(get_current_user)` parametresi ekle |
| 15 | `INSERT INTO wristband_data (time, patient_id, heart_rate, spo2, movement)` | `database/timescale/schema.sql` güncellendikten sonra yeni sütun adlarıyla güncelle |
| 25 | `def ingest_ecg(payload, db=...)` | `current_user=Depends(get_current_user)` parametresi ekle |

---

### 13. `backend/routers/ble.py` — Import düzeltme + yorum temizliği

| Satır | Mevcut | Olması Gereken |
|-------|--------|----------------|
| 23 | `from services.ble_manager import get_ble_manager, BLEManager` | `from ble.manager import get_ble_manager, BLEManager` |
| 12–13 | `from routes import ble` yorum satırı | Artık geçersiz, **sil** |

---

### 14. `backend/services/bp_model_service.py` — CNN runtime yolu

| Satır | Mevcut | Olması Gereken |
|-------|--------|----------------|
| 28 | `Path(__file__).parent.parent.parent / "models"` | `Path(__file__).parent.parent / "ml_models"` |

> **Not:** Mevcut path 3 üst dizin çıkıyor → `bpsync/models/` (yanlış).
> Doğrusu 2 üst dizin → `backend/ml_models/`

---

### 15. `backend/schemas/sensor.py` — Firmware ile uyumlu hale getir + user_id

Mevcut şema firmware JSON'u ile uyumsuz ve `patient_id: int` kullanıyor. Tümünü yeniden yaz:

```python
# MEVCUT (yanlış)
class WristbandDataIn(BaseModel):
    time: datetime
    patient_id: int          # ← YANLIŞ TİP
    heart_rate: Optional[int]
    spo2: Optional[float]
    movement: Optional[float]

# OLMASI GEREKEN (firmware JSON ile birebir)
class WristbandDataIn(BaseModel):
    time: datetime
    user_id: str             # UUID string
    ppg_ir: int
    ppg_red: int
    ax: int
    ay: int
    az: int
    gx: int
    gy: int
    gz: int
    temperature: float
    ep: int
    qi_w: int
    qi_c: int
    qi: int
    battery: int

class ECGDataIn(BaseModel):
    time: datetime
    user_id: str             # patient_id: int → user_id: str
    ecg_value: float
```

---

### 16. `backend/config.py` — Hardcoded JWT secret kaldırma

| Satır | Mevcut | Olması Gereken |
|-------|--------|----------------|
| 27 | `secret_key: str = "your-super-secret-key-change-this-in-production"` | Default'u kaldır: `secret_key: str` — `.env` dosyasında zorunlu |

---

## DATABASE

### 17. `database/docker-compose.yml` — Volume path + uvicorn komutu

| Satır | Mevcut | Olması Gereken |
|-------|--------|----------------|
| 25 | `./timescale_schema.sql:/docker-entrypoint-initdb.d/...` | `./timescale/schema.sql:/docker-entrypoint-initdb.d/01_schema.sql` |
| 40 | `uvicorn backend.app.main:app --host 0.0.0.0 --port 8000` | `uvicorn backend.main:app --host 0.0.0.0 --port 8000` |

---

### 18. `database/timescale/schema.sql` — Firmware JSON ile uyumlu hale getir

Mevcut tablo `patient_id INT` kullanıyor, firmware'den gelen hiçbir alanı taşımıyor. Tümünü yeniden yaz:

```sql
-- MEVCUT (yanlış)
CREATE TABLE wristband_data (
    time       TIMESTAMPTZ NOT NULL,
    patient_id INT,           -- yanlış tip
    heart_rate INT,           -- firmware'de yok
    spo2       NUMERIC,       -- firmware'de yok
    movement   NUMERIC        -- firmware'de yok
);

-- OLMASI GEREKEN (firmware buildJSON() ile birebir)
CREATE TABLE wristband_data (
    time        TIMESTAMPTZ NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    ppg_ir      BIGINT,
    ppg_red     BIGINT,
    ax          INT,
    ay          INT,
    az          INT,
    gx          INT,
    gy          INT,
    gz          INT,
    temperature FLOAT,
    ep          SMALLINT,
    qi_w        SMALLINT,
    qi_c        SMALLINT,
    qi          SMALLINT,
    battery     SMALLINT
);
SELECT create_hypertable('wristband_data', 'time', if_not_exists => TRUE);
CREATE INDEX idx_wristband_user_time ON wristband_data (user_id, time DESC);

-- ecg_data tablosunda da patient_id → user_id
CREATE TABLE ecg_data (
    time      TIMESTAMPTZ NOT NULL,
    user_id   VARCHAR(36) NOT NULL,
    ecg_value NUMERIC
);
SELECT create_hypertable('ecg_data', 'time', if_not_exists => TRUE);
CREATE INDEX idx_ecg_user_time ON ecg_data (user_id, time DESC);
```

---

### 19. `database/postgres/schema.sql` — Çelişen tabloları kaldır

Mevcut `patients`, `devices`, `patient_bmi` tabloları backend ORM ile çelişiyor. Dosya içeriğini sil, yerine açıklama bırak:

```sql
-- Tables are managed automatically by SQLAlchemy ORM (backend/models/).
-- Starting the backend server once will auto-create:
--   users, health_readings, notifications
--
-- Do NOT create tables manually here.
```

---

## BLE

### 20. `ble/data_manager.py` — Import path düzeltmeleri

| Satır | Mevcut | Olması Gereken |
|-------|--------|----------------|
| 26 | `from schemas.ble_frame import BLEFrame, FrameProcessResult` | `from ble.frame import BLEFrame, FrameProcessResult` |
| 185 | `from app.models.health_reading import HealthReading` | `from backend.models.health_reading import HealthReading` |

---

### 21. `ble/tests/test_data_manager.py` — Import path düzeltmeleri

| Satır | Mevcut | Olması Gereken |
|-------|--------|----------------|
| 20 | `from schemas.ble_frame import BLEFrame, FrameProcessResult` | `from ble.frame import BLEFrame, FrameProcessResult` |
| 21 | `from services.data_manager import DataManager` | `from ble.data_manager import DataManager` |

---

## MOBILE

> `mobile/android/` henüz boş. Uygulamanın backend ile konuşabilmesi için aşağıdaki entegrasyonlar gerekli.

### 22. Temel API entegrasyonu

| Endpoint | Method | Açıklama |
|----------|--------|----------|
| `/auth/login` | POST | JWT token al, cihazda sakla |
| `/auth/register` | POST | Kullanıcı kaydı |
| `/dashboard/summary` | GET | Ana ekran — son vitaller |
| `/readings` | GET | Ölçüm geçmişi (date/limit filtreli) |
| `/readings` | POST | Yeni ölçüm gönder |
| `/readings/predict-bp` | POST | PTT + HR ile kan basıncı tahmini |
| `/trends` | GET | Trend grafikleri (type/period filtreli) |
| `/reports/weekly` | GET | Haftalık rapor |
| `/notifications` | GET | Bildirim listesi |
| `/notifications/{id}/read` | PUT | Bildirimi okundu işaretle |
| `/profile` | GET / PUT | Profil görüntüle / güncelle |

---

### 23. BLE kontrolü (sunucu taraflı BLE — `ble/manager.py` çalışıyorsa)

| Endpoint | Method | Açıklama |
|----------|--------|----------|
| `/ble/status` | GET | Cihaz bağlı mı, istatistikler |
| `/ble/start` | POST | Firmware'e START komutu gönder |
| `/ble/stop` | POST | Firmware'e STOP komutu gönder |
| `/ble/stats` | GET | İşlenen/hatalı frame sayıları |

---

### 24. Direkt BLE (mobil uygulama BLE'yi kendisi yönetiyorsa)

Firmware BLE sabitleri:

```
Service UUID  : 19B10000-E8F2-537E-4F6C-D104768A1214
DataChar UUID : 19B10001-E8F2-537E-4F6C-D104768A1214  (Notify — JSON frame alır)
CmdChar UUID  : 19B10002-E8F2-537E-4F6C-D104768A1214  (Write  — START/STOP gönderir)
```

Akış:
1. Cihazı tara → `BPSync-Wrist` adıyla bağlan
2. DataChar'a subscribe ol (Notify)
3. CmdChar'a `START` yaz → 10 Hz JSON frame gelmeye başlar
4. Gelen JSON'u parse et → `POST /sensor/wristband` ile backend'e ilet
5. İşim bitince CmdChar'a `STOP` yaz

---

## Özet Tablo

| # | Dosya | Alan | Tür |
|---|-------|------|-----|
| 1 | `backend/main.py` | Backend | Import + BLE lifespan + CORS |
| 2 | `backend/database.py` | Backend | Import + duplicate sil |
| 3 | `backend/run.py` | Backend | Import + uvicorn path |
| 4 | `backend/routers/__init__.py` | Backend | Circular import sil |
| 5 | `backend/routers/auth.py` | Backend | Import path |
| 6 | `backend/routers/dashboard.py` | Backend | Import path |
| 7 | `backend/routers/readings.py` | Backend | Import path |
| 8 | `backend/routers/trends.py` | Backend | Import path |
| 9 | `backend/routers/reports.py` | Backend | Import path |
| 10 | `backend/routers/notifications.py` | Backend | Import path |
| 11 | `backend/routers/profile.py` | Backend | Import path |
| 12 | `backend/routers/sensor.py` | Backend | Import + JWT + SQL |
| 13 | `backend/routers/ble.py` | Backend | Import path |
| 14 | `backend/services/bp_model_service.py` | Backend | CNN runtime |
| 15 | `backend/schemas/sensor.py` | Backend | Schema yeniden yaz |
| 16 | `backend/config.py` | Backend | JWT secret güvenliği |
| 17 | `database/docker-compose.yml` | Database | Volume path + uvicorn |
| 18 | `database/timescale/schema.sql` | Database | Firmware uyumlu yeniden yaz |
| 19 | `database/postgres/schema.sql` | Database | Çelişen tabloları kaldır |
| 20 | `ble/data_manager.py` | BLE | Import path |
| 21 | `ble/tests/test_data_manager.py` | BLE | Import path |
| 22 | `mobile/android/` | Mobile | API entegrasyonu |
| 23 | `mobile/android/` | Mobile | BLE kontrolü (sunucu) |
| 24 | `mobile/android/` | Mobile | Direkt BLE (opsiyonel) |
