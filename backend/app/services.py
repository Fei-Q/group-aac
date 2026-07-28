from datetime import UTC, datetime, timedelta
from typing import Callable
from uuid import uuid4

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from .models import SessionMemberModel, SessionModel, SessionStatus, UserModel
from .schemas import SessionPayload


def normalize_join_code(raw: str) -> str:
    digits = "".join(char for char in raw if char.isdigit())
    if len(digits) != 8:
        raise ValueError("Session code must contain eight digits.")
    return f"{digits[:4]}-{digits[4:]}"


def default_join_code_generator() -> str:
    digits = str(abs(hash(uuid4())) % 100_000_000).zfill(8)
    return normalize_join_code(digits)


def _to_utc_datetime(value_millis: int | None) -> datetime | None:
    if value_millis is None:
        return None
    return datetime.fromtimestamp(value_millis / 1000, tz=UTC)


def _ensure_utc(value: datetime | None) -> datetime | None:
    if value is None:
        return None
    if value.tzinfo is None:
        return value.replace(tzinfo=UTC)
    return value.astimezone(UTC)


def _compute_expires_at(
    *,
    status: SessionStatus,
    scheduled_start_at: datetime | None,
    scheduled_duration_minutes: int | None,
    now: datetime,
) -> datetime:
    if scheduled_start_at is not None:
        minutes = scheduled_duration_minutes or 60
        return scheduled_start_at.replace(tzinfo=UTC) + timedelta(minutes=minutes)
    if status == SessionStatus.LIVE:
        return now + timedelta(hours=24)
    return now + timedelta(hours=24)


def ensure_user(db: Session, uid: str, display_name: str | None = None) -> UserModel:
    user = db.get(UserModel, uid)
    if user is None:
        user = UserModel(uid=uid, display_name=display_name or uid)
        db.add(user)
        db.flush()
    elif display_name and user.display_name != display_name:
        user.display_name = display_name
    return user


def create_session(
    db: Session,
    *,
    host_uid: str,
    name: str,
    status: SessionStatus,
    scheduled_start_at: int | None,
    scheduled_duration_minutes: int | None,
    join_code_generator: Callable[[], str] = default_join_code_generator,
) -> SessionModel:
    now = datetime.now(UTC)
    ensure_user(db, host_uid)
    session_name = name.strip() or "Group Meeting"
    scheduled_at = _to_utc_datetime(scheduled_start_at)

    for _ in range(20):
        join_code = join_code_generator()
        session = SessionModel(
            id=str(uuid4()),
            join_code=join_code,
            name=session_name,
            host_uid=host_uid,
            status=status,
            scheduled_start_at=scheduled_at,
            scheduled_duration_minutes=scheduled_duration_minutes,
            actual_started_at=now if status == SessionStatus.LIVE else None,
            actual_ended_at=None,
            created_at=now,
            updated_at=now,
            expires_at=_compute_expires_at(
                status=status,
                scheduled_start_at=scheduled_at,
                scheduled_duration_minutes=scheduled_duration_minutes,
                now=now,
            ),
        )
        member = SessionMemberModel(
            session_id=session.id,
            user_uid=host_uid,
            display_name=host_uid,
            role="HOST",
            joined_at=now,
        )
        db.add(session)
        db.add(member)
        try:
            db.commit()
            db.refresh(session)
            return session
        except IntegrityError:
            db.rollback()
            continue

    raise RuntimeError("Unable to reserve a unique join code.")


def resolve_join_code(
    db: Session,
    *,
    join_code: str,
    requester_uid: str,
    now: datetime | None = None,
) -> tuple[str, SessionModel | None]:
    del requester_uid
    current_time = now or datetime.now(UTC)
    session = (
        db.query(SessionModel)
        .filter(SessionModel.join_code == normalize_join_code(join_code))
        .one_or_none()
    )
    if session is None:
        return "NOT_FOUND", None
    if session.status == SessionStatus.CANCELLED:
        return "CANCELLED", None
    if session.status == SessionStatus.ENDED:
        return "ENDED", None
    expires_at = _ensure_utc(session.expires_at)
    if expires_at is not None and expires_at <= current_time:
        return "EXPIRED", None
    return "FOUND", session


def update_session(
    db: Session,
    *,
    session_id: str,
    host_uid: str,
    name: str,
    status: SessionStatus,
    scheduled_start_at: int | None,
    scheduled_duration_minutes: int | None,
) -> tuple[str, SessionModel | None]:
    session = db.get(SessionModel, session_id)
    if session is None:
        return "NOT_FOUND", None
    if session.host_uid != host_uid:
        return "NOT_FOUND", None
    if session.status == SessionStatus.CANCELLED:
        return "CANCELLED", None
    if session.status == SessionStatus.ENDED:
        return "ENDED", None

    now = datetime.now(UTC)
    session.name = name.strip() or session.name
    session.status = status
    session.scheduled_start_at = _to_utc_datetime(scheduled_start_at)
    session.scheduled_duration_minutes = scheduled_duration_minutes
    if status == SessionStatus.LIVE and session.actual_started_at is None:
        session.actual_started_at = now
    session.updated_at = now
    session.expires_at = _compute_expires_at(
        status=status,
        scheduled_start_at=session.scheduled_start_at,
        scheduled_duration_minutes=scheduled_duration_minutes,
        now=now,
    )
    db.commit()
    db.refresh(session)
    return "UPDATED", session


def end_session(
    db: Session,
    *,
    session_id: str,
    host_uid: str,
) -> tuple[str, SessionModel | None]:
    session = db.get(SessionModel, session_id)
    if session is None or session.host_uid != host_uid:
        return "NOT_FOUND", None
    if session.status == SessionStatus.CANCELLED:
        return "CANCELLED", None
    if session.status == SessionStatus.ENDED:
        return "ALREADY_ENDED", None
    session.status = SessionStatus.ENDED
    session.actual_ended_at = datetime.now(UTC)
    session.updated_at = session.actual_ended_at
    db.commit()
    db.refresh(session)
    return "ENDED", session


def cancel_session(
    db: Session,
    *,
    session_id: str,
    host_uid: str,
) -> tuple[str, SessionModel | None]:
    session = db.get(SessionModel, session_id)
    if session is None or session.host_uid != host_uid:
        return "NOT_FOUND", None
    if session.status == SessionStatus.ENDED:
        return "ENDED", None
    if session.status == SessionStatus.CANCELLED:
        return "ALREADY_CANCELLED", None
    session.status = SessionStatus.CANCELLED
    session.updated_at = datetime.now(UTC)
    db.commit()
    db.refresh(session)
    return "CANCELLED", session


def to_payload(session: SessionModel) -> SessionPayload:
    from .schemas import datetime_to_millis

    return SessionPayload(
        sessionId=session.id,
        joinCode=session.join_code,
        sessionName=session.name,
        hostUid=session.host_uid,
        status=session.status.value,
        scheduledStartAt=datetime_to_millis(session.scheduled_start_at),
        scheduledDurationMinutes=session.scheduled_duration_minutes,
        actualStartedAt=datetime_to_millis(session.actual_started_at),
        actualEndedAt=datetime_to_millis(session.actual_ended_at),
        expiresAt=datetime_to_millis(session.expires_at),
    )
