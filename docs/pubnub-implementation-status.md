# PubNub Implementation Status

Date: 2026-07-29
Branch: `feature/pubnub-pi-prototype-hardening`
Status: display-pairing milestone complete in this repository; participant QR joining has not started

## Frozen Scope

- No FastAPI or custom Python backend is part of the current Group AAC app flow.
- Immediate session creation is local-only and creates a `DRAFT` session in Room.
- Scheduled session creation is local-only and remains inactive until explicit launch.
- Join-code discovery is handled by PubNub App Context metadata through `SessionDirectory`.
- Display pairing is coordinated by Android and a Pi-side pairing QR / control-event handshake.
- The Python code under [`pi/`](../pi/README.md) is a simulator, protocol reference, and test harness only.
- Production Raspberry Pi software is external to this repository and is being implemented in C++.
- `launchSessionOnDisplay()` is the only intended path that transitions an unstarted session to `LIVE`.

## Current Architecture

- App assembly and dependency wiring live in [AppContainer](../app/src/main/java/com/example/groupaac/AppContainer.kt).
- Room remains the local source of truth through [AppDatabase](../app/src/main/java/com/example/groupaac/data/AppDatabase.kt), repository writes, and realtime apply logic in [DefaultSessionRealtimeSync](../app/src/main/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSync.kt).
- Session lifecycle logic lives in [SessionRepository](../app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt).
- Join-code registration and lookup flow through [SessionDirectory](../app/src/main/java/com/example/groupaac/data/sessiondirectory/SessionDirectory.kt) and [PubNubSessionDirectory](../app/src/main/java/com/example/groupaac/data/sessiondirectory/PubNubSessionDirectory.kt).
- Display pairing protocol helpers live in [DisplayPairingProtocol](../app/src/main/java/com/example/groupaac/data/pi/DisplayPairingProtocol.kt).
- Android-to-display bind/unbind orchestration lives in [DisplayBindingCoordinator](../app/src/main/java/com/example/groupaac/data/pi/DisplayBindingCoordinator.kt).
- Facilitator launch UI state lives in [SessionCoordinatorViewModel](../app/src/main/java/com/example/groupaac/ui/session/SessionCoordinatorViewModel.kt) and [OutsideSessionNavGraph](../app/src/main/java/com/example/groupaac/ui/navigation/OutsideSessionNavGraph.kt).

## Verified Lifecycle

1. `createSessionNow(...)` creates a local Room session with status `DRAFT`.
2. Draft creation inserts host membership but does not activate `ActiveSessionStore`.
3. No UI file calls `startScheduledSession()` directly.
4. Facilitator display launch starts from a draft or scheduled session, scans a display pairing QR, and calls `launchSessionOnDisplay(...)`.
5. `launchSessionOnDisplay(...)`:
   - validates host ownership and pairing freshness
   - binds the target display over `display.<displayId>.control`
   - waits for acknowledgement on `display.<displayId>.events`
   - registers the join code in PubNub App Context
   - commits the session locally as `LIVE`
   - publishes launch/member realtime events
   - activates the host in `ActiveSessionStore`

## Channel Naming

Display-specific PubNub channels are consistently:

- `display.<displayId>.control`
- `display.<displayId>.events`

Session-scoped display channels remain separate:

- `session.<sessionId>.display`
- `session.<sessionId>.display.events`

## PubNub Client Audit

- The managed session realtime client comes from `RealtimeClientManager` and is reused for display pairing/binding. This avoids creating extra unmanaged realtime clients for the display handshake.
- The only separate PubNub client in current app wiring is the App Context metadata transport created by [`createPubNubMetadataTransport(...)`](../app/src/main/java/com/example/groupaac/data/sessiondirectory/PubNubMetadataTransport.kt).
- That metadata client exists because the managed `SessionRealtimeClient` abstraction exposes publish/subscribe/history behavior, not App Context metadata operations.
- I did not broaden that abstraction in this milestone freeze because there is no concrete lifecycle defect requiring a redesign, and expanding it would increase risk late in the milestone.

## Explicit Non-Scope

- Participant QR joining has not begun and should not be started from this milestone branch.
- The Python `pi/` package is not a backend and is not the production Raspberry Pi runtime.
- Production Pi implementation details and deployment remain external C++ work.

## Companion Docs

- [Pi Pairing And Launch](./pi-pairing-and-launch.md)
- [Pi Display Protocol Contract](./pi-display-protocol-contract.md)
- [PubNub Session Directory](./pubnub-session-directory.md)
- [Realtime Channel Subscriptions](./realtime-channel-subscriptions.md)
- [Group AAC Current State And Roadmap](./group_aac_current_state_and_roadmap.md)
