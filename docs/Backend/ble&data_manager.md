# BPSync — BLE Manager & Data Manager
 
## Nedir?
 
Bu sistem, BPSync bileklik donanımından (Seeed XIAO nRF52840) gelen sensör verilerini
kablosuz (BLE) olarak alıp veritabanına kaydeden iki Python servisinden oluşur:
 
| Servis | Dosya | Görevi |
|--------|-------|--------|
| **BLEManager** | `services/ble_manager.py` | Bileklikle BLE bağlantısı kurar, veri alır |
| **DataManager** | `services/data_manager.py` | Gelen veriyi parse edip DB'ye yazar |
 
---
 
## Nasıl Çalışır?
 
```
Bileklik (nRF52840)
  │  BLE Notify — JSON, 10 Hz (saniyede 10 paket)
  ▼
BLEManager
  │  Her paketi DataManager'a iletir
  ▼
DataManager
  │  Pydantic ile validate → HealthReading ORM nesnesi
  ▼
PostgreSQL  (health_readings tablosu)
```
 
### BLE Paket Formatı
 
Bileklik her 100ms'de şu JSON'u gönderir:
 
```json
{
  "ts":  12345,      "sq": 42,
  "pi":  98000,      "pr": 75000,
  "ax":  -120,       "ay": 340,    "az": 16000,
  "gx":  10,         "gy": -5,     "gz": 2,
  "tp":  36.7,
  "ep":  1,
  "qi_w": 1,  "qi_c": 1,  "qi": 1,
  "bt":  87
}
```
 
| Alan | Sensör | Açıklama |
|------|--------|----------|
| `ts` | — | Cihaz uptime (ms) |
| `sq` | — | Sıra numarası |
| `pi` / `pr` | MAX30102 | PPG — IR ve Red ham değer |
| `ax/ay/az` | MPU6050 | Wrist ivmeölçer |
| `gx/gy/gz` | MPU6050 | Wrist jiroskop |
| `tp` | MCP9808 | Deri sıcaklığı (°C) |
| `ep` | ECG (Chest) | R-peak bayrağı (0/1) |
| `qi_w/qi_c/qi` | — | Sinyal kalitesi (0/1) |
| `bt` | — | Pil yüzdesi |
 
---
 
## Kurulum
 
### 1. Bağımlılığı kur
```bash
pip install bleak>=0.21.0
```
 
### 2. `.env` dosyasına ekle
```env
BLE_DEVICE_NAME=BPSync-Wrist
BLE_SCAN_TIMEOUT=10
BLE_DEFAULT_USER_ID=<veritabanındaki kullanıcı UUID'si>
```
 
### 3. DB migration (yeni kolonlar için)
```bash
cd backend
alembic revision --autogenerate -m "add ble frame columns"
alembic upgrade head
```
 
### 4. Sunucuyu başlat
```bash
python run.py
```
 
Çıktı:
```
[BLE] Manager başlatıldı — 'BPSync-Wrist' aranıyor...
[OK] BPSync Backend started!
```
 
Bileklik yakına geldiğinde otomatik bağlanır ve veriler akmaya başlar.
 
---
 
## API Endpoint'leri
 
| Endpoint | Metod | Açıklama |
|----------|-------|---------|
| `/ble/status` | GET | Bağlantı durumu, cihaz adresi, istatistikler |
| `/ble/start` | POST | Cihaza `START` komutu gönder |
| `/ble/stop` | POST | Cihaza `STOP` komutu gönder |
| `/ble/stats` | GET | Kaç frame işlendi, kaçı başarısız |
 
### Örnek: durum sorgulama
```bash
curl http://localhost:8000/ble/status
```
```json
{
  "available": true,
  "connected": true,
  "device_name": "BPSync-Wrist",
  "device_address": "AA:BB:CC:DD:EE:FF",
  "connected_at": "2026-02-27T13:00:00+00:00",
  "data_stats": { "processed": 150, "failed": 0, "total": 150, "success_rate": 100.0 }
}
```
 
---
 
## Veritabanına Yazılan Alanlar
 
