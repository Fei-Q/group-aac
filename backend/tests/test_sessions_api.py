from datetime import UTC, datetime, timedelta

from sqlalchemy.orm import Session, sessionmaker

from backend.app.models import SessionModel, SessionStatus, UserModel


def test_create_and_resolve_live_session(client):
    create = client.post(
        "/sessions",
        json={
            "hostUid": "host_1",
            "name": "Tuesday Group",
            "status": "LIVE",
            "scheduledStartAt": None,
            "scheduledDurationMinutes": None,
        },
    )
    assert create.status_code == 200
    created = create.json()
    assert created["result"] == "CREATED"
    assert created["session"]["sessionId"]
    assert created["session"]["joinCode"].count("-") == 1

    resolved = client.post(
        "/sessions/resolve-code",
        json={
            "joinCode": created["session"]["joinCode"].replace("-", ""),
            "requesterUid": "participant_1",
        },
    )
    assert resolved.status_code == 200
    payload = resolved.json()
    assert payload["result"] == "FOUND"
    assert payload["session"]["sessionId"] == created["session"]["sessionId"]
    assert payload["session"]["status"] == "LIVE"


def test_duplicate_code_collision_retries_atomically(session_factory: sessionmaker[Session]):
    counter = {"calls": 0}

    def code_generator():
        counter["calls"] += 1
        return "1111-1111" if counter["calls"] < 3 else "2222-2222"

    from fastapi.testclient import TestClient
    from backend.app.main import create_app

    app = create_app(session_factory=session_factory, join_code_generator=code_generator)
    client = TestClient(app)

    first = client.post(
        "/sessions",
        json={
            "hostUid": "host_1",
            "name": "First",
            "status": "LIVE",
            "scheduledStartAt": None,
            "scheduledDurationMinutes": None,
        },
    )
    second = client.post(
        "/sessions",
        json={
            "hostUid": "host_2",
            "name": "Second",
            "status": "LIVE",
            "scheduledStartAt": None,
            "scheduledDurationMinutes": None,
        },
    )

    assert first.json()["session"]["joinCode"] == "1111-1111"
    assert second.json()["session"]["joinCode"] == "2222-2222"
    assert counter["calls"] >= 3


def test_resolve_returns_missing_ended_cancelled_and_expired(client, session_factory: sessionmaker[Session]):
    with session_factory() as db:
        _seed_user(db, "host_1")
        now = datetime.now(UTC)
        db.add_all(
            [
                SessionModel(
                    id="ended",
                    join_code="3333-3333",
                    name="Ended",
                    host_uid="host_1",
                    status=SessionStatus.ENDED,
                    created_at=now,
                    updated_at=now,
                    actual_started_at=now - timedelta(hours=1),
                    actual_ended_at=now,
                    expires_at=now + timedelta(hours=1),
                ),
                SessionModel(
                    id="cancelled",
                    join_code="4444-4444",
                    name="Cancelled",
                    host_uid="host_1",
                    status=SessionStatus.CANCELLED,
                    created_at=now,
                    updated_at=now,
                    expires_at=now + timedelta(hours=1),
                ),
                SessionModel(
                    id="expired",
                    join_code="5555-5555",
                    name="Expired",
                    host_uid="host_1",
                    status=SessionStatus.SCHEDULED,
                    created_at=now - timedelta(days=1),
                    updated_at=now - timedelta(days=1),
                    scheduled_start_at=now - timedelta(hours=2),
                    scheduled_duration_minutes=30,
                    expires_at=now - timedelta(minutes=1),
                ),
            ]
        )
        db.commit()

    missing = client.post(
        "/sessions/resolve-code",
        json={"joinCode": "99999999", "requesterUid": "alice"},
    )
    ended = client.post(
        "/sessions/resolve-code",
        json={"joinCode": "33333333", "requesterUid": "alice"},
    )
    cancelled = client.post(
        "/sessions/resolve-code",
        json={"joinCode": "44444444", "requesterUid": "alice"},
    )
    expired = client.post(
        "/sessions/resolve-code",
        json={"joinCode": "55555555", "requesterUid": "alice"},
    )

    assert missing.json()["result"] == "NOT_FOUND"
    assert ended.json()["result"] == "ENDED"
    assert cancelled.json()["result"] == "CANCELLED"
    assert expired.json()["result"] == "EXPIRED"


def test_update_end_and_cancel_endpoints(client):
    created = client.post(
        "/sessions",
        json={
            "hostUid": "host_1",
            "name": "Planning",
            "status": "SCHEDULED",
            "scheduledStartAt": 1_800_000_000_000,
            "scheduledDurationMinutes": 60,
        },
    ).json()["session"]
    session_id = created["sessionId"]

    updated = client.patch(
        f"/sessions/{session_id}",
        json={
            "hostUid": "host_1",
            "name": "Planning Updated",
            "status": "LIVE",
            "scheduledStartAt": 1_800_000_000_000,
            "scheduledDurationMinutes": 90,
        },
    )
    assert updated.json()["result"] == "UPDATED"
    assert updated.json()["session"]["status"] == "LIVE"

    ended = client.post(
        f"/sessions/{session_id}/end",
        json={"hostUid": "host_1"},
    )
    assert ended.json()["result"] == "ENDED"

    cancelled = client.post(
        f"/sessions/{session_id}/cancel",
        json={"hostUid": "host_1"},
    )
    assert cancelled.json()["result"] == "ENDED"


def _seed_user(db: Session, uid: str) -> None:
    db.add(UserModel(uid=uid, display_name=uid))
    db.flush()
