# BPSync Dual-Device BLE Contract

Bu dokuman, mevcut `wrist-as-hub` denemesinden sonra hedeflenen yeni mimariyi
tanimlar:

- `BPSync-Wrist` -> sadece BLE Peripheral
- `BPSync-Chest` -> sadece BLE Peripheral
- `Android app` -> iki cihaza ayni anda BLE Central olarak baglanir
- `Backend` -> Android'in gonderdigi eslestirilmis pencereleri alir

Bu mimari, `ArduinoBLE` ile ayni cihazda stabil `Peripheral + Central` rolunu
surdurmenin zor olmasi nedeniyle secilmistir.

## 1. Roller

| Bilesen | BLE rolu | Gorev |
|---|---|---|
| Wrist module | Peripheral | PPG, IMU, sicaklik verisi yayinlar |
| Chest module | Peripheral | ECG verisi yayinlar |
| Android | Central | Iki cihaza baglanir, `START/STOP` gonderir, frame'leri buffer'lar |
| Backend | HTTP API | Android'den gelen pencereyi kaydeder ve inferans yapar |

## 2. Wrist BLE Contract

| Alan | Deger |
|---|---|
| Device name | `BPSync-Wrist` |
| Service UUID | `19B10000-E8F2-537E-4F6C-D104768A1214` |
| Data characteristic | `19B10001-E8F2-537E-4F6C-D104768A1214` |
| Command characteristic | `19B10002-E8F2-537E-4F6C-D104768A1214` |
| Data property | `Notify` |
| Command property | `Write` / `WriteWithoutResponse` |

### Wrist frame

Wrist her frame'de tek bir JSON payload gonderir:

```json
{
  "device_type": "wrist",
  "seq": 1042,
  "timestamp": 1775646328088,
  "ppg_ir": 15320,
  "ppg_red": 12680,
  "ax": 120,
  "ay": -42,
  "az": 16310,
  "gx": 4,
  "gy": 2,
  "gz": -1,
  "temperature": 36.8,
  "qi_w": 1
}
```

### Wrist commands

| Komut | Anlam |
|---|---|
| `START` | olcumu baslat, `seq` sifirla |
| `STOP` | olcumu durdur |

## 3. Chest BLE Contract

| Alan | Deger |
|---|---|
| Device name | `BPSync-Chest` |
| Service UUID | `29B10000-E8F2-537E-4F6C-D104768A1214` |
| Data characteristic | `29B10001-E8F2-537E-4F6C-D104768A1214` |
| Command characteristic | `29B10002-E8F2-537E-4F6C-D104768A1214` |
| Data property | `Notify` |
| Command property | `Write` / `WriteWithoutResponse` |

### Chest frame

Chest de JSON ile ilerlesin; Android tarafinda parse mantigi tek kalsin:

```json
{
  "device_type": "chest",
  "seq": 1042,
  "timestamp": 1775646328088,
  "ecg": 512,
  "r_peak": 1,
  "qi_c": 1
}
```

Not:

- `seq` her iki cihazda da ayni mantikla artmali
- `START` geldiginde `seq=0`'dan yeniden baslamali
- `timestamp` cihazdaki `millis()` yerine mumkunse telefonun set ettigi oturum
  mantigiyla eslestirilecek sekilde yorumlanmali; ama ilk iterasyonda `seq`
  ana eslestirme anahtari olacak

### Chest commands

| Komut | Anlam |
|---|---|
| `START` | streaming baslat, `seq` sifirla |
| `STOP` | streaming durdur |

## 4. Android Tarafi

Android iki ayri GATT baglantisi tutar:

1. `BPSync-Wrist` -> connect -> `19B10001` notify -> `19B10002` write
2. `BPSync-Chest` -> connect -> `29B10001` notify -> `29B10002` write

### Start button davranisi

- Iki cihaz da bagliysa `Start Measurement` aktif olur
- Butona basinca Android iki cihaza da `START` yazar
- Butona tekrar basinca Android iki cihaza da `STOP` yazar

### Android buffer mantigi

```kotlin
wristBuffer[frame.seq] = frame
chestBuffer[frame.seq] = frame

if (wristBuffer.size >= 25 && chestBuffer.size >= 25) {
    val window = matchBySeq(wristBuffer, chestBuffer)
    sendToBackend(window)
    wristBuffer.clear()
    chestBuffer.clear()
}
```

## 5. Backend Payload

En temiz tasarim tek endpoint ile eslesmis pencere gondermek:

`POST /ble/mobile-window`

```json
{
  "user_id": "k@gmail.com",
  "window_size": 25,
  "start_seq": 1042,
  "end_seq": 1066,
  "wrist_frames": [
    {
      "seq": 1042,
      "timestamp": 1775646328088,
      "ppg_ir": 15320,
      "ppg_red": 12680,
      "ax": 120,
      "ay": -42,
      "az": 16310,
      "gx": 4,
      "gy": 2,
      "gz": -1,
      "temperature": 36.8,
      "qi_w": 1
    }
  ],
  "chest_frames": [
    {
      "seq": 1042,
      "timestamp": 1775646328088,
      "ecg": 512,
      "r_peak": 1,
      "qi_c": 1
    }
  ]
}
```

Bu yapi, backend'in iki ayri endpointten frame eslestirme yukunu azaltir.

## 6. UI Beklentisi

| Ekran | Icerik |
|---|---|
| Ana ekran | `Wrist bagli`, `Chest bagli`, `Start Measurement` |
| Olcum ekrani | baglanti durumu, anlik pulse/ECG/PPG gostergesi |
| Sonuc ekrani | `SBP / DBP / HR` |

## 7. Oncelik Sirasi

1. Chest firmware:
   `29B10002` command characteristic ekle
2. Wrist firmware:
   sadece peripheral akisini koru
3. Android:
   iki ayri BLE baglantisi + `START/STOP` + 25 frame buffer
4. Backend:
   `mobile-window` payload'ini kabul et

## 8. Mevcut Repo Durumu

- Mevcut `firmware/wrist_module/wrist_module.ino`, wrist'i hem phone'a
  peripheral hem de chest'e central yapmaya calisiyor
- `firmware/Chest_module/` henuz bos
- Bu dokuman, bir sonraki iterasyon icin hedef davranisi tanimlar; mevcut
  firmware'in zaten bunu uyguladigi anlamina gelmez
