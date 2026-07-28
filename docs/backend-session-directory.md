# Backend Session Directory

## Purpose

The backend is the authoritative source for:

- session IDs
- eight-digit join codes
- lifecycle status
- expiry decisions

Android talks to it through the `SessionDirectory` abstraction instead of resolving sessions directly from local Room state.

## Android Integration

- interface: [SessionDirectory](../app/src/main/java/com/example/groupaac/data/sessiondirectory/SessionDirectory.kt)
- remote implementation: [RemoteSessionDirectory](../app/src/main/java/com/example/groupaac/data/sessiondirectory/RemoteSessionDirectory.kt)
- HTTP transport: [HttpGroupAacApi](../app/src/main/java/com/example/groupaac/data/sessiondirectory/HttpGroupAacApi.kt)
- DI wiring: [AppContainer](../app/src/main/java/com/example/groupaac/AppContainer.kt)

Android runtime base URL comes from `GROUP_AAC_SESSION_DIRECTORY_BASE_URL` and is exposed to app code as `BuildConfig.SESSION_DIRECTORY_BASE_URL` in [app/build.gradle.kts](../app/build.gradle.kts).

Default local emulator value:

```text
http://10.0.2.2:8000
```

## Backend Files

- app entrypoint: [backend/app/main.py](../backend/app/main.py)
- configuration: [backend/app/config.py](../backend/app/config.py)
- database plumbing: [backend/app/database.py](../backend/app/database.py)
- ORM models: [backend/app/models.py](../backend/app/models.py)
- request/response schemas: [backend/app/schemas.py](../backend/app/schemas.py)
- service logic: [backend/app/services.py](../backend/app/services.py)

## Environment Variables

- `GROUP_AAC_DATABASE_URL`
  - runtime database URL
  - default: `sqlite:///./group_aac_backend.db`
  - intended production shape: PostgreSQL-compatible SQLAlchemy URL

Local development example:

```bash
export GROUP_AAC_DATABASE_URL=sqlite:///./group_aac_backend.db
```

PostgreSQL-compatible example:

```bash
export GROUP_AAC_DATABASE_URL=postgresql+psycopg://groupaac:password@localhost:5432/groupaac
```

## Schema

Tables defined in [backend/app/models.py](../backend/app/models.py):

- `users`
  - primary key: `uid`
- `sessions`
  - primary key: `id`
  - unique join code constraint: `uq_sessions_join_code`
  - lifecycle status enum:
    - `SCHEDULED`
    - `LIVE`
    - `ENDED`
    - `CANCELLED`
- `session_members`
  - composite primary key: `(session_id, user_uid)`
- `facilitator_requests`
  - status enum:
    - `PENDING`
    - `APPROVED`
    - `DECLINED`
    - `CANCELLED`

## Endpoints

Implemented in [backend/app/main.py](../backend/app/main.py):

- `POST /sessions`
  - creates authoritative session ID and join code
- `POST /sessions/resolve-code`
  - resolves an eight-digit join code
  - returns explicit `FOUND`, `NOT_FOUND`, `EXPIRED`, `ENDED`, or `CANCELLED`
- `PATCH /sessions/{sessionId}`
  - updates scheduled/live session fields
- `POST /sessions/{sessionId}/end`
  - marks the session ended
- `POST /sessions/{sessionId}/cancel`
  - marks the session cancelled

## Create / Resolve / Update Rules

- Join codes are normalized to `1234-5678` form in [normalize_join_code](../backend/app/services.py).
- Session creation retries up to `20` times on unique-constraint collisions.
- The host is inserted into `session_members` transactionally with the new session shell.
- Resolve returns terminal states explicitly rather than pretending the session is missing.
- Expiry is computed from scheduled timing when available, otherwise from a default live/session window.

## Local Startup

From repo root:

```bash
cd backend
python3 -m uvicorn backend.app.main:app --host 0.0.0.0 --port 8000 --reload
```

Android emulator traffic reaches that server through `http://10.0.2.2:8000`.

## Tests

Backend tests live in:

- [backend/tests/test_sessions_api.py](../backend/tests/test_sessions_api.py)
- [backend/tests/test_pi_test_consumer.py](../backend/tests/test_pi_test_consumer.py)

Current verified command:

```bash
cd backend
python3 -m pytest
```

Current result on 2026-07-28: `8 passed`.

## Deferred Work

- production authentication
- PubNub token issuance
- backend-side event authorization
- persistent migrations beyond the current lightweight bootstrap approach
