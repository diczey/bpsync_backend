# BPSync Backend – Architecture & Integration Guide

> Last updated: 2026-04-02  
> Branch: `devfixed_frontend_version2`


---

## 1. System Overview

BPSync backend is built with a **dual-database architecture** separating transactional data from high-frequency time-series sensor data.

```
Mobile App (Android)
  → FastAPI Backend (port 8000)
      → PostgreSQL  (users, notifications)
      → TimescaleDB (wristband_data, ecg_data, bp_readings)
```

---

## 2. Technology Stack

| Component | Technology |
|---|---|
| REST API | FastAPI + Uvicorn |
| ORM | SQLAlchemy |
| App DB | PostgreSQL 16 |
| Sensor DB | TimescaleDB |
| Orchestration | Docker Compose |
| Auth | JWT (python-jose) |
| ML | XGBoost (BP inference from PTT) |

---

## 3. Running the Backend

```powershell
cd database
docker compose up -d --build
docker ps        # verify postgres, timescaledb, backend are Up
```

Swagger UI: **http://localhost:8000/docs**

---

## 4. Database Architecture

### PostgreSQL — `bpsync`
Stores: users, notifications (transactional records)  
Connection: `postgresql://bpsync_app:bpsync_password@localhost:5432/bpsync`

### TimescaleDB — `bpsync_sensor`
Stores: wristband_data (10 Hz frames), ecg_data, bp_readings (ML output)  
Connection: `postgresql://bpsync_sensor_app:bpsync_sensor_password@timescaledb:5432/bpsync_sensor`

**TimescaleDB hypertables:**

| Table | Write Rate | Written By |
|---|---|---|
| `wristband_data` | 10 Hz | `ble/data_manager.py` |
| `ecg_data` | streaming | `ble/data_manager.py` |
| `bp_readings` | ~1/10 s | ML inference in `data_manager.py` |

---

## 5. API Endpoint Reference

> All protected endpoints require `Authorization: Bearer <JWT>` header.

### Auth — `/auth`

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/auth/login` | `{email, password}` | `LoginResponse` |
| POST | `/auth/register` | `{email, password, name, date_of_birth?, gender?, weight?, height?, blood_type?, emergency_contact?}` | `LoginResponse` |
| POST | `/auth/logout` | — | `{success}` |

**LoginResponse:**
```json
{
  "success": true,
  "token": "<JWT>",
  "user": { "id", "email", "name", "avatar_url", "date_of_birth", "gender",
            "weight", "height", "blood_type", "emergency_contact" },
  "message": "Registration successful"
}
```

---

### Dashboard — `/dashboard`

| Method | Path | Returns |
|---|---|---|
| GET | `/dashboard/summary` | `DashboardResponse` |
| GET | `/dashboard/health-status` | `HealthStatusResponse` |
| GET | `/dashboard/pulse` | `PulseResponse` |
| GET | `/dashboard/ppg/signal` | `PpgSignalResponse` |

**DashboardResponse:**
```json
{
  "success": true,
  "summary": {
    "latest_systolic": 120,
    "latest_diastolic": 80,
    "latest_heart_rate": 72,
    "latest_spo2": 98,
    "latest_temperature": 36.6,
    "health_status": "NORMAL",
    "last_updated": 1742300400000
  }
}
```

> `last_updated` is a **Unix milliseconds timestamp** (matches Android `Long`).  
> `health_status` values: `"NORMAL"`, `"HIGH"`, `"LOW"`, `"ELEVATED"`  
> `latest_spo2` comes from a constant 98 until the SpO2 inference pipeline is added.  
> `latest_temperature` is fetched from the most recent `wristband_data` row.

---

### Health Readings — `/readings`

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/readings?limit=50` | — | `HealthReadingsResponse` |
| POST | `/readings` | `HealthReadingCreate` | `HealthReadingsResponse` |

**HealthReadingDto:**
```json
{
  "id": "<user_id>-<timestamp_ms>",
  "user_id": "abc-123",
  "timestamp": 1742300400000,
  "heart_rate": 72,
  "systolic_bp": 120,
  "diastolic_bp": 80,
  "spo2": null,
  "temperature": null,
  "ecg_data": null,
  "ppg_data": null
}
```