`health_readings` tablosuna her frame için bir satır eklenir:
 
| DB Kolonu | Kaynak | Açıklama |
|-----------|--------|---------|
| `timestamp` | backend alım zamanı | Unix ms |
| `temperature` | `tp` | Deri sıcaklığı |
| `ppg_data` | `[pi, pr]` | PPG IR ve Red listesi |
| `ecg_data` | `[ep]` | ECG R-peak bayrağı |
| `quality_score` | `qi * 100` | 0–100 |
| `raw_frame` | tüm JSON | Ham paket (debug için) |
| `seq_num` | `sq` | Sıra numarası |
| `battery` | `bt` | Pil yüzdesi |
 
> `heart_rate`, `systolic_bp`, `diastolic_bp`, `spo2` bu servis tarafından **hesaplanmaz**.  
> Bunlar `MLService` tarafından PPG datasından türetilir.
 
---
 
## Cihaz Olmadan Test
 
### Unit testler (sadece Python, BLE/DB gerekmez)
```bash
cd backend
python -m pytest tests/test_data_manager.py -v
# 11 passed ✓
```
 
---
 
## Hata Toleransı
 
| Durum | Davranış |
|-------|---------|
| `bleak` kurulu değil | Pasif mod, sunucu çalışmaya devam eder |
| Bileklik bulunamadı | 5 sn bekleyip yeniden tarar |
| BLE bağlantısı koptu | Otomatik yeniden bağlanır |
| Bozuk JSON paketi | Loglanır, streaming devam eder |
| DB hatası | Rollback, sonraki paket denenir |
 
---
 
## Kod Açıklaması
 
### `app/schemas/ble_frame.py` — BLE Frame Şeması
 
Bu dosyanın tek görevi: bileklikten gelen ham JSON metninin geçerli olup olmadığını
kontrol etmek. Pydantic kütüphanesi bunu bizim için yapar.
 
```python
class BLEFrame(BaseModel):
    ts: int = Field(..., description="Cihaz uptime (milisaniye)")
    sq: int = Field(..., description="Sıra numarası")
    pi: int = Field(..., ge=0, description="PPG IR ham değer")
    ...
```
 
`Field(...)` → `...` (üç nokta) bu alanın **zorunlu** olduğu anlamına gelir;
gelmezse `ValidationError` fırlatır.
 
`Field(..., ge=0)` → `ge=0` "greater or equal to 0" yani negatif değer gelirse
hata verir. Bu, sensörden anlamsız veri gelmesini önler.
 
```python
ep: int = Field(0, ge=0, le=1, description="ECG R-peak bayrağı (0/1)")
```
 
`Field(0, ...)` → Varsayılan değeri `0`'dır. Chest modülü bağlı değilse firmware
bu alanı göndermeyebilir; sıfır varsayarak sistemi koruruz.
 
```python
received_at_ms: Optional[int] = Field(None, ...)
```
 
Bu alan firmware'den **gelmez**. `DataManager` hangi anda paketi aldığını buraya
yazar. Neden cihazın kendi saati (`ts`) değil? Çünkü `ts` cihazın açıldığından
bu yana geçen ms'dir — mutlak zaman değildir.
 
```python
@classmethod
def parse_raw_json(cls, json_str: str) -> "BLEFrame":
    data = json.loads(json_str)   # önce JSON sözdizimi kontrolü
    return cls(**data)            # sonra Pydantic alan kontrolü
```
 
İki aşamalı doğrulama: önce ham metin gerçekten JSON mi (`json.loads`),
sonra alanlar doğru mu (`cls(**data)`).
 
```python
@property
def quality_percent(self) -> float:
    return float(self.qi * 100)
```
 
`qi` firmware tarafından 0 veya 1 gelir. Biz bunu veritabanında 0–100 skalasında
saklarız (MLService bu skalayla çalışır).
 
```python
@property
def chest_connected(self) -> bool:
    return self.qi_c == 1
```
 
`qi_c` chest modülünün sinyal kalitesi; 1 ise chest bağlı ve kaliteli sinyal
alıyoruz demektir.
 
