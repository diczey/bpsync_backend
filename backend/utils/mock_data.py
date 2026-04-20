"""
Mock Data Generator — Development without real sensors

This module generates physiologically realistic fake data so the mobile app
can be developed and tested before actual BLE hardware is connected.

All functions return plain dicts whose keys match the Pydantic DTO field names
used in the routers, so they can be unpacked directly with **kwargs.

Physiological reference ranges used:
  Heart rate:    60-100 bpm (normal adult resting)
  Systolic BP:   90-140 mmHg
  Diastolic BP:  60-90  mmHg  (systolic - diastolic >= 30 always)
  SpO2:          95-100 %
  Body temp:     36.1-37.2 °C
  PTT:           200-400 ms  (inversely correlated with BP)
"""
import random
import math
import uuid
from datetime import datetime, timedelta, timezone
from typing import List, Tuple


def generate_heart_rate(base: int = 75, variation: int = 15) -> int:
    """Generate realistic heart rate (bpm)."""
    # Ensure heart rate stays within a plausible range
    return max(55, min(120, base + random.randint(-variation, variation)))


def generate_blood_pressure() -> Tuple[int, int]:
    """Generate realistic blood pressure (systolic, diastolic)"""
    # Normal ranges with some variation
    systolic = random.randint(110, 135)
    diastolic = random.randint(70, 85)
    
    # Ensure systolic > diastolic by at least 30
    if systolic - diastolic < 30:
        systolic = diastolic + 30 + random.randint(5, 15)
    
    return systolic, diastolic


def generate_spo2() -> int:
    """Generate realistic SpO2 value"""
    # Most healthy people have SpO2 between 95-100
    return random.choices(
        [95, 96, 97, 98, 99, 100],
        weights=[5, 10, 20, 30, 25, 10]  # 98% is most common
    )[0]


def generate_temperature() -> float:
    """Generate realistic body temperature in Celsius"""
    # Normal: 36.1°C - 37.2°C
    return round(random.uniform(36.1, 37.2), 1)


def generate_ecg_waveform(duration_seconds: float = 5.0, sampling_rate: int = 250) -> List[float]:
    """
    Generate synthetic ECG waveform
    
    This is a simplified ECG simulation for development purposes.
    Real ECG data will come from the AD8232 sensor.
    """
    num_samples = int(duration_seconds * sampling_rate)
    heart_rate = generate_heart_rate()
    beat_interval = 60.0 / heart_rate  # seconds per beat
    samples_per_beat = int(beat_interval * sampling_rate)
    
    ecg = []
    for i in range(num_samples):
        phase = (i % samples_per_beat) / samples_per_beat
        
        # Simple QRS-like waveform
        if 0.15 < phase < 0.17:  # Q wave
            value = -0.1
        elif 0.17 < phase < 0.20:  # R wave (peak)
            value = 1.0
        elif 0.20 < phase < 0.22:  # S wave
            value = -0.2
        elif 0.35 < phase < 0.45:  # T wave
            value = 0.3 * math.sin((phase - 0.35) / 0.1 * math.pi)
        else:
            value = 0.0
        
        # Add some noise
        value += random.uniform(-0.05, 0.05)
        ecg.append(round(value, 4))
    
    return ecg


def generate_ppg_waveform(duration_seconds: float = 5.0, sampling_rate: int = 100) -> List[float]:
    """
    Generate synthetic PPG waveform
    
    This is a simplified PPG simulation for development purposes.
    Real PPG data will come from the MAX30102 sensor.
    """
    num_samples = int(duration_seconds * sampling_rate)
    heart_rate = generate_heart_rate()
    beat_interval = 60.0 / heart_rate
    samples_per_beat = int(beat_interval * sampling_rate)
    
    ppg = []
    for i in range(num_samples):
        phase = (i % samples_per_beat) / samples_per_beat
        
        # Simplified PPG waveform (systolic peak + dicrotic notch)
        if phase < 0.15:  # Rising edge to systolic peak
            value = math.sin(phase / 0.15 * math.pi / 2)
        elif phase < 0.30:  # Falling from systolic peak
            value = math.cos((phase - 0.15) / 0.15 * math.pi / 2) * 0.8
        elif phase < 0.40:  # Dicrotic notch
            value = 0.3 + 0.2 * math.sin((phase - 0.30) / 0.10 * math.pi)
        else:  # Diastolic decay
            value = 0.3 * math.exp(-(phase - 0.40) * 5)
        
        # Add noise
        value += random.uniform(-0.02, 0.02)
        ppg.append(round(value, 4))
    
    return ppg


