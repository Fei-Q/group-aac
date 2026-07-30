# End-To-End Test Procedure

Date: 2026-07-29

## Prerequisites

- branch: `feature/pubnub-pi-prototype-hardening`
- valid `pubnub.properties` at repo root
- one of:
  - two emulators
  - one emulator plus one physical Android device
- optional:
  - Python Pi runtime in [`pi/`](../pi/README.md)
  - physical Raspberry Pi implementation that follows [Pi Display Protocol Contract](./pi-display-protocol-contract.md)

## Required Local Config

### PubNub

Create repo-root `pubnub.properties`:

```properties
PUBNUB_PUBLISH_KEY=pub-c-...
PUBNUB_SUBSCRIBE_KEY=sub-c-...
```

Do not add a secret key to Android.

## Optional Pi Harness

The repo currently provides a Python Pi runtime and simulator rather than production Pi software:

- implementation: [`pi/group_aac_pi/runtime.py`](../pi/group_aac_pi/runtime.py)
- tests: [`pi/tests/test_state_machine.py`](../pi/tests/test_state_machine.py)
- fixtures: [pi-display-protocol-fixtures.json](./pi-display-protocol-fixtures.json)

To run a true vertical slice, use the Python runtime with valid PubNub keys or replace it with a physical Pi implementation.

## Android Verification Commands

Run from repo root:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest --stacktrace
cd pi && source .venv/bin/activate && python -m pytest tests -q
```

Current verified outcomes on 2026-07-29:

- `assembleDebug`: passed
- `testDebugUnitTest`: passed (`129` tests, `0` failures)
- `connectedDebugAndroidTest`: not rerun as part of the Stage 4B prompt
  - smoke test executed
  - Compose interaction suite skipped on current Android 17 preview emulator
- `pytest`: passed (`12` tests)

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

### 3. Create And Launch A Session

On client A:

1. create a new session
2. scan the display pairing QR from the Pi or simulator
3. wait for the session preview / launch to complete
4. capture:
   - session ID
   - join code
   - display ID

Expected:

- Android creates a local draft session first
- display launch transitions the session to `LIVE`
- join code registration happens through PubNub App Context
- host membership is stored locally
- host realtime subscriptions start

### 4. Join As Participant

On client B:

1. create a participant account
2. enter fewer than eight digits and verify no preview appears yet
3. enter the full eight-digit code and wait for the preview
4. verify the preview shows session name, code, start label, and display identity
5. tap Join session

Expected:

- PubNub App Context resolves the join code
- Room persists the returned session shell before activation
- participant subscribes to public and private-user channels

### 5. Join From Participant QR

On client B:

1. return to the join screen
2. tap Scan QR code
3. scan a `group-aac-session` invitation QR
4. verify the same preview appears before joining
5. tap Join session

Expected:

- QR validation does not call `SessionDirectory`
- the same shared invitation pipeline is used after confirmation
- resulting local session and membership state matches the manual-code path

### 6. Join As Facilitator Requester

Optional second non-host facilitator flow:

1. request facilitator access from client B or a third client using either manual code or participant QR preview
2. approve from host
3. verify requester activates from private `facilitator.approved` without waiting for public `member.joined`

### 7. Message Flow

On client B:

1. send a group message

Expected:

- message is created locally as `PENDING`
- outbox row is inserted transactionally
- publish succeeds
- transport status changes to `SENT`
- host receives `message.created`

### 8. Signal Flow

On participant client:

1. create a signal

Expected:

- host/facilitator receives `aac.signal.created`

Then:

1. snooze from facilitator client A
2. verify that a different facilitator still sees the signal unsnoozed

### 9. Display Flow

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

### 10. Session Lifecycle

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

- The repository still does not contain the production C++ Pi implementation; the Python runtime is a simulator/reference.
- The current Android 17 preview emulator skips the richer Compose interaction suite because of a platform/test-rule incompatibility.
- Full production authentication and PubNub Access Manager token flows are deferred.
