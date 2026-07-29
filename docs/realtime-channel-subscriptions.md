# Realtime Channel Subscriptions

## Canonical Channel Names

Channel helpers live in [RealtimeChannels](../app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeChannels.kt).

- public session stream: `session.<sessionId>.public`
- facilitator stream: `session.<sessionId>.facilitator`
- private per-user stream: `session.<sessionId>.<userId>`
- display commands: `session.<sessionId>.display`
- display acknowledgements and reconciliation: `session.<sessionId>.display.events`
- reserved device control stream: `display.<displayId>.control`
- retained device event stream: `display.<displayId>.events`

## Event Types

Declared realtime constants live in [RealtimeEventTypes](../app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeEventTypes.kt).

Implemented event families include:

- session lifecycle and settings
- facilitator request / approved / declined / cancelled
- membership join / left / removed / display-name changed / role changed / host transferred
- message created / deleted
- announcements
- attachments available / failed
- AAC signals create / snooze / clear
- display commands and acknowledgements
- snapshot requested / snapshot

Reserved event constants, if any remain, should be kept clearly marked in code rather than silently ignored.

## Active User Lifecycle

Realtime client lifecycle is coordinated across:

- [AccountRepository](../app/src/main/java/com/example/groupaac/data/repository/AccountRepository.kt)
- [RealtimeClientManager](../app/src/main/java/com/example/groupaac/data/realtime/RealtimeClientManager.kt)
- [RealtimeStartupInitializer](../app/src/main/java/com/example/groupaac/data/realtime/RealtimeStartupInitializer.kt)

Rules:

- exactly one PubNub client exists per active UID
- startup activates the persisted UID before active-session restoration
- account switch closes the previous client before activating the next
- sign-out closes subscriptions and destroys the live client
- real-client initialization failure is surfaced and does not silently fall back to the fake

## Subscription Coordinator

[SessionSubscriptionCoordinator](../app/src/main/java/com/example/groupaac/data/realtime/SessionSubscriptionCoordinator.kt) is the runtime owner for session-scoped collection.

Its responsibilities are:

- start subscriptions when session membership or facilitator state changes
- stop old collectors on leave, end, sign-out, and account switch
- restore subscriptions after app restart
- route received events through one pipeline
- expose connection state to [SessionCoordinatorViewModel](../app/src/main/java/com/example/groupaac/ui/session/SessionCoordinatorViewModel.kt)
- recover Message Persistence history after the saved channel cursor before live collection resumes

## Subscription Matrix

### Participant

- `session.<sessionId>.public`
- `session.<sessionId>.<userId>`

### Facilitator / Host

- `session.<sessionId>.public`
- `session.<sessionId>.facilitator`
- `session.<sessionId>.<userId>`
- `session.<sessionId>.display.events`
- `display.<displayId>.events` when the active session is already bound to a display

### Pending Facilitator Requester

- `session.<sessionId>.public`
- `session.<sessionId>.<userId>`

This lets the requester receive private `facilitator.approved` or `facilitator.declined` before they are activated as a facilitator.

## Start Conditions

Subscriptions start when:

- host creates or opens a session
- participant joins a session
- facilitator request begins and the requester private channel must be monitored
- approved facilitator activation commits locally
- persisted active session is restored after restart

## Stop Conditions

Subscriptions stop when:

- the user leaves a session
- the host ends or cancels a session
- the active account changes
- the user signs out

Collector jobs are cancelled before replacement so they do not survive account or session changes.

## Incoming Pipeline

Every received event follows the same pipeline:

1. parse the raw PubNub payload into the canonical envelope
2. verify channel and session consistency
3. reject expired events
4. deduplicate by `eventId`
5. apply domain changes to Room
6. record processed event state and advance the cursor

Android apply logic lives in [DefaultSessionRealtimeSync](../app/src/main/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSync.kt).

## Message Persistence Replay

Android production transport uses PubNub Kotlin SDK `13.4.1` `fetchMessages(...)` message-persistence retrieval for one channel at a time.

Rules:

- replay starts from the saved per-channel cursor using `end = afterTimetoken`
- the PubNub `end` boundary is inclusive, so Android drops any event with `timetoken <= afterTimetoken`
- additional pages are requested only when needed, using the returned `page` token from PubNub
- replay results are sorted ascending before application
- overlap across pages is deduplicated before apply
- replay is used for public, facilitator, private-user, session display-event, and retained device-event channels
- old Pi control commands are not auto-replayed into the device-control channel; only retained acknowledgements/device events are candidates for recovery

Malformed history policy:

- malformed persisted payloads are quarantined locally for diagnostics
- valid events from the same replay batch continue
- cursor advancement still depends on successfully applied valid events, not on raw history fetch completion
- broader persistent quarantine tooling is deferred; see [Outbox And Recovery](./outbox-and-recovery.md)

## Routing Notes

- session lifecycle, public messages, announcements, roster events, and snapshots use the public channel
- facilitator requests, approvals visible to hosts, and signal create/clear use the facilitator channel
- private approvals, declines, facilitator-specific snooze, and targeted snapshots use the private user channel
- display commands publish on `session.<sessionId>.display`
- display acknowledgements and reconciliation publish on `session.<sessionId>.display.events`
- obvious channel/session mismatches are rejected even though full authorization policy is deferred

## Tests

Coverage lives in Android unit and connected tests, including:

- [SessionSubscriptionCoordinatorTest](../app/src/test/java/com/example/groupaac/data/realtime/SessionSubscriptionCoordinatorTest.kt)
- [PubNubSessionRealtimeClientTest](../app/src/test/java/com/example/groupaac/data/realtime/PubNubSessionRealtimeClientTest.kt)
- [RealtimeComposeFlowsTest](../app/src/androidTest/java/com/example/groupaac/RealtimeComposeFlowsTest.kt)

Final executed outcomes are summarized in [Live Realtime Integration Status](./pubnub-implementation-status.md), and the manual two-client exercise is in [End-To-End Test Procedure](./end-to-end-test-procedure.md).