def generate_ptt() -> float:
    """
    Generate Pulse Transit Time (PTT) in milliseconds
    
    PTT = time between ECG R-peak and PPG peak
    Normal range: 200-400 ms
    Inversely correlated with blood pressure
    """
    return round(random.uniform(200, 400), 2)


def generate_health_reading(user_id: str, timestamp: int = None) -> dict:
    """Generate a complete health reading"""
    if timestamp is None:
        timestamp = int(datetime.now().timestamp() * 1000)
    
    systolic, diastolic = generate_blood_pressure()
    
    return {
        "id": str(uuid.uuid4()),
        "user_id": user_id,
        "timestamp": timestamp,
        "heart_rate": generate_heart_rate(),
        "systolic_bp": systolic,
        "diastolic_bp": diastolic,
        "spo2": generate_spo2(),
        "temperature": generate_temperature(),
        "ecg_data": generate_ecg_waveform(duration_seconds=2.0),  # Shorter for storage
        "ppg_data": generate_ppg_waveform(duration_seconds=2.0),
    }


def generate_health_readings(user_id: str, count: int = 50) -> List[dict]:
    """
    Generate mock readings in the HealthReadingDto schema used by GET /readings.

    Each dict has the same keys as HealthReadingDto so routers can do
    HealthReadingDto(**r) without any field mapping.
    Short ECG/PPG waveforms are included so the mobile waveform charts
    have data to render during development.
    """
    now = datetime.now(timezone.utc)
    readings = []

    for i in range(count):
        # Spread readings backwards in time (one per ~6 hours)
        ts = now - timedelta(hours=i * 6 + random.randint(0, 3))
        ts_ms = int(ts.timestamp() * 1000)
        systolic, diastolic = generate_blood_pressure()

        readings.append({
            "id":           f"{user_id}-{ts_ms}",
            "user_id":      user_id,
            "timestamp":    ts_ms,
            "heart_rate":   generate_heart_rate(),
            "systolic_bp":  systolic,      # Matches HealthReadingDto.systolicBp
            "diastolic_bp": diastolic,     # Matches HealthReadingDto.diastolicBp
            "spo2":         generate_spo2(),
            "temperature":  generate_temperature(),
            "ecg_data":     generate_ecg_waveform(duration_seconds=1.0),
            "ppg_data":     generate_ppg_waveform(duration_seconds=1.0),
        })

    # Newest first — matches ORDER BY time DESC from the real query
    readings.sort(key=lambda x: x["timestamp"], reverse=True)
    return readings


def generate_dashboard_summary(user_id: str) -> dict:
    """
    Generate a dashboard summary dict that matches DashboardSummary in dashboard.py.

    user_id is accepted so this function can be extended to produce
    user-specific patterns (e.g. personalised baselines) in the future.
    last_updated is returned as Unix milliseconds to match the Android Long field.
    """
    systolic, diastolic = generate_blood_pressure()
    heart_rate = generate_heart_rate()
    spo2 = generate_spo2()

    if systolic > 140:
        health_status = "HIGH"
    elif systolic < 90:
        health_status = "LOW"
    elif spo2 < 95 or heart_rate > 100:
        health_status = "ELEVATED"
    else:
        health_status = "NORMAL"

    return {
        "latest_systolic":    systolic,
        "latest_diastolic":   diastolic,
        "latest_heart_rate":  heart_rate,
        "latest_spo2":        spo2,
        "latest_temperature": generate_temperature(),
        "health_status":      health_status,
        "last_updated":       int(datetime.now(timezone.utc).timestamp() * 1000),
    }


