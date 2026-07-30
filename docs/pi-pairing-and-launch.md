# Pi Pairing And Launch

Date: 2026-07-29
Branch: `feature/pubnub-pi-prototype-hardening`

## Purpose

This document freezes the completed display-pairing milestone.

It covers:

- how an Android facilitator launches a session onto a display
- which PubNub channels are used
- what the Python `pi/` package represents
- what is intentionally not in scope yet

It does not cover participant QR joining. That work has not started on this branch.

## Components

- Android app:
  - creates draft and scheduled sessions locally
  - scans the display pairing QR
  - binds the display and launches the session
- PubNub:
  - carries display control/events
  - stores live short-code directory entries in App Context metadata
- Python `pi/` package:
  - simulator
  - protocol reference implementation
  - automated test harness
- Production Raspberry Pi software:
  - external to this repository
  - implemented separately in C++

## Channel Contract

Display-pairing uses exactly these device channels:

- `display.<displayId>.control`
- `display.<displayId>.events`

After binding, the Pi also subscribes to the session display channel:

- `session.<sessionId>.display`

The session-scoped display events channel remains:

- `session.<sessionId>.display.events`

## Launch Sequence

1. Facilitator creates a new immediate session or selects an existing draft/scheduled session.
2. Immediate session creation stores a local `DRAFT` session and host membership only.
3. `ActiveSessionStore` is not activated at draft creation time.
4. The facilitator opens the display-launch flow and scans the Pi pairing QR.
5. Android decodes `DisplayPairingPayload`.
6. Android calls `SessionRepository.launchSessionOnDisplay(...)`.
7. Android publishes `display.bind_session` to `display.<displayId>.control`.
8. The Pi validates the request and replies on `display.<displayId>.events`.
9. Android registers the chosen join code in PubNub App Context metadata.
10. Android commits the session locally as `LIVE`, persists the `displayId`, publishes session-start/member-joined realtime events, and activates `ActiveSessionStore`.
11. The facilitator enters the in-session shell only after `LaunchSessionResult.Launched`.

## Important Lifecycle Rules

- New immediate sessions remain `DRAFT`.
- Draft creation must not activate `ActiveSessionStore`.
- Scheduled creation must not activate `ActiveSessionStore`.
- `launchSessionOnDisplay(...)` is the only intended path that makes a not-yet-started session `LIVE`.
- UI code must not call `startScheduledSession()` for this flow.

## Python Versus Production Pi

The Python code under [`pi/`](../pi/README.md) is:

- a simulator for local integration work
- a reference implementation of the display-pairing protocol
- a state-machine test harness
- a compatibility target for the future production Pi client

It is not:

- a FastAPI service
- a custom backend for Android
- the production Raspberry Pi application

The production Pi software for this project is external C++ code and must remain protocol-compatible with the Android and Python artifacts defined here.

## Not Started Yet

- Participant session-join QR flow
- Shared invitation join pipeline for scanned participant QRs
- Any Android logic that treats participant QR joining as part of this milestone
