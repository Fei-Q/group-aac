# Live Realtime Integration Status

Date: 2026-07-28
Branch: `feature/pubnub-live-integration`
Status: stages 1-10 complete with documented integration limits

## Stage Commits

| Stage | Commit | Summary |
| --- | --- | --- |
| 1 | `db730b3` | Stage 1 foundation cleanup |
| 2 | `0a44e87` | Authoritative session directory |
| 3 | `5d504c5` | Live PubNub transport |
| 4 | `216270b` | Runtime session subscriptions |
| 5 | `6921bd3` | Durable realtime delivery |
| 6 | `3950a10` | Cross-device facilitator approval |
| 7 | `89f1dc9` | Realtime session events |
| 8 | `6ded9d6` | Shared display semantics |
| 9 | `32f4497` | End-to-end verification |
| 10 | `this commit` | Documentation handoff and operator runbook |

## Current Architecture

- Android app wiring lives in [AppContainer](../app/src/main/java/com/example/groupaac/AppContainer.kt).
- Room is the UI source of truth through [AppDatabase](../app/src/main/java/com/example/groupaac/data/AppDatabase.kt), the repository layer, and realtime apply logic in [DefaultSessionRealtimeSync](../app/src/main/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSync.kt).
- Session creation and join-code lookup use the backend `SessionDirectory` boundary:
  - Android interface: [SessionDirectory](../app/src/main/java/com/example/groupaac/data/sessiondirectory/SessionDirectory.kt)
  - Remote adapter: [RemoteSessionDirectory](../app/src/main/java/com/example/groupaac/data/sessiondirectory/RemoteSessionDirectory.kt)
  - HTTP transport: [HttpGroupAacApi](../app/src/main/java/com/example/groupaac/data/sessiondirectory/HttpGroupAacApi.kt)
  - Backend app: [backend/app/main.py](../backend/app/main.py)
- PubNub transport is active per signed-in UID:
  - config: [PubNubRuntimeConfig](../app/src/main/java/com/example/groupaac/data/realtime/PubNubRuntimeConfig.kt)
  - client manager: [RealtimeClientManager](../app/src/main/java/com/example/groupaac/data/realtime/RealtimeClientManager.kt)
  - live client: [PubNubSessionRealtimeClient](../app/src/main/java/com/example/groupaac/data/realtime/PubNubSessionRealtimeClient.kt)
  - startup activation: [RealtimeStartupInitializer](../app/src/main/java/com/example/groupaac/data/realtime/RealtimeStartupInitializer.kt)
- Session-scoped subscriptions and replay are coordinated in [SessionSubscriptionCoordinator](../app/src/main/java/com/example/groupaac/data/realtime/SessionSubscriptionCoordinator.kt).
- Local-first durable publish happens through:
  - outbox persistence: [RealtimeReliabilityStore](../app/src/main/java/com/example/groupaac/data/realtime/reliability/RealtimeReliabilityStore.kt)
  - dispatcher: [OutboxDispatcher](../app/src/main/java/com/example/groupaac/data/realtime/reliability/OutboxDispatcher.kt)
  - canonical publish/apply: [DefaultSessionRealtimeSync](../app/src/main/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSync.kt)
- Shared display command and acknowledgement rules are documented in:
  - [Pi Display Protocol Contract](./pi-display-protocol-contract.md)
  - [Pi Display Fixtures](./pi-display-protocol-fixtures.json)
  - [Outbox And Recovery](./outbox-and-recovery.md)

## Data Flow

1. Account creation inserts `UserEntity` and `UserSettingsEntity` transactionally through [UserIdRegistry](../app/src/main/java/com/example/groupaac/data/account/UserIdRegistry.kt).
2. `AccountRepository` activates the realtime client before updating the persisted active user, so client activation failure does not silently switch the session state.
3. Session creation, scheduling, update, end, cancel, and resolve-code all pass through the backend `SessionDirectory`.
4. Domain mutations and outgoing realtime events are written in one Room transaction.
5. `OutboxDispatcher` publishes after commit, updates transport/display state, and retries failed rows with bounded backoff.
6. `SessionSubscriptionCoordinator` subscribes by role, replays channel history after the stored cursor, validates channel/session scope, and forwards accepted events into `applyIncoming`.
7. `DefaultSessionRealtimeSync.applyIncoming(...)` updates Room and records processed-event plus cursor state transactionally.
8. UI flows read Room-backed repositories rather than transport callbacks directly.