> `spo2` and `temperature` are `null` for rows sourced from `bp_readings` (those columns don't exist there yet). Manual readings submitted via POST do include them in the response echo.

**HealthReadingCreate (POST body):**
```json
{
  "timestamp": 1742300400000,
  "heart_rate": 72,
  "systolic_bp": 120,
  "diastolic_bp": 80,
  "spo2": 98,
  "temperature": 36.6
}
```

---

### Trends — `/trends`

| Method | Path | Returns |
|---|---|---|
| GET | `/trends?period=week` | `TrendResponse` |

**period** values: `day` (last 24 h), `week` (last 7 days), `month` (last 30 days)

**TrendResponse:**
```json
{
  "success": true,
  "trends": [
    {
      "type": "systolic",
      "data_points": [{"timestamp": 1742200000000, "value": 118.0}, ...],
      "average": 121.5,
      "min": 110.0,
      "max": 135.0
    },
    { "type": "diastolic", ... },
    { "type": "heart_rate", ... }
  ]
}
```

> Always returns three TrendDataDto items (systolic, diastolic, heart_rate) even if a metric has no data (empty `data_points`, zeros for average/min/max).

---

### Reports — `/reports`

| Method | Path | Returns |
|---|---|---|
| GET | `/reports/weekly?week_offset=0` | `WeeklyReportResponse` |
| GET | `/reports/monthly?month_offset=0` | `WeeklyReportResponse` |

**WeeklyReport:**
```json
{
  "week_start": "2026-03-11",
  "week_end": "2026-03-17",
  "avg_systolic": 121.4,
  "avg_diastolic": 79.2,
  "avg_heart_rate": 73.1,
  "readings_count": 28,
  "health_score": 85,
  "daily_summaries": [{ "date": "2026-03-11", "avg_systolic": 119.0, "avg_diastolic": 78.0, "avg_heart_rate": 72.5, "reading_count": 4 }, ...]
}
```

---

### Profile — `/profile`

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/profile` | — | `ProfileResponse` |
| PUT | `/profile` | `ProfileUpdateRequest` | `ProfileResponse` |
| DELETE | `/profile` | — | `{success}` |

---

### BLE — `/ble`

| Method | Path | Returns |
|---|---|---|
| GET | `/ble/status` | `BLEStatusResponse` |
| POST | `/ble/scan` | `ScanResult` |
| POST | `/ble/start` | `CommandResponse` |
| POST | `/ble/stop` | `CommandResponse` |
| GET | `/ble/stats` | DataManager frame statistics |

---

### ML Readings — `/readings`

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/readings/predict-bp` | `{ptt, heart_rate, age?, ptt_std?}` | `BPPredictionResponse` |
| POST | `/readings/calibrate-bp` | `{measured_systolic, measured_diastolic, ptt, heart_rate, age?}` | `{success, message}` |
| GET | `/readings/model-info` | — | Model status + feature importances |

---

## 6. Mock Data Mode

Set `USE_MOCK_DATA=true` in `backend/.env` to enable mock mode.

In mock mode:
- `GET /dashboard/summary` → generates realistic BP summary
- `GET /readings` → generates 50 historical readings with ECG/PPG waveforms
- `GET /trends?period=*` → generates time-series for systolic, diastolic, heart_rate
- `GET /notifications` → generates 10 sample notifications (first 4 unread)
- `POST /auth/login` → auto-creates user if not found (quick onboarding for dev)

---

## 7. Code Conventions

- Every **function and class** has an English docstring explaining what it does and why it exists there.
- Every **global constant** has an inline comment explaining the value choice (e.g. why 98 for default SpO2).
- Schema field names match the Android `Models.kt` `@SerializedName` values exactly.
- When adding a new endpoint, update this guide in the same commit.

---

## 8. Changelog

### 2026-04-02 — Full Android Screen Integration

| Area | What changed |
|---|---|
| **Health Status screen** | Added `last_checkup_date` (DATE) column to `users` table. Mobile shows "X days since last check-up" countdown or prompts user to enter the date if null. |
| **PPG Signal screen** | `PpgViewModel` + `PpgRepository` created; wired to `GET /dashboard/ppg/signal`. Canvas animates real signal array from backend. |
| **Heart Rate / ECG screen** | `PulseViewModel` + `PulseRepository` created; polls `GET /dashboard/pulse` every 2 s. Live BPM, Resting HR and ECG waveform Canvas replace all hard-coded values. |
| **Trends screen** | `TrendsRepository` bug fixed — was using old `RetrofitClient` and `SessionStore.token` (static String) instead of `ApiClient` + `SessionStore.token.value` (StateFlow). Charts now receive real backend data for Daily / Weekly / Monthly. |
| **BLE Connection screen** | `ScanResult` DTO added to `Models.kt`. `ApiService.scanBle` return type changed to `ScanResult`. `BleRepository` parses real device name/address. `BleViewModel` auto-calls `POST /ble/start` on connect and `POST /ble/stop` on disconnect. |
| **Blood Pressure screen** | `BloodPressureViewModel` fetches Dashboard summary + Trends in parallel. Screen rewritten: live reading card, dynamic status badge, real AVG cards, dual-line Canvas chart (systolic in red, diastolic in pink). |
| **Android `Models.kt`** | Added `PulseResponse`, `PulseDataPoint`, `PpgSignalResponse`, `ScanResult` DTOs. |
| **Android `ApiService.kt`** | Added `getPulse`, `getPpgSignal`, `startBleStreaming`, `stopBleStreaming`; fixed duplicate import/interface block. |

---

### 2026-03-18 — Mobile-Backend Schema Alignment


| File | Change |
|---|---|
| `routers/auth.py` | Added `RegisterRequest` with name + profile fields; register endpoint now uses it instead of `LoginRequest` |
| `routers/dashboard.py` | Added `latest_spo2`, `latest_temperature`; changed `last_updated` from `str` to Unix ms `int`; added wristband_data temperature query; extracted `_classify_bp` helper |
| `routers/readings.py` | Renamed `systolic→systolic_bp`, `diastolic→diastolic_bp`; added surrogate `id` field; changed `time→timestamp` (Unix ms); added `POST /readings` endpoint for manual entry |
| `routers/trends.py` | Complete rewrite — response format changed from `{bucket, avg_systolic, ...}` to `{type, data_points, average, min, max}` matching Android `TrendDataDto` |
| `routers/reports.py` | English comments only (schema was already correct) |
| `routers/notifications.py` | English comments only |
| `routers/profile.py` | English comments only |
| `utils/mock_data.py` | Added `generate_health_readings()`, `generate_trends()`; fixed `generate_dashboard_summary()` to use timezone-aware UTC timestamps; updated notification templates to English |

---

## 9. Production Checklist

- [ ] Replace SHA-256 password hashing with bcrypt
- [ ] Use environment secrets (not hardcoded credentials)
- [ ] Enable HTTPS (reverse proxy / nginx)
- [ ] Add rate limiting
- [ ] Add Timescale retention policies
- [ ] Add SpO2 inference from `ppg_ir`/`ppg_red` in `data_manager.py`
- [ ] Add sensor batching endpoint for high-frequency ingestion
- [ ] Protect sensor endpoints with authentication
- [ ] Cascade delete sensor data when user account is removed