def generate_trend_data(trend_type: str, days: int = 7) -> dict:
    """Generate trend data for a specific metric"""
    now = datetime.now()
    data_points = []
    values = []
    
    for day_offset in range(days):
        for hour_offset in range(0, 24, 6):  # Every 6 hours
            timestamp = int((now - timedelta(days=day_offset, hours=hour_offset)).timestamp() * 1000)
            
            if trend_type == "heart_rate":
                value = generate_heart_rate()
            elif trend_type == "systolic":
                value, _ = generate_blood_pressure()
            elif trend_type == "diastolic":
                _, value = generate_blood_pressure()
            elif trend_type == "spo2":
                value = generate_spo2()
            elif trend_type == "temperature":
                value = generate_temperature()
            else:
                value = random.uniform(0, 100)
            
            data_points.append({"timestamp": timestamp, "value": float(value)})
            values.append(float(value))
    
    # Sort by timestamp
    data_points.sort(key=lambda x: x["timestamp"])
    
    return {
        "type": trend_type,
        "data_points": data_points,
        "average": round(sum(values) / len(values), 2) if values else 0,
        "min": round(min(values), 2) if values else 0,
        "max": round(max(values), 2) if values else 0
    }


def generate_weekly_report(user_id: str, week_offset: int = 0) -> dict:
    """Generate weekly health report"""
    now = datetime.now()
    
    # Calculate week start/end
    week_start = now - timedelta(days=now.weekday() + (7 * week_offset))
    week_end = week_start + timedelta(days=6)
    
    # Generate daily summaries
    daily_summaries = []
    total_heart_rate = 0
    total_systolic = 0
    total_diastolic = 0
    total_spo2 = 0
    total_readings = 0
    
    for day_offset in range(7):
        day = week_start + timedelta(days=day_offset)
        readings_count = random.randint(3, 6)
        total_readings += readings_count
        
        day_hr = sum(generate_heart_rate() for _ in range(readings_count)) / readings_count
        systolic, diastolic = generate_blood_pressure()
        
        total_heart_rate += day_hr * readings_count
        total_systolic += systolic * readings_count
        total_diastolic += diastolic * readings_count
        total_spo2 += generate_spo2() * readings_count
        
        daily_summaries.append({
            "date": day.strftime("%Y-%m-%d"),
            "avg_heart_rate": round(day_hr, 1),
            "avg_bp": f"{systolic}/{diastolic}",
            "readings_count": readings_count
        })
    
    # Calculate averages
    avg_heart_rate = round(total_heart_rate / total_readings, 1) if total_readings else 0
    avg_systolic = round(total_systolic / total_readings, 1) if total_readings else 0
    avg_diastolic = round(total_diastolic / total_readings, 1) if total_readings else 0
    avg_spo2 = round(total_spo2 / total_readings, 1) if total_readings else 0
    
    # Calculate health score (0-100)
    health_score = 85  # Base score
    if avg_systolic > 130:
        health_score -= 10
    if avg_systolic > 140:
        health_score -= 15
    if avg_heart_rate > 90:
        health_score -= 5
    if avg_spo2 < 96:
        health_score -= 10
    health_score = max(0, min(100, health_score))
    
    # Generate recommendations based on data
    recommendations = []
    if avg_systolic > 130:
        recommendations.append("Tuz alımınızı azaltmayı düşünün")
    if avg_heart_rate > 85:
        recommendations.append("Düzenli kardiyovasküler egzersiz yapın")
    if avg_spo2 < 97:
        recommendations.append("Derin nefes egzersizleri yapın")
    if not recommendations:
        recommendations.append("Harika! Sağlıklı yaşam tarzınızı sürdürün")
        recommendations.append("Günde en az 30 dakika yürüyüş yapın")
    
    return {
        "week_start": week_start.strftime("%Y-%m-%d"),
        "week_end": week_end.strftime("%Y-%m-%d"),
        "avg_heart_rate": avg_heart_rate,
        "avg_systolic": avg_systolic,
        "avg_diastolic": avg_diastolic,
        "avg_spo2": avg_spo2,
        "readings_count": total_readings,
        "health_score": health_score,
        "recommendations": recommendations,
        "daily_summaries": daily_summaries
    }


