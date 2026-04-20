from typing import Optional

from sqlalchemy import func, or_
from sqlalchemy.orm import Session

from backend.models.user import User


def canonicalize_sensor_user_key(value: Optional[str]) -> str:
    """Normalize a sensor owner key so email-based ownership stays stable."""
    return (value or "").strip().lower()


def canonical_sensor_user_key(user: User) -> str:
    """Use normalized email as the canonical sensor owner key."""
    return canonicalize_sensor_user_key(user.email)


def sensor_user_clause(column: str = "user_id") -> str:
    return (
        f"({column} = :user_id OR {column} = :user_email_raw "
        f"OR LOWER({column}) = :user_email_normalized)"
    )


def sensor_user_params(user: User) -> dict[str, str]:
    return {
        "user_id": user.id,
        "user_email_raw": user.email,
        "user_email_normalized": canonical_sensor_user_key(user),
    }


def resolve_user_by_sensor_key(db: Session, sensor_key: Optional[str]) -> Optional[User]:
    """Resolve a sensor row owner key that may be either UUID or email."""
    raw_key = (sensor_key or "").strip()
    normalized_key = canonicalize_sensor_user_key(sensor_key)
    if not raw_key:
        return None

    return (
        db.query(User)
        .filter(
            or_(
                User.id == raw_key,
                func.lower(User.email) == normalized_key,
            )
        )
        .first()
    )
