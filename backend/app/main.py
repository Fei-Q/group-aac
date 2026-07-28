from collections.abc import Callable

from fastapi import Depends, FastAPI
from sqlalchemy.orm import Session, sessionmaker

from .config import load_settings
from .database import Base, create_engine_for_url, get_db
from .models import SessionStatus
from .schemas import CloseSessionRequest, CreateSessionRequest, ResolveCodeRequest, SessionResponse, UpdateSessionRequest
from .services import (
    cancel_session,
    create_session,
    default_join_code_generator,
    end_session,
    resolve_join_code,
    to_payload,
    update_session,
)


def create_app(
    session_factory: sessionmaker[Session] | None = None,
    join_code_generator: Callable[[], str] | None = None,
) -> FastAPI:
    settings = load_settings()
    if session_factory is None:
        engine = create_engine_for_url(settings.database_url)
        Base.metadata.create_all(engine)
        session_factory = sessionmaker(
            bind=engine,
            autocommit=False,
            autoflush=False,
            expire_on_commit=False,
        )

    app = FastAPI(title="Group AAC Backend")

    def db_dependency():
        yield from get_db(session_factory)

    @app.post("/sessions", response_model=SessionResponse)
    def create_session_endpoint(
        request: CreateSessionRequest,
        db: Session = Depends(db_dependency),
    ) -> SessionResponse:
        session = create_session(
            db,
            host_uid=request.hostUid,
            name=request.name,
            status=SessionStatus(request.status),
            scheduled_start_at=request.scheduledStartAt,
            scheduled_duration_minutes=request.scheduledDurationMinutes,
            join_code_generator=join_code_generator or default_join_code_generator,
        )
        return SessionResponse(result="CREATED", session=to_payload(session))

    @app.post("/sessions/resolve-code", response_model=SessionResponse)
    def resolve_code_endpoint(
        request: ResolveCodeRequest,
        db: Session = Depends(db_dependency),
    ) -> SessionResponse:
        result, session = resolve_join_code(
            db,
            join_code=request.joinCode,
            requester_uid=request.requesterUid,
        )
        return SessionResponse(
            result=result,
            session=to_payload(session) if session else None,
        )

    @app.patch("/sessions/{session_id}", response_model=SessionResponse)
    def update_session_endpoint(
        session_id: str,
        request: UpdateSessionRequest,
        db: Session = Depends(db_dependency),
    ) -> SessionResponse:
        result, session = update_session(
            db,
            session_id=session_id,
            host_uid=request.hostUid,
            name=request.name,
            status=SessionStatus(request.status),
            scheduled_start_at=request.scheduledStartAt,
            scheduled_duration_minutes=request.scheduledDurationMinutes,
        )
        return SessionResponse(
            result=result,
            session=to_payload(session) if session else None,
        )

    @app.post("/sessions/{session_id}/end", response_model=SessionResponse)
    def end_session_endpoint(
        session_id: str,
        request: CloseSessionRequest,
        db: Session = Depends(db_dependency),
    ) -> SessionResponse:
        result, session = end_session(
            db,
            session_id=session_id,
            host_uid=request.hostUid,
        )
        return SessionResponse(
            result=result,
            session=to_payload(session) if session else None,
        )

    @app.post("/sessions/{session_id}/cancel", response_model=SessionResponse)
    def cancel_session_endpoint(
        session_id: str,
        request: CloseSessionRequest,
        db: Session = Depends(db_dependency),
    ) -> SessionResponse:
        result, session = cancel_session(
            db,
            session_id=session_id,
            host_uid=request.hostUid,
        )
        return SessionResponse(
            result=result,
            session=to_payload(session) if session else None,
        )

    return app


app = create_app()
