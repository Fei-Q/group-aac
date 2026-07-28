# End-To-End Test Procedure

Date: 2026-07-28

## Prerequisites

- branch: `feature/pubnub-live-integration`
- local backend available
- valid `pubnub.properties` at repo root
- one of:
  - two emulators
  - one emulator plus one physical Android device
- optional:
  - Python Pi test consumer wrapper around [backend/pi_test_consumer.py](../backend/pi_test_consumer.py)
  - physical Raspberry Pi implementation that follows [Pi Display Protocol Contract](./pi-display-protocol-contract.md)

## Required Local Config

### PubNub

Create repo-root `pubnub.properties`:

```properties
PUBNUB_PUBLISH_KEY=pub-c-...
PUBNUB_SUBSCRIBE_KEY=sub-c-...
```

Do not add a secret key to Android.

### Backend

Recommended local defaults:

```bash
export GROUP_AAC_DATABASE_URL=sqlite:///./group_aac_backend.db
```

The Android app defaults `BuildConfig.SESSION_DIRECTORY_BASE_URL` to:

```text
http://10.0.2.2:8000
```

Override with `GROUP_AAC_SESSION_DIRECTORY_BASE_URL` before Gradle build if needed.

## Start The Backend

```bash
cd backend
python3 -m uvicorn backend.app.main:app --host 0.0.0.0 --port 8000 --reload
```

## Optional Pi Harness

The repo currently provides a deterministic protocol model, not a live PubNub subscriber:

- implementation: [backend/pi_test_consumer.py](../backend/pi_test_consumer.py)
- tests: [backend/tests/test_pi_test_consumer.py](../backend/tests/test_pi_test_consumer.py)
- fixtures: [pi-display-protocol-fixtures.json](./pi-display-protocol-fixtures.json)

To run a true vertical slice, wrap that consumer in a small live PubNub subscriber/publisher or replace it with a physical Pi implementation.

## Android Verification Commands

Run from repo root:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest --stacktrace
cd backend && python3 -m pytest
```

Current verified outcomes on 2026-07-28:

- `assembleDebug`: passed
- `testDebugUnitTest`: passed (`91` tests, `0` failures)
- `connectedDebugAndroidTest`: passed
  - smoke test executed
  - Compose interaction suite skipped on current Android 17 preview emulator
- `pytest`: passed (`8` tests)

## Two-Client Vertical Slice

### 1. Prepare Two Clients

- launch emulator A
- launch emulator B or connect one physical device
- install the debug build on both
- verify both can reach the backend

### 2. Create Host Account

On client A:

1. create a host/facilitator account
2. choose advanced home experience
3. confirm the app reaches the home screen

### 3. Create Live Session

On client A:

1. create a live session
2. capture:
   - session ID
   - join code
   - active display mode

Expected:

- backend creates authoritative session shell
- host membership is stored locally
- host realtime subscriptions start

### 4. Join As Participant

On client B:

1. create a participant account
2. join with the host’s join code

Expected:

- backend resolves the join code
- Room persists the returned session shell before activation
- participant subscribes to public and private-user channels

### 5. Join As Facilitator Requester

Optional second non-host facilitator flow:

1. request facilitator access from client B or a third client
2. approve from host
3. verify requester activates from private `facilitator.approved` without waiting for public `member.joined`

### 6. Message Flow

On client B:

1. send a group message

Expected:

- message is created locally as `PENDING`
- outbox row is inserted transactionally
- publish succeeds
- transport status changes to `SENT`
- host receives `message.created`

### 7. Signal Flow

On participant client:

1. create a signal

Expected:

- host/facilitator receives `aac.signal.created`

Then:

1. snooze from facilitator client A
2. verify that a different facilitator still sees the signal unsnoozed

### 8. Display Flow

On host/facilitator client A:

1. use Show on the group message
2. Pin it
3. send another group message from client B
4. verify automatic replacement is rejected while pinned
5. Unpin
6. Clear

Expected:

- commands publish on `session.<sessionId>.display`
- acknowledgements publish on `session.<sessionId>.display.events`
- Room display state updates only from fresh acknowledgements

### 9. Session Lifecycle

On host:

1. end the session

Expected:

- backend marks it `ENDED`
- Android publishes `session.ended`
- facilitators can leave but must not see End Session unless they are the host

## Debug Console Setup

PubNub Debug Console can be used to inspect message flow manually:

1. open the PubNub admin/debug console in a browser
2. load the configured publish and subscribe keys from `pubnub.properties`
3. subscribe to channels such as:
   - `session.<sessionId>.public`
   - `session.<sessionId>.facilitator`
   - `session.<sessionId>.<userId>`
   - `session.<sessionId>.display`
   - `session.<sessionId>.display.events`
4. confirm canonical payloads match Android fixtures and code

## Current Blockers

- The repository does not yet contain a live PubNub-backed Pi process, only the deterministic Python consumer model.
- The current Android 17 preview emulator skips the richer Compose interaction suite because of a platform/test-rule incompatibility.
- Full production authentication and PubNub Access Manager token flows are deferred.
