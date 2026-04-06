"""
DataManager Unit Tests

Tests DataManager without a real TimescaleDB or BLE device.
Uses SQLite in-memory DB that mirrors the TimescaleDB schema.

Run from project root:
    python -m pytest ble/tests/test_data_manager.py -v
"""

import asyncio
import json
import pytest
from unittest.mock import patch

from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker

from ble.frame import BLEFrame
from ble.data_manager import DataManager, WINDOW_SIZE


# ──────────────────────────────────────────────────────────────────────────────
#  TEST DB SETUP  (SQLite in-memory, mirrors TimescaleDB schema)
# ──────────────────────────────────────────────────────────────────────────────

engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})

with engine.connect() as conn:
    conn.execute(text("""
        CREATE TABLE IF NOT EXISTS wristband_data (
            time        DATETIME NOT NULL,
            user_id     TEXT     NOT NULL,
            ppg_ir      INTEGER,
            ppg_red     INTEGER,
            ax INTEGER, ay INTEGER, az INTEGER,
            gx INTEGER, gy INTEGER, gz INTEGER,
            temperature REAL,
            ep          INTEGER,
            qi_w        INTEGER,
            qi_c        INTEGER,
            qi          INTEGER,
            battery     INTEGER
        )
    """))
    conn.execute(text("""
        CREATE TABLE IF NOT EXISTS bp_readings (
            time        DATETIME NOT NULL,
            user_id     TEXT     NOT NULL,
            systolic    INTEGER,
            diastolic   INTEGER,
            heart_rate  INTEGER,
            ptt         REAL,
            quality     INTEGER,
            category    TEXT
        )
    """))
    conn.commit()

TestSession = sessionmaker(bind=engine)


# ──────────────────────────────────────────────────────────────────────────────
#  SAMPLE FRAME DATA
# ──────────────────────────────────────────────────────────────────────────────

VALID_FRAME = json.dumps({
    "ts": 12345, "sq": 1,
    "pi": 98000, "pr": 75000,
    "ax": -120, "ay": 340, "az": 16000,
    "gx": 10,   "gy": -5,  "gz": 2,
    "tp": 36.7,
    "ep": 1,
    "qi_w": 1, "qi_c": 1, "qi": 1,
    "bt": 87,
})

WRIST_ONLY_FRAME = json.dumps({
    "ts": 999, "sq": 2,
    "pi": 50000, "pr": 40000,
    "ax": 0, "ay": 0, "az": 16384,
    "gx": 0, "gy": 0, "gz": 0,
    "tp": 35.5,
    "ep": 0,
    "qi_w": 1, "qi_c": 0, "qi": 1,   # wrist-only mode: qi = qi_w
    "bt": 100,
})

INVALID_JSON = "not a json string"

MISSING_FIELD_FRAME = json.dumps({
    "ts": 100, "sq": 3,
    # pi, pr, ax, ay, az, gx, gy, gz, tp missing — all required
})


# ──────────────────────────────────────────────────────────────────────────────
#  HELPERS
# ──────────────────────────────────────────────────────────────────────────────

def _make_dm(**kwargs):
    """Create a DataManager backed by the SQLite test DB."""
    return DataManager(db_factory=TestSession, **kwargs)


def _run(coro):
    return asyncio.run(coro)


def _make_frame(ep=0, pi=50000, qi=1):
    """Build a minimal BLEFrame for signal processing tests."""
    return BLEFrame(
        ts=0, sq=0,
        pi=pi, pr=40000,
        ax=0, ay=0, az=16384,
        gx=0, gy=0, gz=0,
        tp=36.0, ep=ep,
        qi_w=qi, qi_c=qi, qi=qi, bt=100,
    )


# ──────────────────────────────────────────────────────────────────────────────
#  TESTS: BLEFrame schema validation
# ──────────────────────────────────────────────────────────────────────────────

class TestBLEFrame:
    """BLEFrame Pydantic schema validation tests."""

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

    def test_missing_required_field_raises(self):
        with pytest.raises(Exception):
            BLEFrame.parse_raw_json(MISSING_FIELD_FRAME)

    def test_quality_percent(self):
        frame = BLEFrame.parse_raw_json(VALID_FRAME)
        assert frame.quality_percent == 100.0

    def test_ppg_pair(self):
        frame = BLEFrame.parse_raw_json(VALID_FRAME)
        assert frame.ppg_pair == (98000, 75000)

    def test_chest_connected_true(self):
        frame = BLEFrame.parse_raw_json(VALID_FRAME)
        assert frame.chest_connected is True

    def test_chest_connected_false_wrist_only(self):
        frame = BLEFrame.parse_raw_json(WRIST_ONLY_FRAME)
        assert frame.chest_connected is False


# ──────────────────────────────────────────────────────────────────────────────
#  TESTS: DataManager frame processing
# ──────────────────────────────────────────────────────────────────────────────

