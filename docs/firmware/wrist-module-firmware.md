# BPSync — Wrist Module Firmware Reference

> **File:** `firmware/wrist_module/wrist_module.ino`
> **Board:** Seeed XIAO nRF52840 (PlatformIO: `adafruit_feather_nrf52840`)
> **BLE Stack:** Adafruit Bluefruit (built into Adafruit nRF52 core)

---

## Table of Contents

1. [System Architecture](#1-system-architecture)
2. [Hardware & Sensors](#2-hardware--sensors)
3. [BLE Architecture](#3-ble-architecture)
4. [JSON Data Frame](#4-json-data-frame)
5. [Signal Quality Index (SQI)](#5-signal-quality-index-sqi)
6. [Sampling Rates & Timing](#6-sampling-rates--timing)
7. [Commands](#7-commands)
8. [Code Structure — Function Reference](#8-code-structure--function-reference)
9. [Chest Module Interface Contract](#9-chest-module-interface-contract)

---

## 1. System Architecture

```
┌─────────────────┐        ┌──────────────────────┐        ┌───────────┐
│  Chest Module   │  BLE   │   Wrist Module        │  BLE   │  Phone    │
│  (XIAO nRF52840)│──────► │   (XIAO nRF52840)     │──────► │  Android  │
│                 │        │                       │        │           │
│  ECG sensor     │        │  MAX30102  (PPG)      │        │  BPSync   │
│  Chest IMU      │        │  MPU6050   (IMU)      │        │  App      │
│  R-peak detect  │        │  MCP9808   (Temp)     │        │           │
│  Chest SQI      │        │  Merge + JSON build   │        │           │
└─────────────────┘        └──────────────────────┘        └───────────┘
   BLE Peripheral              BLE Central (chest)
                               BLE Peripheral (phone)
```

The wrist module acts as a **hub**:
- It connects **to** the chest module as a BLE Central (client).
- It connects **from** the phone as a BLE Peripheral (server).
- It reads its own sensors, collects chest data over BLE, merges everything into a single JSON frame, and sends it to the phone every 100 ms.

> **Current state (Iteration 1):** Chest module not yet implemented.
> Fields `ep`, `cx`, `cy`, `cz`, `qi_c` are `0` (placeholders) until the chest module is built.
> The firmware already contains all scaffolding for the chest BLE connection — it will auto-connect once a `BPSync-Chest` device is in range.

---

## 2. Hardware & Sensors

All three sensors share the **same I2C bus**: `SDA = D4`, `SCL = D5`.

| Sensor | Chip | I2C Address | Measures |
|--------|------|-------------|----------|
| PPG | MAX30102 | 0x57 | Infrared + Red light absorption → pulse waveform |
| Wrist IMU | MPU6050 | 0x68 | Acceleration (3-axis) + Gyroscope (3-axis) |
| Temperature | MCP9808 | 0x18 | Skin/ambient temperature (°C) |

**MAX30102 configuration:**
```
ppgSensor.setup(
    ledBrightness = 0x1F,   // ~6 mA LED current
    sampleAverage = 4,       // 4 samples averaged per reading
    ledMode       = 2,       // Red + IR mode
    sampleRate    = 100,     // 100 samples/sec
    pulseWidth    = 411,     // 411 µs pulse width
    adcRange      = 4096     // 18-bit ADC full-scale
)
```

**MPU6050 scales:**
- Accelerometer: ±2 g → 16384 LSB per g
- Gyroscope: ±250 °/s → 131 LSB per °/s

**MCP9808 resolution:** Mode 3 → 0.0625 °C

> **Wrist vs. Finger PPG:**
> MAX30102 uses Red + IR LEDs, optimized for fingertip SpO2. On the wrist, signals are weaker (typical IR: 5,000–30,000 vs. 50,000–150,000 on finger). This is acceptable for PPG morphology-based BP trend estimation. Place the sensor firmly against the inner wrist (over the radial/ulnar artery) for best results.

---

## 3. BLE Architecture

The nRF52840 chip supports **concurrent Peripheral + Central** roles. The firmware uses both simultaneously.

```cpp
Bluefruit.begin(1, 1);  // peripheralCount=1 (phone), centralCount=1 (chest)
```

### 3.1 BLE Peripheral — Phone Connection

The wrist module **advertises** as `BPSync-Wrist`. The phone scans and connects to it.

| Item | Value |
|------|-------|
| Device name | `BPSync-Wrist` |
| Service UUID | `19B10000-E8F2-537E-4F6C-D104768A1214` |
| Data Characteristic | `19B10001-…` — `Notify`, max 256 bytes |
| Command Characteristic | `19B10002-…` — `Write`, max 20 bytes |
| TX Power | +4 dBm (max for nRF52840) |
| Advertising interval | Fast 20 ms (first 30 s) → Slow 152 ms |

**Connection lifecycle:**
```
Phone connects  → periph_connect_callback()  → streaming = true, seqNum = 0
Phone disconnects → periph_disconnect_callback() → streaming = false
                    Bluefruit re-advertises automatically
```

### 3.2 BLE Central — Chest Module Connection

The wrist module **scans** for a device named `BPSync-Chest`. When found, it connects automatically.

| Item | Value |
|------|-------|
| Target device name | `BPSync-Chest` |
| Chest Service UUID | `29B10000-E8F2-537E-4F6C-D104768A1214` |
| Chest Data Characteristic | `29B10001-…` — `Notify`, 8 bytes (`ChestPacket`) |
| Scan interval | 100 ms, 50 ms window |

**How the wrist finds the chest — `adv_has_name()`:**

BLE advertisement packets are structured as linked AD (Advertising Data) entries:
```
[length][type][data][length][type][data]...
```
Device name types: `0x08` (shortened) or `0x09` (complete local name).
The `adv_has_name()` function walks these entries byte-by-byte looking for `"BPSync-Chest"`. This approach does not depend on UUID filtering, making it robust regardless of how the chest firmware structures its advertisement.

```
scan_callback()
  └── adv_has_name() → true?
        ├── YES → Bluefruit.Central.connect()
        └── NO  → Bluefruit.Scanner.resume()  (keep scanning)
```

**Discovery after connect — `central_connect_callback()`:**
```
1. chestSvc.discover(conn_handle)     → find the chest service
2. chestDataChar.discover()           → find the data characteristic inside it
3. chestDataChar.enableNotify()       → subscribe to notifications
4. chestConnected = true
```
If any step fails, the connection is dropped and the scanner restarts automatically.

**Connection lifecycle:**
```
Chest in range  → scan_callback → connect → central_connect_callback
                → chestConnected = true
Chest out of range → central_disconnect_callback
                  → ep=cx=cy=cz=qi_c=0
                  → Scanner restarts (restartOnDisconnect=true)
```

---

## 4. JSON Data Frame

Sent via BLE Notify on the Data Characteristic every **100 ms** (10 Hz).
Built by `buildJSON()` using `snprintf`, max ~175 bytes.

```json
{
  "ts":   12345,
  "sq":   1,
  "pi":   15000,
  "pr":   12000,
  "ax":   100,   "ay":  200,  "az": 9800,
  "gx":   10,    "gy":  5,    "gz": 3,
  "tp":   36.5,
  "ep":   1,
  "cx":   50,    "cy":  20,   "cz": 9900,
  "qi_w": 1,
  "qi_c": 1,
  "qi":   1,
  "bt":   100
}
```

| Field | Type | Source | Description |
|-------|------|--------|-------------|
| `ts` | uint32 | Wrist | `millis()` timestamp in ms |
| `sq` | uint16 | Wrist | Sequence number, resets on connect or START |
| `pi` | uint32 | MAX30102 | PPG Infrared raw value |
| `pr` | uint32 | MAX30102 | PPG Red raw value |
| `ax` `ay` `az` | int16 | MPU6050 | Wrist acceleration (raw, 16384 = 1 g) |
| `gx` `gy` `gz` | int16 | MPU6050 | Wrist gyroscope (raw, 131 = 1 °/s) |
| `tp` | float | MCP9808 | Temperature in °C (1 decimal place) |
| `ep` | uint8 | Chest | ECG R-peak detected in this window (0 or 1) |
| `cx` `cy` `cz` | int16 | Chest | Chest acceleration raw (same scale as wrist) |
| `qi_w` | uint8 | Wrist | Wrist Signal Quality Index (0 or 1) |
| `qi_c` | uint8 | Chest | Chest Signal Quality Index (0 or 1) |
| `qi` | uint8 | Combined | `qi_w AND qi_c`; if chest disconnected: `qi = qi_w` |
| `bt` | uint8 | Wrist | Battery % — fixed at 100 (USB powered) |

> **Chest placeholders:** Until the chest module is connected, `ep=0`, `cx=cy=cz=0`, `qi_c=0`.
> The ML model should use only frames where `qi = 1`.

---

## 5. Signal Quality Index (SQI)

SQI gates unreliable frames before they reach the ML model. There are three SQI values.

### 5.1 Wrist SQI (`qi_w`)

Calculated in `calculateSQI()` every loop cycle.

**Step 1 — Motion check:**
```
magnitude = sqrt(ax² + ay² + az²)
deviation = |magnitude - 16384|     ← 16384 = 1g at ±2g scale

motionOK = deviation < 4096         ← threshold ≈ 0.25g
```
When the wrist is still and flat, the accelerometer reads approximately 1g (gravity). A deviation larger than 0.25g indicates motion artifact.

**Step 2 — PPG signal check:**
```
ppgOK = ppg_ir > 1000
```
IR < 1000 means the sensor is not in contact with skin.

**Result:**
```
qi_w = (motionOK AND ppgOK) ? 1 : 0
```

| Scenario | qi_w |
|----------|------|
| Wrist still + good contact | 1 |
| Wrist moving | 0 |
| Sensor lifted off skin | 0 |
| Both bad | 0 |

### 5.2 Chest SQI (`qi_c`)

Calculated **inside the chest module firmware** by the same principle:
- ECG electrode contact check
- Chest motion check (chest IMU)

Transmitted as part of `ChestPacket.qi_c` and stored directly — the wrist module does not recalculate it.

### 5.3 Combined SQI (`qi`)

```
if (chestConnected):
    qi = qi_w AND qi_c    ← both signals must be clean
else:
    qi = qi_w             ← wrist-only mode
```

**Why three separate values?**

A single combined `qi` would only tell you "something is wrong." With separate values, the backend can distinguish:

| qi_w | qi_c | qi | Meaning |
|------|------|----|---------|
| 1 | 1 | 1 | Clean frame — use for BP estimation |
| 0 | 1 | 0 | Wrist moved — PPG artifact |
| 1 | 0 | 0 | Chest electrode lost / chest movement |
| 0 | 0 | 0 | Both degraded |

The ML model filters on `qi = 1`. The separate fields are available for logging, clinical review, and future per-channel confidence weighting.

---

## 6. Sampling Rates & Timing

All timing is done with non-blocking `millis()` comparisons — no `delay()` calls.

| Source | Rate | Interval | Timer var |
|--------|------|----------|-----------|
| PPG (MAX30102) | 100 Hz | 10 ms | `tPPG` |
| IMU (MPU6050) | 50 Hz | 20 ms | `tIMU` |
| Temperature (MCP9808) | 1 Hz | 1000 ms | `tTemp` |
| BLE TX to phone | 10 Hz | 100 ms | `tBLE` |
| SQI calculation | Every loop | — | — |

BLE TX fires only when `streaming = true` AND a phone is connected.

---

## 7. Commands

The phone writes ASCII strings to the **Command Characteristic** (`19B10002-…`).

| Command | Effect |
|---------|--------|
| `START` | `streaming = true`, `seqNum` resets to 0 |
| `STOP` | `streaming = false` — data transmission pauses |

Commands are handled in `onCmdWrite()`. Connection events also auto-set `streaming = true` on connect and `streaming = false` on disconnect.

---

## 8. Code Structure — Function Reference

```
wrist_module.ino
│
├── setup()
│   ├── Serial.begin(115200)
│   ├── Wire.begin()               I2C bus on SDA=D4, SCL=D5
│   ├── initSensors()
│   └── initBLE()
│
├── loop()                         Non-blocking, millis()-based
│   ├── readPPG()                  @ 100 Hz
│   ├── readIMU()                  @  50 Hz
│   ├── readTemperature()          @   1 Hz
│   ├── calculateSQI()             every cycle
│   └── sendToPhone()              @  10 Hz (if streaming)
│
├── initSensors()
│   ├── MAX30102: ppgSensor.begin() + .setup()
│   ├── MPU6050:  imu.initialize() + testConnection() + range config
│   └── MCP9808:  tempSensor.begin(0x18) + resolution + wake
│
├── initBLE()
│   ├── Bluefruit.begin(1, 1)      peripheral + central
│   ├── [Peripheral] wristSvc, dataChar (Notify 256B), cmdChar (Write 20B)
│   ├── Bluefruit.Advertising.start(0)
│   ├── [Central] chestSvc, chestDataChar + setNotifyCallback
│   └── Bluefruit.Scanner.start(0)
│
├── readPPG()        ppgSensor.getIR() / getRed() / nextSample()
├── readIMU()        imu.getMotion6(ax, ay, az, gx, gy, gz)
├── readTemperature() tempSensor.readTempC()
│
├── calculateSQI()   motion magnitude + PPG threshold → qi_w
│
├── buildJSON()      snprintf compact JSON into 256-byte buffer
├── sendToPhone()    buildJSON → dataChar.notify() — every 50th frame prints to Serial
│
├── [Peripheral callbacks]
│   ├── periph_connect_callback()    streaming=true, seqNum=0
│   ├── periph_disconnect_callback() streaming=false
│   └── onCmdWrite()                 START / STOP
│
└── [Central callbacks]
    ├── adv_has_name()               parse raw AD bytes for device name
    ├── scan_callback()              name check → connect or resume
    ├── central_connect_callback()   discover service → char → enableNotify
    ├── central_disconnect_callback() reset chest fields, scanner restarts
    └── chest_data_callback()        cast uint8_t* to ChestPacket*, update globals
```

---

## 9. Chest Module Interface Contract

When the chest module firmware is implemented, it **must** conform to the following interface so the wrist module connects without any changes.

### BLE Advertisement

- Advertise device name: `BPSync-Chest`
- Advertise Service UUID: `29B10000-E8F2-537E-4F6C-D104768A1214`

### BLE Service & Characteristic

| Item | Value |
|------|-------|
| Service UUID | `29B10000-E8F2-537E-4F6C-D104768A1214` |
| Data Characteristic UUID | `29B10001-E8F2-537E-4F6C-D104768A1214` |
| Properties | `Notify` |
| Payload size | 8 bytes — `ChestPacket` struct |

### ChestPacket Struct

The chest firmware must send this exact packed binary layout:

```cpp
struct __attribute__((packed)) ChestPacket {
    uint8_t  ep;            // ECG R-peak detected in this window (0 or 1)
    int16_t  cx, cy, cz;   // Chest accelerometer raw (±2g, 16384 = 1g)
    uint8_t  qi_c;          // Chest SQI — 1 if signal clean, 0 if noisy
};
// Total: 8 bytes
```

The `__attribute__((packed))` is critical — it prevents the compiler from inserting padding bytes. Both the chest and wrist firmware must use this attribute for the struct to decode correctly.

### Chest SQI Calculation (recommended)

```
chest_mag = sqrt(cx² + cy² + cz²)
deviation = |chest_mag - 16384|

ecgOK    = (ECG electrode contact check — implementation-specific)
motionOK = (deviation < 4096)

qi_c = (ecgOK AND motionOK) ? 1 : 0
```

---

*Last updated: firmware/wrist_module/wrist_module.ino — BLE Central + Peripheral dual-role implementation*
