from backend.models.user import User


def sensor_user_clause(column: str = "user_id") -> str:
    return f"({column} = :user_id OR {column} = :user_email)"


def sensor_user_params(user: User) -> dict[str, str]:
    return {
        "user_id": user.id,
        "user_email": user.email,
    }