---
 
### `app/services/data_manager.py` — Veri Yöneticisi
 
```python
class DataManager:
    def __init__(self, db_factory, default_user_id=""):
        self._db_factory = db_factory
        self._default_user_id = default_user_id
        self._frames_processed = 0
        self._frames_failed = 0
```
 
`db_factory` bir fonksiyon alır (örn. `SessionLocal`). Her `process_frame()`
çağrısında `db_factory()` ile yeni bir DB bağlantısı açılır, işlenir, kapatılır.
Böylece uzun süre açık kalan bağlantı problemi olmaz.
 
```python
async def process_frame(self, json_str, user_id=None):
```
 
`async` keyword'ü bu metodun bekleyebileceği anlamına gelir. BLEManager
`await dm.process_frame(...)` diyerek çağırır. Asyncio event loop'u diğer
görevleri (BLE notify dinleme vb.) bu bekleme sırasında çalıştırabilir.
 
```python
try:
    frame = BLEFrame.parse_raw_json(json_str)
    frame.received_at_ms = int(time.time() * 1000)
except Exception as exc:
    self._frames_failed += 1
    logger.warning(...)
    return FrameProcessResult(success=False, ...)
```
 
Parse hatası yakalanır ama program çökmez. `_frames_failed` sayacı artar ve
bir sonraki frame için dinlemeye devam eder.
 
```python
def _frame_to_reading(self, frame, user_id):
    reading = HealthReading(
        ppg_data=[frame.pi, frame.pr],  # IR ve Red tek listede
        ecg_data=[frame.ep],            # R-peak bayrağı liste olarak
        temperature=frame.tp,
        quality_score=frame.quality_percent,
    )
```
 
PPG verisi `[IR, Red]` şeklinde tek bir JSON listesine sıkıştırılır. Neden liste?
Çünkü ileride birden fazla sample'ı aynı satıra yazmak gerekebilir.
ECG için de aynı mantık: şimdilik tek bayrak, ileride tam dalga formu dizisi gelecek.
 
```python
_safe_set(reading, "raw_frame", frame.to_dict())
_safe_set(reading, "seq_num", frame.sq)
_safe_set(reading, "battery", frame.bt)
```
 
```python
def _safe_set(obj, attr, value):
    mapper = type(obj).__mapper__
    if attr in [c.key for c in mapper.columns]:
        setattr(obj, attr, value)
```
 
`_safe_set` SQLAlchemy'nin kendisine sorar: "Bu modelde bu kolon var mı?"
Varsa yazar, yoksa sessizce geçer. Bu sayede `health_reading.py`'ye yeni kolon
eklemeden önce de sistem çalışmaya devam eder.
 
---
 
### `app/services/ble_manager.py` — BLE Yöneticisi
 
```python
try:
    from bleak import BleakScanner, BleakClient
    BLEAK_AVAILABLE = True
except ImportError:
    BLEAK_AVAILABLE = False
```
 
`bleak` kurulu değilse Python hata vermez; `BLEAK_AVAILABLE = False` atanır.
`start()` çağrıldığında bunu kontrol eder ve sessizce çıkar. Böylece
BLE donanımı olmayan geliştirme ortamlarında sunucu yine de ayağa kalkar.
 
```python
async def start(self):
    self._running = True
    self._task = asyncio.create_task(self._connection_loop())
```
 
`asyncio.create_task()` → `_connection_loop()` fonksiyonunu **arka planda**
başlatır. FastAPI'nin kendi event loop'u içinde çalışır. Sunucu istek almaya
devam ederken BLE döngüsü de paralel çalışır.
 
```python
async def _connection_loop(self):
    while self._running:
        device = await self._scan_for_device()
        if device is None:
            await asyncio.sleep(RECONNECT_DELAY_S)  # 5 sn bekle
            continue
        await self._connect_and_stream(device)
```
 
Sonsuz döngü: cihaz bul → bağlan → koptu mu → tekrar bul.
`await asyncio.sleep(5)` beklerken CPU kullanmaz; event loop başka işleri yapar.
 
