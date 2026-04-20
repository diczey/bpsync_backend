import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy import text
from sqlalchemy.orm import Session

from backend.models.notification import Notification
from backend.models.user import User
from backend.utils.sensor_identity import sensor_user_clause, sensor_user_params

logger = logging.getLogger(__name__)

WEEKLY_REPORT_TITLE = "Weekly report ready"
WEEKLY_REPORT_MESSAGE = (
    "Your weekly health report is ready. Open Settings > Health Reports to review the last 7 days."
)
WEEKLY_REPORT_TYPE = "report"
WEEKLY_REPORT_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000


def ensure_weekly_report_notification(
    current_user: User,
    user_db: Session,
    sensor_db: Session,
) -> None:
    """
    Lazily create one in-app notification when a weekly report becomes available.

    We do this on normal app traffic instead of a background scheduler so the
    notification inbox stays useful even in local/dev environments.
    """
    if not current_user.weekly_reports_enabled or not current_user.push_notifications_enabled:
        return

    try:
        latest_row = sensor_db.execute(
            text(
                f"""
                SELECT MAX(time) AS latest_time
                FROM bp_readings
                WHERE {sensor_user_clause()}
                """
            ),
            sensor_user_params(current_user),
        ).first()

        latest_time = latest_row.latest_time if latest_row else None
        if latest_time is None:
            return

        if latest_time.tzinfo is None:
            latest_time = latest_time.replace(tzinfo=timezone.utc)
        else:
            latest_time = latest_time.astimezone(timezone.utc)

        seven_days_ago = latest_time - timedelta(days=6)
        window_stats = sensor_db.execute(
            text(
                f"""
                SELECT MIN(time) AS first_time, COUNT(*) AS reading_count
                FROM bp_readings
                WHERE {sensor_user_clause()}
                  AND time >= :window_start
                  AND time <= :window_end
                """
            ),
            {
                **sensor_user_params(current_user),
                "window_start": seven_days_ago,
                "window_end": latest_time,
            },
        ).first()

        if not window_stats or not window_stats.reading_count:
            return

        first_time = window_stats.first_time
        if first_time is None:
            return

        if first_time.tzinfo is None:
            first_time = first_time.replace(tzinfo=timezone.utc)
        else:
            first_time = first_time.astimezone(timezone.utc)

        if first_time > seven_days_ago:
            return

        latest_existing = (
            user_db.query(Notification)
            .filter(
                Notification.user_id == current_user.id,
                Notification.type == WEEKLY_REPORT_TYPE,
                Notification.title == WEEKLY_REPORT_TITLE,
            )
            .order_by(Notification.timestamp.desc())
            .first()
        )

        if latest_existing and (int(latest_time.timestamp() * 1000) - latest_existing.timestamp) < WEEKLY_REPORT_COOLDOWN_MS:
            return

        user_db.add(
            Notification(
                user_id=current_user.id,
                title=WEEKLY_REPORT_TITLE,
                message=WEEKLY_REPORT_MESSAGE,
                type=WEEKLY_REPORT_TYPE,
                timestamp=int(datetime.now(timezone.utc).timestamp() * 1000),
                is_read=False,
            )
        )
        user_db.commit()
    except Exception as exc:
        user_db.rollback()
        logger.warning("Could not create weekly report notification for %s: %s", current_user.id, exc)
