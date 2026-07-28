from datetime import datetime
from enum import StrEnum
from uuid import uuid4

from sqlalchemy import DateTime, Enum, ForeignKey, Integer, PrimaryKeyConstraint, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base


class SessionStatus(StrEnum):
    SCHEDULED = "SCHEDULED"
    LIVE = "LIVE"
    ENDED = "ENDED"
    CANCELLED = "CANCELLED"


class FacilitatorRequestStatus(StrEnum):
    PENDING = "PENDING"
    APPROVED = "APPROVED"
    DECLINED = "DECLINED"
    CANCELLED = "CANCELLED"


class UserModel(Base):
    __tablename__ = "users"

    uid: Mapped[str] = mapped_column(String(24), primary_key=True)
    display_name: Mapped[str] = mapped_column(String(120), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=datetime.utcnow,
    )


class SessionModel(Base):
    __tablename__ = "sessions"
    __table_args__ = (
        UniqueConstraint("join_code", name="uq_sessions_join_code"),
    )

    id: Mapped[str] = mapped_column(
        String(64),
        primary_key=True,
        default=lambda: str(uuid4()),
    )
    join_code: Mapped[str] = mapped_column(String(9), nullable=False)
    name: Mapped[str] = mapped_column(String(160), nullable=False)
    host_uid: Mapped[str] = mapped_column(ForeignKey("users.uid"), nullable=False)
    status: Mapped[SessionStatus] = mapped_column(
        Enum(SessionStatus),
        nullable=False,
    )
    scheduled_start_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    scheduled_duration_minutes: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
    )
    actual_started_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    actual_ended_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=datetime.utcnow,
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
    )
    expires_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    host: Mapped[UserModel] = relationship()


class SessionMemberModel(Base):
    __tablename__ = "session_members"
    __table_args__ = (
        PrimaryKeyConstraint("session_id", "user_uid", name="pk_session_members"),
    )

    session_id: Mapped[str] = mapped_column(ForeignKey("sessions.id"), nullable=False)
    user_uid: Mapped[str] = mapped_column(ForeignKey("users.uid"), nullable=False)
    display_name: Mapped[str] = mapped_column(String(120), nullable=False)
    role: Mapped[str] = mapped_column(String(32), nullable=False)
    joined_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=datetime.utcnow,
    )


class FacilitatorRequestModel(Base):
    __tablename__ = "facilitator_requests"

    id: Mapped[str] = mapped_column(
        String(64),
        primary_key=True,
        default=lambda: str(uuid4()),
    )
    session_id: Mapped[str] = mapped_column(ForeignKey("sessions.id"), nullable=False)
    requester_uid: Mapped[str] = mapped_column(ForeignKey("users.uid"), nullable=False)
    display_name: Mapped[str] = mapped_column(String(120), nullable=False)
    status: Mapped[FacilitatorRequestStatus] = mapped_column(
        Enum(FacilitatorRequestStatus),
        nullable=False,
        default=FacilitatorRequestStatus.PENDING,
    )
    requested_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=datetime.utcnow,
    )
    decided_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    decided_by_uid: Mapped[str | None] = mapped_column(
        ForeignKey("users.uid"),
        nullable=True,
    )