def generate_notifications(user_id: str, count: int = 10) -> List[dict]:
    """
    Generate sample notification dicts that match NotificationDto in schemas/notifications.py.

    The first 4 are marked unread (is_read=False) so the mobile badge counter
    shows a non-zero value during development.
    """
    notification_templates = [
        ("alert",       "High Blood Pressure",  "Systolic reading exceeds 140 mmHg"),
        ("reminder",    "Measurement Reminder", "Don't forget your daily BP check"),
        ("achievement", "Goal Reached!",         "You completed all weekly goals"),
        ("info",        "Weekly Report",         "Your weekly health report is ready"),
        ("reminder",    "Exercise Time",         "30-minute walk goal — let's go!"),
        ("alert",       "Low SpO2",              "Oxygen saturation is below normal"),
        ("info",        "Health Tip",            "Try deep breathing to reduce stress"),
        ("achievement", "Consistency Award",     "5 consecutive days of measurements!"),
    ]

    now = datetime.now(timezone.utc)
    notifications = []

    for i in range(count):
        template = random.choice(notification_templates)
        timestamp = int((now - timedelta(hours=i * 3 + random.randint(0, 2))).timestamp() * 1000)

        notifications.append({
            "id":        str(uuid.uuid4()),
            "user_id":   user_id,
            "type":      template[0],
            "title":     template[1],
            "message":   template[2],
            "timestamp": timestamp,
            "is_read":   i > 3,   # First 4 are unread to populate the badge counter
        })

    notifications.sort(key=lambda x: x["timestamp"], reverse=True)
    return notifications


# ---------------------------------------------------------------------------
# Trends mock — returns List[TrendDataDto-compatible dicts]
# One dict per metric: systolic, diastolic, heart_rate
# ---------------------------------------------------------------------------

# Number of data points per period — controls chart density
_TREND_POINTS = {'day': 24, 'week': 28, 'month': 30}


def generate_trends(period: str = 'week') -> list:
    """
    Generate mock trend data that matches the TrendDataDto schema used by
    GET /trends.  Returns a plain list of dicts so the router can pass them
    directly to TrendDataDto(**item).

    Each dict has: type, data_points ([{timestamp, value}]), average, min, max.
    Three metrics are always returned: systolic, diastolic, heart_rate.

    _TREND_POINTS controls how many samples are generated per period so charts
    look appropriately dense without overloading the parser.
    """
    n_points = _TREND_POINTS.get(period, 28)
    now = datetime.now(timezone.utc)
    result = []

    # Each metric generator: (type_name, value_callable)
    generators = [
        ('systolic',   lambda: generate_blood_pressure()[0]),
        ('diastolic',  lambda: generate_blood_pressure()[1]),
        ('heart_rate', lambda: generate_heart_rate()),
        ('spo2',       lambda: generate_spo2()),
    ]

    for metric_type, value_fn in generators:
        points = []
        values = []

        for i in range(n_points):
            # Spread points evenly across the period window
            if period == 'day':
                ts = now - timedelta(hours=i)
            elif period == 'month':
                ts = now - timedelta(days=i)
            else:  # week — one point every 6 hours
                ts = now - timedelta(hours=i * 6)

            val = float(value_fn())
            ts_ms = int(ts.timestamp() * 1000)
            points.append({'timestamp': ts_ms, 'value': val})
            values.append(val)

        # Sort ascending so mobile charts render left-to-right correctly
        points.sort(key=lambda p: p['timestamp'])

        result.append({
            'type':        metric_type,
            'data_points': points,
            'average':     round(sum(values) / len(values), 2),
            'min':         round(min(values), 2),
            'max':         round(max(values), 2),
        })

    return result
