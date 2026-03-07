# BPSync — PlatformIO Kurulum Rehberi

> **Hedef:** VS Code + PlatformIO kullanarak Seeed XIAO nRF52840'a
> Arduino IDE'ye gerek kalmadan kod derlemek ve yüklemek.
>
> Bu rehber adım adım takip edilebilir. Claude veya başka bir yapay zekaya
> gerek yok — tüm komutlar burada.

---

## ÖNEMLİ NOTLAR (Önce Oku)

> **Board ID sorunu:** PlatformIO'nun `nordicnrf52` platformu `seeed_xiao_nrf52840`
> board ID'sini tanımıyor. Workaround olarak `adafruit_feather_nrf52840` kullanıyoruz —
> aynı nRF52840 chip'i, sadece Adafruit'in core'u. **Bu repo'da zaten doğru şekilde
> ayarlı, bir şey yapmanı gerektirmiyor.**
>
> **ArduinoBLE çalışmıyor:** Adafruit core ile ArduinoBLE uyumsuz. Firmware Adafruit'in
> built-in Bluefruit kütüphanesini kullanıyor — `lib_deps`'e eklemeye gerek yok, otomatik gelir.

---

## Gereksinimler

| Araç | İndirme Adresi | Not |
|------|----------------|-----|
| VS Code | https://code.visualstudio.com | Zorunlu |
| Git | https://git-scm.com | Zorunlu |
| Python 3.x | https://python.org/downloads | PlatformIO için gerekli |
| USB Type-C kablo | — | XIAO için |

---

## Adım 1 — Python Kurulumu

PlatformIO Python gerektiriyor. Kurulu olup olmadığını kontrol et:

```powershell
python --version
```

Çıktı `Python 3.x.x` gösteriyorsa geç. Göstermiyorsa:

1. https://python.org/downloads adresine git
2. En güncel Python 3.x sürümünü indir
3. Kurulum sırasında **"Add Python to PATH"** kutusunu işaretle ✓
4. Kur, bilgisayarı yeniden başlat

---

## Adım 2 — VS Code Kurulumu

1. https://code.visualstudio.com adresinden VS Code'u indir
2. Kur (varsayılan seçenekler yeterli)
3. VS Code'u aç

---

## Adım 3 — PlatformIO Extension Kurulumu

1. VS Code'da sol menüden **Extensions** simgesine tıkla (`Ctrl+Shift+X`)
2. Arama kutusuna `PlatformIO IDE` yaz
3. **PlatformIO IDE** (yayıncı: *PlatformIO*) → **Install**
4. Kurulum bitince VS Code'u **tamamen kapat ve yeniden aç**

> Kurulum 3-5 dakika sürebilir. Sol alt köşede PlatformIO simgesi (ev ikonu) görününce hazır.

---

## Adım 4 — Repoyu Klonla ve Aç

```powershell
# İstediğin bir klasörde terminal aç (örn: Masaüstü)
git clone https://gitlab.com/bpsync/bpsync.git

# Veya zaten klonladıysan sadece güncelle
git pull
```

VS Code'da:
- **File → Open Folder** → `bpsync/` klasörünü seç → **Open**

VS Code bir uyarı gösterebilir: *"Do you trust the authors?"* → **Yes, I trust**

---

## Adım 5 — PlatformIO Terminalini Aç

**ÖNEMLİ:** `pio` komutu normal PowerShell'de çalışmaz.
PlatformIO'nun kendi terminalini kullanman gerekiyor.

**Yöntem 1 — VS Code Terminal (önerilen):**
- VS Code'da üst menü → **Terminal → New Terminal**
- Terminalde şunu çalıştır:

```powershell
pio --version
```

Çıktı `PlatformIO Core, version X.X.X` gösteriyorsa hazır.

**Yöntem 2 — Tam yol ile PowerShell:**

```powershell
& "$env:USERPROFILE\.platformio\penv\Scripts\pio.exe" --version
```

---

## Adım 6 — Platform Güncelle

İlk kurulumda platform güncellemesi önerilir:

```powershell
pio platform update nordicnrf52
```

Çıktıda `already up-to-date` veya `updated` yazması beklenir.

---

## Adım 7 — Derleme (Build)

Board bağlı olmasa da derleme yapılabilir:

```powershell
pio run -e wrist_module
```

**İlk çalıştırmada** kütüphaneler otomatik indirilir (~2-5 dakika, internet bağlantısı gerekli):
```
Downloading  sparkfun/SparkFun MAX3010x ...
Downloading  electroniccats/MPU6050 ...
Downloading  adafruit/Adafruit MCP9808 Library ...
Downloading  adafruit/Adafruit BusIO ...
```

Başarılı sonuç şöyle görünür:
```
RAM:   [=         ]   6.4% (used 15892 bytes from 248832 bytes)
Flash: [==        ]  16.8% (used 136720 bytes from 815104 bytes)
================================= [SUCCESS] Took 8.00 seconds =================================
```

**Hata alırsan** → Adım 9'daki Sorun Giderme bölümüne bak.

---

## Adım 8 — XIAO'ya Yükleme (Upload)

1. XIAO'yu Type-C kablo ile bilgisayara bağla
2. Terminalde bağlı portları kontrol et:

```powershell
pio device list
```

Çıktıda `COM3`, `COM4` gibi bir port görünmeli (Windows).

