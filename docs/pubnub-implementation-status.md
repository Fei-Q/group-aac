# PubNub Implementation Status

Date: 2026-07-29
Branch: `feature/pubnub-pi-prototype-hardening`
Status: display-pairing milestone plus Stages 4A, 4B, 5, 6, and 7 are complete; participant QR scanning now previews and confirms joins, while broader recovery and leasing work remain deferred

## Frozen Scope

- No FastAPI or custom Python backend is part of the current Group AAC app flow.
- Immediate session creation is local-only and creates a `DRAFT` session in Room.
- Scheduled session creation is local-only and remains inactive until explicit launch.
- Join-code discovery is handled by PubNub App Context metadata through `SessionDirectory`.
- Direct session invitations are validated through a shared repository join pipeline and do not call `SessionDirectory`.
- Number-code joins resolve one directory entry, convert it to the same invitation model, and then use that same repository join pipeline.
- JoinSessionScreen now supports both manual-code preview and participant QR preview before confirmation.
- Display pairing is coordinated by Android and a Pi-side pairing QR / control-event handshake.
- Session-scoped display commands now use one generated `eventId` for both optimistic Room state and the outbox event, and acknowledgements are accepted only when command, session, display, and timetoken freshness all match.
- The Python code under [`pi/`](../pi/README.md) is a simulator, protocol reference, and test harness only.
- Production Raspberry Pi software is external to this repository and is being implemented in C++.
- `launchSessionOnDisplay()` is the only intended path that transitions an unstarted session to `LIVE`.

## Current Architecture

- App assembly and dependency wiring live in [AppContainer](../app/src/main/java/com/example/groupaac/AppContainer.kt).
- Room remains the local source of truth through [AppDatabase](../app/src/main/java/com/example/groupaac/data/AppDatabase.kt), repository writes, and realtime apply logic in [DefaultSessionRealtimeSync](../app/src/main/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSync.kt).
- Session lifecycle logic lives in [SessionRepository](../app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt).
- Join-code registration and lookup flow through [SessionDirectory](../app/src/main/java/com/example/groupaac/data/sessiondirectory/SessionDirectory.kt) and [PubNubSessionDirectory](../app/src/main/java/com/example/groupaac/data/sessiondirectory/PubNubSessionDirectory.kt).
- Display pairing protocol helpers live in [DisplayPairingProtocol](../app/src/main/java/com/example/groupaac/data/pi/DisplayPairingProtocol.kt).
- Shared invitation validation and join entry points now use the existing [SessionInvitationPayload](../app/src/main/java/com/example/groupaac/data/pi/DisplayPairingProtocol.kt) model through [SessionRepository.joinInvitation(...)](../app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt).
- Android-to-display bind/unbind orchestration lives in [DisplayBindingCoordinator](../app/src/main/java/com/example/groupaac/data/pi/DisplayBindingCoordinator.kt).
- Participant lookup and preview state live in [SessionCoordinatorViewModel](../app/src/main/java/com/example/groupaac/ui/session/SessionCoordinatorViewModel.kt) and [JoinSessionScreen](../app/src/main/java/com/example/groupaac/ui/session/JoinSessionScreen.kt).
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
6. Participant/facilitator joining now uses one validated invitation pipeline:
   - direct invitation joins receive `SessionInvitationPayload` and perform zero directory calls
   - number-code joins normalize the code, resolve `SessionDirectory` once, convert the result to `SessionInvitationPayload`, and call the same repository function
   - both paths upsert equivalent local session-shell state before participant membership or facilitator request handling
   - `CancellationException` is preserved through both paths
7. JoinSessionScreen lookup flow now behaves as follows:
   - fewer than eight manual digits: no directory request
   - eight manual digits: show resolving state, then preview / not found / expired / invalid / failure
   - participant QR scan: decode and validate with the shared invitation codec/validator and show the same preview without calling `SessionDirectory`
   - neither manual code nor QR scan joins immediately; both require an explicit confirmation tap

## Channel Naming

Display-specific PubNub channels are consistently:

- `display.<displayId>.control`
- `display.<displayId>.events`

Session-scoped display channels remain separate:

- `session.<sessionId>.display`
- `session.<sessionId>.display.events`

## Display Command Semantics

- Every display command now generates exactly one command `eventId`.
- For show, restore, pin, unpin, clear, and display-mode changes, Android writes the optimistic local display state and enqueues the outbox event with that same `eventId` inside one Room transaction.
- Display-state timing is separated into:
  - `localOptimisticUpdatedAt`
  - `lastPublishedCommandTimetoken`
  - `lastPiAppliedCommandTimetoken`
- Android never compares PubNub timetokens with epoch-millisecond timestamps.
- Display-mode changes preserve the current message, pinned state, and command origin instead of resetting them.
- Acknowledgements are side-effect free unless all of the following are true:
  - `inReplyToEventId` matches the current outstanding command
  - session scope matches
  - display scope matches the bound display
  - PubNub timetoken is fresher than the saved publish/apply state

## PubNub Client Audit

- The managed session realtime client comes from `RealtimeClientManager` and is reused for display pairing/binding. This avoids creating extra unmanaged realtime clients for the display handshake.
- The only separate PubNub client in current app wiring is the App Context metadata transport created by [`createPubNubMetadataTransport(...)`](../app/src/main/java/com/example/groupaac/data/sessiondirectory/PubNubMetadataTransport.kt).
- That metadata client exists because the managed `SessionRealtimeClient` abstraction exposes publish/subscribe/history behavior, not App Context metadata operations.
- I did not broaden that abstraction in this milestone freeze because there is no concrete lifecycle defect requiring a redesign, and expanding it would increase risk late in the milestone.

## Explicit Non-Scope

- Scanner UI for participant QR joining has not begun and should not be started from this branch.
- The Python `pi/` package is not a backend and is not the production Raspberry Pi runtime.
- Production Pi implementation details and deployment remain external C++ work.

## Deferred Follow-Up

- Broad snapshot or history-based recovery for invitation joins remains a later recovery-stage requirement and is intentionally not part of Stage 4A.
- Participant QR scanner UI now exists, but snapshot/history recovery and larger reconnect/resume behaviors are still later-stage work.
- Account-scoped outbox leasing is still deferred; this stage only corrects display command generation, optimistic state updates, and acknowledgement acceptance semantics.

## Companion Docs

- [Pi Pairing And Launch](./pi-pairing-and-launch.md)
- [Pi Display Protocol Contract](./pi-display-protocol-contract.md)
- [PubNub Session Directory](./pubnub-session-directory.md)
- [Realtime Channel Subscriptions](./realtime-channel-subscriptions.md)
- [Group AAC Current State And Roadmap](./group_aac_current_state_and_roadmap.md)
