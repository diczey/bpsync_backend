"""
DataManager Unit Tests

Tests DataManager without a real DB or BLE device.
Uses SQLite in-memory DB.

Run:
    cd backend
    python -m pytest tests/test_data_manager.py -v
"""
 
import asyncio
import json
import pytest
from unittest.mock import MagicMock
 
from sqlalchemy import create_engine, Column, String, Integer, Float, BigInteger, JSON
from sqlalchemy.orm import sessionmaker, DeclarativeBase
 
from schemas.ble_frame import BLEFrame, FrameProcessResult
from services.data_manager import DataManager
 
 
# ──────────────────────────────────────────────────────────────────────────────
#  TEST DB SETUP  (SQLite in-memory)
# ──────────────────────────────────────────────────────────────────────────────
class Base(DeclarativeBase):
    pass
 
 
class HealthReadingTest(Base):
    """Minimal HealthReading model for testing (instead of the full model)"""
    __tablename__ = "health_readings"
 
    id            = Column(String, primary_key=True)
    user_id       = Column(String, nullable=False)
    timestamp     = Column(BigInteger, nullable=False)
    heart_rate    = Column(Integer, nullable=True)
    systolic_bp   = Column(Integer, nullable=True)
    diastolic_bp  = Column(Integer, nullable=True)
    spo2          = Column(Integer, nullable=True)
    temperature   = Column(Float, nullable=True)
    ecg_data      = Column(JSON, nullable=True)
    ppg_data      = Column(JSON, nullable=True)
    ptt           = Column(Float, nullable=True)
    quality_score = Column(Float, nullable=True)
    # Optional new columns (to be added when health_reading.py is modified)
    raw_frame     = Column(JSON, nullable=True)
    seq_num       = Column(Integer, nullable=True)
    battery       = Column(Integer, nullable=True)
 
 
engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
Base.metadata.create_all(engine)
TestSession = sessionmaker(bind=engine)
 
 
# ──────────────────────────────────────────────────────────────────────────────
#  SAMPLE FRAMES
# ──────────────────────────────────────────────────────────────────────────────
VALID_FRAME = json.dumps({
    "ts": 12345, "sq": 1,
    "pi": 98000, "pr": 75000,
    "ax": -120, "ay": 340, "az": 16000,
    "gx": 10,   "gy": -5,  "gz": 2,
    "tp": 36.7,
    "ep": 1,
    "qi_w": 1, "qi_c": 1, "qi": 1,
    "bt": 87
})
 
WRIST_ONLY_FRAME = json.dumps({
    "ts": 999, "sq": 2,
    "pi": 50000, "pr": 40000,
    "ax": 0, "ay": 0, "az": 16384,
    "gx": 0, "gy": 0, "gz": 0,
    "tp": 35.5,
    "ep": 0,
    "qi_w": 1, "qi_c": 0, "qi": 1,  # wrist-only mode: qi=qi_w
    "bt": 100
})
 
INVALID_JSON = "not a json string"
 
MISSING_FIELD_FRAME = json.dumps({
    "ts": 100, "sq": 3
    # pi, pr, ax, ay, az, gx, gy, gz, tp missing — required fields
})
 
 
# ──────────────────────────────────────────────────────────────────────────────
#  TESTS
# ──────────────────────────────────────────────────────────────────────────────
 
class TestBLEFrame:
    """BLEFrame schema validation tests"""
 
    def test_valid_frame_parse(self):
        frame = BLEFrame.parse_raw_json(VALID_FRAME)
        assert frame.ts == 12345
        assert frame.sq == 1
        assert frame.pi == 98000
        assert frame.tp == 36.7
        assert frame.qi == 1
 
    def test_wrist_only_frame(self):
        frame = BLEFrame.parse_raw_json(WRIST_ONLY_FRAME)
        assert frame.ep == 0
        assert frame.qi_c == 0
        assert frame.qi == 1  # wrist-only mode
 
    def test_invalid_json_raises(self):
        with pytest.raises(Exception):
            BLEFrame.parse_raw_json(INVALID_JSON)
 
    def test_missing_required_field(self):
        with pytest.raises(Exception):
            BLEFrame.parse_raw_json(MISSING_FIELD_FRAME)
 
    def test_quality_percent(self):
        frame = BLEFrame.parse_raw_json(VALID_FRAME)
        assert frame.quality_percent == 100.0
 
    def test_ppg_pair(self):
        frame = BLEFrame.parse_raw_json(VALID_FRAME)
        assert frame.ppg_pair == (98000, 75000)
 
    def test_chest_connected(self):
        frame = BLEFrame.parse_raw_json(VALID_FRAME)
        assert frame.chest_connected is True
 
        frame2 = BLEFrame.parse_raw_json(WRIST_ONLY_FRAME)
        assert frame2.chest_connected is False
 
 
class TestDataManager:
    """DataManager integration tests (SQLite in-memory DB)"""
 
    def _get_manager(self):
        dm = DataManager(
            db_factory=TestSession,
            default_user_id="test-user-001"
        )
        # Monkey-patch to use HealthReadingTest model for testing
        import services.data_manager as dm_module
        # Save original _frame_to_reading
        original = dm._frame_to_reading
 
        def patched_frame_to_reading(frame, user_id):
            import uuid, time
            reading = HealthReadingTest(
                id=str(uuid.uuid4()),
                user_id=user_id,
                timestamp=frame.received_at_ms or int(time.time() * 1000),
                temperature=frame.tp,
                ppg_data=[frame.pi, frame.pr],
                ecg_data=[frame.ep],
                quality_score=frame.quality_percent,
                raw_frame=frame.to_dict(),
                seq_num=frame.sq,
                battery=frame.bt,
            )
            return reading
 
        dm._frame_to_reading = patched_frame_to_reading
        return dm
 
    def test_process_valid_frame(self):
        dm = self._get_manager()
        result = asyncio.get_event_loop().run_until_complete(
            dm.process_frame(VALID_FRAME)
        )
        assert result.success is True
        assert result.reading_id is not None
        assert result.seq_num == 1
        assert result.quality == 100.0
 
    def test_process_invalid_frame(self):
        dm = self._get_manager()
        result = asyncio.get_event_loop().run_until_complete(
            dm.process_frame(INVALID_JSON)
        )
        assert result.success is False
        assert result.message is not None
 
    def test_db_record_written(self):
        dm = self._get_manager()
        asyncio.get_event_loop().run_until_complete(dm.process_frame(VALID_FRAME))
 
        db = TestSession()
        readings = db.query(HealthReadingTest).all()
        assert len(readings) >= 1
        latest = readings[-1]
        assert latest.temperature == 36.7
        assert latest.ppg_data == [98000, 75000]
        assert latest.quality_score == 100.0
        db.close()
 
    def test_stats_tracking(self):
        dm = self._get_manager()
        loop = asyncio.get_event_loop()
        loop.run_until_complete(dm.process_frame(VALID_FRAME))
        loop.run_until_complete(dm.process_frame(INVALID_JSON))
 
        stats = dm.get_stats()
        assert stats["failed"] >= 1
        assert stats["total"] >= 2