3. **Bootloader moduna geç:**
   - XIAO üzerindeki küçük **RST** (Reset) butonuna **iki kez hızlıca** bas
   - Sarı/turuncu LED yanıp sönmeye başlarsa bootloader moduna girdi demektir
   - Girmezse: kablo bağlıyken **bir kez** bas, 1 saniye bekle, **bir kez daha** bas

4. Yükle:

```powershell
pio run -e wrist_module -t upload
```

Başarılı sonuç:
```
Flashing firmware.zip
Upgrade completed successfully in X.XXs
================================= [SUCCESS] Took XX.XX seconds =================================
```

> **Port bulunamazsa:**
> ```powershell
> # Portu manuel belirt (COM numarasını pio device list'ten gör)
> pio run -e wrist_module -t upload --upload-port COM4
> ```

---

## Adım 9 — Serial Monitor (Test)

XIAO bağlıyken:

```powershell
pio device monitor
```

Çıktıda şunu görmen gerekir:
```
==============================================
  BPSync Wrist Module
  Seeed XIAO nRF52840
==============================================
[OK]    MAX30102  (PPG)
[OK]    MPU6050   (Wrist IMU)
[OK]    MCP9808   (Temperature)
[OK]    BLE       (Bluefruit, advertising)
[READY] Advertising as 'BPSync-Wrist'...
==============================================
```

Serial Monitor'dan çıkmak: `Ctrl+C`

> `[ERROR]` görürsen sensör bağlantısını kontrol et:
> MAX30102 / MPU6050 / MCP9808 → SDA=D4, SCL=D5, VCC=3.3V

---

## Adım 10 — BLE Test (nRF Connect)

1. Telefona **nRF Connect for Mobile** yükle (Nordic Semiconductor — ücretsiz)
   - Android: Google Play → "nRF Connect for Mobile"
   - iOS: App Store → "nRF Connect for Mobile"
2. Uygulamayı aç → **SCAN**
3. Listede `BPSync-Wrist` cihazını bul → **CONNECT**
4. **Unknown Service** altında:
   - `19B10001...` karakteristiği → sağdaki **aşağı ok (Subscribe/Notify)** butonuna bas
   - JSON verisi akmaya başlar:
     ```json
     {"ts":1234,"sq":1,"pi":85000,"pr":60000,"ax":320,...,"qi":1,"bt":100}
     ```
5. Komut göndermek için `19B10002...` karakteristiği → **yukarı ok (Write)**:
   - `STOP` yaz → veri durur
   - `START` yaz → veri tekrar akar

---

## Sorun Giderme

### `pio` komutu tanınmıyor

Normal PowerShell değil, VS Code'un kendi terminali kullan:
`Terminal → New Terminal`

Veya tam yolu kullan:
```powershell
& "$env:USERPROFILE\.platformio\penv\Scripts\pio.exe" run -e wrist_module
```

### `Unknown board ID: seeed_xiao_nrf52840`

Bu sorun zaten çözüldü — `platformio.ini`'de `adafruit_feather_nrf52840` kullanılıyor.
Eğer biri yanlışlıkla `seeed_xiao_nrf52840` yazarsa `adafruit_feather_nrf52840` ile değiştir.

### `#error "Unsupported board selected!"` (ArduinoBLE hatası)

Firmware ArduinoBLE kullanmıyor, Adafruit Bluefruit kullanıyor.
`platformio.ini`'de `arduino-libraries/ArduinoBLE` varsa sil.

### Upload başarısız / timeout

```powershell
# 1. Portu kontrol et
pio device list

# 2. XIAO'yu bootloader moduna al (RST'e iki kez bas)

# 3. Manuel port ile dene
pio run -e wrist_module -t upload --upload-port COM3
```

### Kütüphane indirilemiyor

```powershell
# İnternet bağlantısını kontrol et, sonra:
pio lib install
```

### Build cache bozuldu

```powershell
pio run -e wrist_module -t clean
pio run -e wrist_module
```

---

## Özet — Günlük Kullanım Komutları

```powershell
# Sadece derle (board bağlı olmak zorunda değil)
pio run -e wrist_module

# Derle + Yükle (board bağlı olmalı)
pio run -e wrist_module -t upload

# Serial Monitor (board bağlı olmalı)
pio device monitor

# Bağlı USB cihazlarını listele
pio device list

# Build klasörünü temizle
pio run -e wrist_module -t clean

# Platform güncelle
pio platform update nordicnrf52
```

---

## Proje Dosya Yapısı

```
bpsync/
├── platformio.ini                ← PlatformIO config (board, lib_deps, src_dir)
├── firmware/
│   └── wrist_module/
│       └── wrist_module.ino      ← Wrist modül firmware
└── docs/
    └── guides/
        └── platformio-setup.md   ← Bu dosya
```

### `platformio.ini` özeti

```ini
[platformio]
src_dir = firmware/wrist_module      ← kaynak klasörü

[env:wrist_module]
platform  = nordicnrf52
board     = adafruit_feather_nrf52840  ← seeed_xiao_nrf52840 çalışmıyor, workaround
framework = arduino
lib_deps =
    sparkfun/SparkFun MAX3010x ...
    electroniccats/MPU6050
    adafruit/Adafruit MCP9808 Library
    adafruit/Adafruit BusIO
    # ArduinoBLE YOK — Bluefruit built-in geliyor
```