```python
async def _connect_and_stream(self, device):
    async with BleakClient(device) as client:
        await client.start_notify(DATA_CHAR_UUID, self._on_notification)
        while self._running and self._connected:
            await asyncio.sleep(1.0)
```
 
`async with BleakClient(...)` → bağlantıyı açar; `async with` bloğu bitince
otomatik kapatır. `start_notify(...)` bileklikten her yeni veri geldiğinde
`_on_notification()` callback'ini çağırmasını söyler.
 
```python
async def _on_notification(self, sender, data: bytearray):
    json_str = data.decode("utf-8")
    await self._dm.process_frame(json_str, user_id=self._user_id)
```
 
BLE verisi ham byte dizisi olarak gelir (`bytearray`). Firmware UTF-8 JSON
gönderdiği için `.decode("utf-8")` ile stringe çevrilir ve DataManager'a iletilir.
 
```python
def _on_disconnect(self, client):
    self._connected = False
```
 
Bu callback `async` **değil** — bleak bu callback'i non-async olarak çağırır.
Sadece `_connected = False` atayarak döngünün yeniden bağlanmasını tetikler.
 
---
 
### `app/routers/ble.py` — REST API Router
 
```python
def require_ble() -> BLEManager:
    mgr = get_ble_manager()
    if mgr is None:
        raise HTTPException(status_code=503, detail="BLEManager başlatılmamış.")
    return mgr
```
 
FastAPI'nin dependency injection sistemi (`Depends`) ile çalışır.
Her endpoint bu fonksiyonu çağırır; BLEManager yoksa otomatik 503 döner.
Endpoint kodunda elle kontrol yazmaya gerek kalmaz.
 
```python
@router.get("/status", response_model=BLEStatusResponse)
async def get_ble_status(ble: BLEManager = Depends(require_ble)):
    return ble.get_status()
```
 
`Depends(require_ble)` → FastAPI, endpoint çağrılmadan önce `require_ble()`
çalıştırır ve sonucunu `ble` parametresine atar.
 
---
 
### `backend/app/main.py` — Değişiklikler
 
```python
@asynccontextmanager
async def lifespan(app: FastAPI):
    create_tables()
 
    if not settings.use_mock_data:
        if settings.ble_default_user_id:
            dm = DataManager(db_factory=SessionLocal, ...)
            ble = init_ble_manager(dm, ...)
            await ble.start()
    yield                    # ← sunucu burada çalışır
 
    mgr = get_ble_manager()
    if mgr:
        await mgr.stop()
```
 
`lifespan` bir context manager'dır. `yield`'dan öncesi startup, sonrası shutdown.
`use_mock_data=True` iken BLE başlatılmaz; geliştirme ortamında BLE donanımı
olmadan çalışmaya devam edilir.
`ble_default_user_id` boşsa BLE başlatılmaz ve kullanıcı `.env` dosyasına
eklenmesi gerektiği konusunda uyarı verilir.
 
---
 
### `tests/test_data_manager.py` — Unit Testler
 
```python
engine = create_engine("sqlite:///:memory:", ...)
Base.metadata.create_all(engine)
```
 
`sqlite:///:memory:` → disk yerine RAM'de bir SQLite DB açar. Test bittikten
sonra silinir. Böylece gerçek PostgreSQL'e gerek kalmaz.
 
```python
def patched_frame_to_reading(frame, user_id):
    reading = HealthReadingTest(...)
    return reading
 
dm._frame_to_reading = patched_frame_to_reading
```
 
Monkey-patching: `DataManager`'ın asıl `_frame_to_reading()` metodunu
test-friendly versiyonuyla değiştiririz. Gerçek `HealthReading` modelini
import etmeden test SQLite modeliyle çalışırız.
 
```python
result = asyncio.get_event_loop().run_until_complete(
    dm.process_frame(VALID_FRAME)
)
```
 
`process_frame` `async` olduğu için normal test fonksiyonunda
doğrudan `await` kullanamayız. `run_until_complete()` event loop'u kendimiz
çalıştırarak async kodu senkron test içinde çalıştırırız.