## Final Verification

Executed on Tuesday, July 28, 2026:

- `./gradlew :app:assembleDebug`
  - Result: `BUILD SUCCESSFUL`
- `./gradlew :app:testDebugUnitTest`
  - Result: `BUILD SUCCESSFUL`
  - XML summary under `app/build/test-results/testDebugUnitTest`: `91` tests, `0` failures, `0` skipped
- `./gradlew :app:connectedDebugAndroidTest --stacktrace`
  - Result: `BUILD SUCCESSFUL`
  - Emulator/device: `Resizable_Experimental(AVD) - 17` on `emulator-5554`
  - XML summary under `app/build/outputs/androidTest-results/connected/debug/TEST-Resizable_Experimental(AVD) - 17-_app-.xml`: `2` tests, `0` failures, `0` errors, `1` skipped
  - Executed smoke path: [MainActivitySmokeTest](../app/src/androidTest/java/com/example/groupaac/MainActivitySmokeTest.kt)
  - Skipped: [RealtimeComposeFlowsTest](../app/src/androidTest/java/com/example/groupaac/RealtimeComposeFlowsTest.kt) due to preview-emulator incompatibility with Compose test-rule input lookup
- `python3 -m pytest`
  - Result: `8 passed`
  - Warnings:
    - upstream FastAPI/Starlette `TestClient` deprecation warning
    - SQLAlchemy `datetime.utcnow()` deprecation warning in backend model defaults

## Successful Flows

- Local account creation with normalized UID validation and realtime activation boundary
- Backend-backed session creation and join-code resolution
- Host/facilitator/participant session subscriptions with replay after cursor
- Durable outbox publication for messages, session events, facilitator events, signals, and display commands
- Private `facilitator.approved` activation path that does not depend on public roster timing
- Realtime signal create/snooze/clear routing with facilitator-specific snooze isolation
- Shared display mode change, pin, unpin, clear, and stale acknowledgement rejection
- Deterministic Pi consumer command/acknowledgement simulation through [backend/pi_test_consumer.py](../backend/pi_test_consumer.py)

## Unresolved Issues

### High

- Two-client live vertical slice is still unverified in this workspace.
  - Impact: no confirmed end-to-end proof yet for backend session creation -> PubNub delivery -> second Android client -> Pi acknowledgement loop on real devices.
  - Recommended next action: run [End-To-End Test Procedure](./end-to-end-test-procedure.md) with two Android clients, valid PubNub keys, and the Pi consumer or a physical Pi.

### Medium

- [RealtimeComposeFlowsTest](../app/src/androidTest/java/com/example/groupaac/RealtimeComposeFlowsTest.kt) is intentionally skipped on the current Android 17 preview emulator.
  - Impact: richer connected UI scenarios are present but not executable on this preview image.
  - Recommended next action: rerun on a stable emulator/device image where `android.hardware.input.InputManager.getInstance` is available to Compose test infrastructure.

### Medium

- The Python Pi consumer is deterministic and protocol-oriented, not a real PubNub subscriber yet.
  - Impact: Pi protocol coverage is strong, but device/network integration remains manual.
  - Recommended next action: add a small live PubNub wrapper around [backend/pi_test_consumer.py](../backend/pi_test_consumer.py) or replace it with physical Pi code.

### Low

- Backend warnings remain in test output.
  - Impact: no failing behavior today.
  - Recommended next action: migrate backend default timestamps to timezone-aware `datetime.now(UTC)` and update the FastAPI client test stack once compatible versions are selected.

## Deferred Deployment And Security Work

- Production authentication for Android -> backend requests
- PubNub Access Manager token issuance and token refresh
- Full event authorization and channel policy enforcement
- Physical Pi device enrollment and binding lifecycle
- Full accessibility touch-target audit across live in-session flows

## Companion Docs

- [Backend Session Directory](./backend-session-directory.md)
- [Realtime Channel Subscriptions](./realtime-channel-subscriptions.md)
- [Outbox And Recovery](./outbox-and-recovery.md)
- [Pi Display Protocol Contract](./pi-display-protocol-contract.md)
- [End-To-End Test Procedure](./end-to-end-test-procedure.md)
