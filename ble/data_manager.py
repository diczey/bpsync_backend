import logging, statistics, time
from collections import deque
from datetime import datetime, timezone
from typing import Callable, Optional
from sqlalchemy.orm import Session
from sqlalchemy import text
from ble.frame import BLEFrame, FrameProcessResult

logger = logging.getLogger(__name__)
WINDOW_SIZE = 100
MIN_QUALITY_FRAMES = 60

class DataManager:
    def __init__(self, db_factory, default_user_id='', user_age=40.0):
        self._db_factory = db_factory
        self._default_user_id = default_user_id
        self._user_age = user_age
        self._frames_processed = 0
        self._frames_failed = 0
        self._bp_inferences = 0
        self._window = deque(maxlen=WINDOW_SIZE)
        logger.info('DataManager initialized (user_id=%s)', default_user_id)

    async def process_frame(self, json_str, user_id=None):
        uid = user_id or self._default_user_id
        try:
            frame = BLEFrame.parse_raw_json(json_str)
            frame.received_at_ms = int(time.time() * 1000)
        except Exception as exc:
            self._frames_failed += 1
            logger.warning('Frame parse error: %s', exc)
            return FrameProcessResult(success=False, seq_num=0, quality=0.0, message=f'Parse error: {exc}')

        db = self._db_factory()
        try:
            self._insert_raw_frame(db, frame, uid)
            db.commit()
            self._frames_processed += 1
        except Exception as exc:
            db.rollback()
            self._frames_failed += 1
            logger.error('Raw frame DB error (seq=%d): %s', frame.sq, exc)
            return FrameProcessResult(success=False, seq_num=frame.sq, quality=frame.quality_percent, message=f'DB error: {exc}')
        finally:
            db.close()

        self._window.append(frame)
        if len(self._window) == WINDOW_SIZE:
            await self._run_ml_window(uid)

        return FrameProcessResult(success=True, seq_num=frame.sq, quality=frame.quality_percent)

    def set_user_id(self, user_id):
        self._default_user_id = user_id
        logger.info('DataManager: active user_id=%s', user_id)

    def set_user_age(self, age):
        self._user_age = age

    def get_stats(self):
        total = self._frames_processed + self._frames_failed
        return {
            'processed': self._frames_processed,
            'failed': self._frames_failed,
            'total': total,
            'success_rate': round(self._frames_processed / total * 100, 1) if total else 0.0,
            'bp_inferences': self._bp_inferences,
            'buffer_fill': f'{len(self._window)}/{WINDOW_SIZE}',
        }

    def _insert_raw_frame(self, db, frame, user_id):
        db.execute(text('''
            INSERT INTO wristband_data
                (time, user_id, ppg_ir, ppg_red, ax, ay, az, gx, gy, gz,
                 temperature, ep, qi_w, qi_c, qi, battery)
            VALUES
                (:time, :user_id, :ppg_ir, :ppg_red, :ax, :ay, :az,
                 :gx, :gy, :gz, :temperature, :ep, :qi_w, :qi_c, :qi, :battery)
        '''), {
            'time': datetime.fromtimestamp(frame.received_at_ms / 1000, tz=timezone.utc),
            'user_id': user_id,
            'ppg_ir': frame.pi, 'ppg_red': frame.pr,
            'ax': frame.ax, 'ay': frame.ay, 'az': frame.az,
            'gx': frame.gx, 'gy': frame.gy, 'gz': frame.gz,
            'temperature': frame.tp,
            'ep': frame.ep, 'qi_w': frame.qi_w, 'qi_c': frame.qi_c,
            'qi': frame.qi, 'battery': frame.bt,
        })

    async def _run_ml_window(self, user_id):
        frames = list(self._window)
        self._window.clear()
        good_count = sum(1 for f in frames if f.qi == 1)
        if good_count < MIN_QUALITY_FRAMES:
            logger.info('ML window skipped: %d/%d good frames', good_count, WINDOW_SIZE)
            return
        heart_rate = self._calc_heart_rate(frames)
        ptt, ptt_std = self._calc_ptt(frames)
        if heart_rate is None or ptt is None:
            logger.info('ML window skipped: not enough peaks')
            return
        try:
            from backend.services.ml_service import predict_blood_pressure
            result = predict_blood_pressure(ptt=ptt, heart_rate=heart_rate, age=self._user_age, ptt_std=ptt_std)
        except Exception as exc:
            logger.error('ML inference error: %s', exc)
            return
        avg_quality = round(sum(f.qi for f in frames) / len(frames) * 100)
        db = self._db_factory()
        try:
            db.execute(text('''
                INSERT INTO bp_readings
                    (time, user_id, systolic, diastolic, heart_rate, ptt, quality, category)
                VALUES (:time, :user_id, :systolic, :diastolic, :heart_rate, :ptt, :quality, :category)
            '''), {
                'time': datetime.now(timezone.utc),
                'user_id': user_id,
                'systolic': result['systolic'],
                'diastolic': result['diastolic'],
                'heart_rate': int(heart_rate),
                'ptt': round(ptt, 2),
                'quality': avg_quality,
                'category': result['category'],
            })
            db.commit()
            self._bp_inferences += 1
            logger.info('BP saved: SYS=%d DIA=%d HR=%d PTT=%.1fms', result['systolic'], result['diastolic'], int(heart_rate), ptt)
        except Exception as exc:
            db.rollback()
            logger.error('bp_readings DB error: %s', exc)
        finally:
            db.close()

    @staticmethod
    def _calc_heart_rate(frames):
        r_peak_count = sum(1 for f in frames if f.ep == 1)
        if r_peak_count < 2:
            return None
        return r_peak_count * 6.0  # 100 frames = 10 sec -> x6 = BPM

    @staticmethod
    def _calc_ptt(frames):
        r_peak_indices = [i for i, f in enumerate(frames) if f.ep == 1]
        if len(r_peak_indices) < 2:
            return None, 0.0
        ir_values = [f.pi for f in frames]
        ir_mean = sum(ir_values) / len(ir_values)
        ppg_peaks = []
        in_peak = False
        peak_val = peak_idx = 0
        for i, val in enumerate(ir_values):
            if val > ir_mean and not in_peak:
                in_peak, peak_val, peak_idx = True, val, i
            elif in_peak and val > peak_val:
                peak_val, peak_idx = val, i
            elif in_peak and val <= ir_mean:
                ppg_peaks.append(peak_idx)
                in_peak = False
        if not ppg_peaks:
            return None, 0.0
        ptts = []
        for r in r_peak_indices:
            nxt = next((p for p in ppg_peaks if p > r), None)
            if nxt is not None:
                ms = (nxt - r) * 100.0
                if 100.0 <= ms <= 500.0:
                    ptts.append(ms)
        if len(ptts) < 2:
            return None, 0.0
        return statistics.mean(ptts), statistics.stdev(ptts)
