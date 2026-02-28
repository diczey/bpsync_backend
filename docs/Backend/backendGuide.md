BPSync Backend – Architecture & Integration Guide

* import atarken dosya pathlerine dikkt edelim lütfen.

1. System Overview

BPSync backend is built using a dual-database architecture designed to separate transactional application data from high-frequency time-series sensor data.

Architecture Flow

Frontend / Hardware Device
→ FastAPI Backend
→ PostgreSQL (Application Data)
→ TimescaleDB (Sensor Time-Series Data)

This separation provides:

Clear separation of concerns

Optimized time-series performance

Scalability for high-frequency ingestion

Clean production-ready structure

2. Technology Stack

FastAPI (REST API)

SQLAlchemy (DB connection layer)

PostgreSQL 16 (Application DB)

TimescaleDB (Time-series DB)

Docker Compose (Orchestration)

Uvicorn (ASGI server)

3. Repository Structure
database/
 ├── docker-compose.yml
 ├── timescale_schema.sql

backend/
 ├── Dockerfile
 ├── requirements.txt
 └── app/
      ├── main.py
      ├── config.py
      ├── database.py
      ├── routers/
      ├── schemas/
      ├── models/
      └── services/
Important Files

docker-compose.yml → Starts PostgreSQL, TimescaleDB and Backend

timescale_schema.sql → Creates hypertables

database.py → Defines two DB engines (Postgres + Timescale)

routers/sensor.py → Sensor ingestion endpoints

4. Running the Backend

From repository root:

cd database
docker compose up -d --build

Check containers:

docker ps

Swagger documentation:

http://localhost:8000/docs
5. Database Architecture
5.1 PostgreSQL (Application Database)

Database: bpsync
Purpose:

Users

Authentication

Notifications

Dashboard data

Non-time-series records

Connection (inside Docker):

postgresql://bpsync_app:bpsync_password@postgres:5432/bpsync
5.2 TimescaleDB (Sensor Database)

Database: bpsync_sensor
Purpose:

ECG stream

Wristband telemetry

High-frequency sensor ingestion

Hypertables:

wristband_data

ecg_data

Connection:

postgresql://bpsync_sensor_app:bpsync_sensor_password@timescaledb:5432/bpsync_sensor

Timescale is optimized for:

Time-based partitioning

Aggregations

Range queries

6. Integration Guide – Frontend Developers

Frontend must never connect directly to databases.
All communication happens through REST API.

6.1 Base URL
http://localhost:8000
6.2 Authentication Flow

POST /auth/register

POST /auth/login

Store JWT token

Send token in header:

Authorization: Bearer <JWT>
6.3 Sensor Visualization

Frontend should:

Fetch time-series data through backend endpoints (future analytics endpoints)

Never query Timescale directly

Use OpenAPI schema for endpoint definitions:

http://localhost:8000/openapi.json
6.4 CORS

Allowed origins are configured in:

backend/app/config.py

Update if frontend runs on different port.

7. Integration Guide – Hardware / Sensor Developers

Hardware devices must send HTTP POST requests to backend.

All sensor data is ingested via backend and written to TimescaleDB.

7.1 Wristband Data Ingestion

Endpoint:

POST /sensor/wristband

Payload format:

{
  "time": "2026-02-26T20:40:00Z",
  "patient_id": 1,
  "heart_rate": 78,
  "spo2": 98,
  "movement": 0.12
}

Field Requirements:

time → ISO 8601 timestamp (UTC recommended)

patient_id → must match existing user

Other fields optional but recommended

Behavior:

Data is written directly into wristband_data hypertable

Insert is atomic

No local buffering inside backend

7.2 ECG Data Ingestion

Endpoint:

POST /sensor/ecg

Payload:

{
  "time": "2026-02-26T20:40:01Z",
  "patient_id": 1,
  "ecg_value": 0.83
}

This writes to ecg_data hypertable.

7.3 Important Hardware Notes

Use UTC timestamps

Ensure clock synchronization

For high-frequency streaming, consider batching (future enhancement)

Backend currently accepts single-record ingestion

8. Internal Backend Design
Dual Engine Strategy

In database.py:

user_engine → PostgreSQL

sensor_engine → TimescaleDB

Separate session dependencies:

get_db() → App data

get_sensor_db() → Sensor data

This prevents cross-database contamination.

9. Timescale Initialization

timescale_schema.sql is mounted into:

/docker-entrypoint-initdb.d/

It runs automatically only when Timescale volume is first created.

To reset Timescale:

docker compose down
docker volume rm database_tsdata
docker compose up -d timescaledb
10. Production Considerations

Before production:

Replace hardcoded credentials

Use environment secrets

Enable HTTPS (reverse proxy)

Add rate limiting

Add retention policies in Timescale

Add sensor batching endpoint

Protect sensor endpoints with authentication

11. Current System Capabilities

✔ Dual database architecture
✔ Time-series hypertable ingestion
✔ Dockerized environment
✔ Swagger API documentation
✔ Authentication system
✔ Sensor ingestion endpoints
✔ Clean separation of application vs sensor data

Backend is ready for:

Frontend integration

Hardware data ingestion

ML prediction integration

Trend analytics development