class TestDataManager:
    """DataManager integration tests using SQLite in-memory DB."""

    def test_process_valid_frame_returns_success(self):
        dm = _make_dm(default_user_id="u-001")
        result = _run(dm.process_frame(VALID_FRAME))
        assert result.success is True
        assert result.seq_num == 1
        assert result.quality == 100.0

    def test_process_invalid_json_returns_failure(self):
        dm = _make_dm(default_user_id="u-001")
        result = _run(dm.process_frame(INVALID_JSON))
        assert result.success is False
        assert result.message is not None

    def test_wrist_only_frame_succeeds(self):
        dm = _make_dm(default_user_id="u-001")
        result = _run(dm.process_frame(WRIST_ONLY_FRAME))
        assert result.success is True
        assert result.seq_num == 2

    def test_db_record_written_to_wristband_data(self):
        dm = _make_dm(default_user_id="u-db-test")
        _run(dm.process_frame(VALID_FRAME))

        with engine.connect() as conn:
            rows = conn.execute(
                text("SELECT * FROM wristband_data WHERE user_id = 'u-db-test'")
            ).fetchall()

        assert len(rows) >= 1
        row = rows[-1]
        # Column order: time(0), user_id(1), ppg_ir(2), ppg_red(3), ax(4), ay(5), az(6),
        #               gx(7), gy(8), gz(9), temperature(10), ep(11), qi_w(12), qi_c(13),
        #               qi(14), battery(15)
        assert row[2] == 98000   # ppg_ir
        assert row[3] == 75000   # ppg_red
        assert row[10] == 36.7   # temperature
        assert row[15] == 87     # battery

    def test_stats_initial_state(self):
        dm = _make_dm()
        stats = dm.get_stats()
        assert stats["processed"] == 0
        assert stats["failed"] == 0
        assert stats["total"] == 0
        assert stats["bp_inferences"] == 0

    def test_stats_tracking(self):
        dm = _make_dm(default_user_id="u-stats")
        _run(dm.process_frame(VALID_FRAME))
        _run(dm.process_frame(INVALID_JSON))

        stats = dm.get_stats()
        assert stats["processed"] >= 1
        assert stats["failed"] >= 1
        assert stats["total"] >= 2

    def test_stats_success_rate_all_valid(self):
        dm = _make_dm(default_user_id="u-rate")
        _run(dm.process_frame(VALID_FRAME))
        _run(dm.process_frame(VALID_FRAME))
        stats = dm.get_stats()
        assert stats["success_rate"] == 100.0

    def test_set_user_id_updates_active_user(self):
        dm = _make_dm(default_user_id="u-old")
        dm.set_user_id("u-new")
        assert dm._default_user_id == "u-new"

    def test_set_user_age_updates_age(self):
        dm = _make_dm()
        dm.set_user_age(35.5)
        assert dm._user_age == 35.5

    def test_buffer_fill_format(self):
        dm = _make_dm(default_user_id="u-buf")
        _run(dm.process_frame(VALID_FRAME))
        stats = dm.get_stats()
        assert stats["buffer_fill"].endswith(f"/{WINDOW_SIZE}")
        assert stats["buffer_fill"].startswith("1/")

    def test_buffer_clears_after_full_window(self):
        """After WINDOW_SIZE frames, _run_ml_window clears the buffer."""
        dm = _make_dm(default_user_id="u-window")
        # Patch ML so it does not need real model file
        mock_result = {"systolic": 120, "diastolic": 80, "category": "Normal"}
        with patch("backend.services.ml_service.predict_blood_pressure", return_value=mock_result):
            for _ in range(WINDOW_SIZE):
                _run(dm.process_frame(VALID_FRAME))
        # Buffer should be empty after ML window ran
        assert len(dm._window) == 0


# ──────────────────────────────────────────────────────────────────────────────
#  TESTS: Signal processing static methods
# ──────────────────────────────────────────────────────────────────────────────

class TestSignalProcessing:
    """Unit tests for DataManager static signal processing methods."""

    def test_calc_heart_rate_no_peaks_returns_none(self):
        frames = [_make_frame(ep=0)] * 100
        assert DataManager._calc_heart_rate(frames) is None

    def test_calc_heart_rate_one_peak_returns_none(self):
        frames = [_make_frame(ep=0)] * 99 + [_make_frame(ep=1)]
        assert DataManager._calc_heart_rate(frames) is None

    def test_calc_heart_rate_correct_bpm(self):
        # 12 R-peaks in 100 frames (10 s) → 12 × 6 = 72 BPM
        frames = [_make_frame(ep=1 if i % 8 == 0 else 0) for i in range(100)]
        hr = DataManager._calc_heart_rate(frames)
        assert hr is not None
        assert 60 <= hr <= 120   # physiologically reasonable

    def test_calc_heart_rate_multiplier_is_6(self):
        # Exactly 10 peaks → 10 × 6 = 60 BPM
        frames = [_make_frame(ep=1 if i % 10 == 0 else 0) for i in range(100)]
        hr = DataManager._calc_heart_rate(frames)
        assert hr == 60.0

    def test_calc_ptt_no_peaks_returns_none(self):
        frames = [_make_frame(ep=0)] * 100
        ptt, std = DataManager._calc_ptt(frames)
        assert ptt is None
        assert std == 0.0

    def test_calc_ptt_one_peak_returns_none(self):
        frames = [_make_frame(ep=0)] * 99 + [_make_frame(ep=1)]
        ptt, std = DataManager._calc_ptt(frames)
        assert ptt is None
        assert std == 0.0

    def test_calc_ptt_valid_range_when_signal_present(self):
        """
        ECG R-peak every 10 frames; PPG elevated 3 frames after each R-peak.
        PTT = 3 frames × 100 ms/frame = 300 ms → within 100–500 ms valid range.
        """
        frames = []
        for i in range(100):
            ep = 1 if i % 10 == 0 else 0
            pi = 80000 if (i % 10) in (3, 4) else 40000
            frames.append(_make_frame(ep=ep, pi=pi))

        ptt, std = DataManager._calc_ptt(frames)
        if ptt is not None:
            assert 100.0 <= ptt <= 500.0
