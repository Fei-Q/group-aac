from datetime import datetime
from pydantic import BaseModel


class CreateSessionRequest(BaseModel):
    hostUid: str
    name: str
    status: str
    scheduledStartAt: int | None = None
    scheduledDurationMinutes: int | None = None


class ResolveCodeRequest(BaseModel):
    joinCode: str
    requesterUid: str


class UpdateSessionRequest(BaseModel):
    hostUid: str
    name: str
    status: str
    scheduledStartAt: int | None = None
    scheduledDurationMinutes: int | None = None


class CloseSessionRequest(BaseModel):
    hostUid: str


class SessionPayload(BaseModel):
    sessionId: str
    joinCode: str
    sessionName: str
    hostUid: str
    status: str
    scheduledStartAt: int | None = None
    scheduledDurationMinutes: int | None = None
    actualStartedAt: int | None = None
    actualEndedAt: int | None = None
    expiresAt: int | None = None


class SessionResponse(BaseModel):
    result: str
    session: SessionPayload | None = None
    message: str | None = None


def datetime_to_millis(value: datetime | None) -> int | None:
    if value is None:
        return None
    return int(value.timestamp() * 1000)
