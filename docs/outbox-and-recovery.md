# Outbox And Recovery

## Purpose

Room remains the UI source of truth even when realtime transport is delayed or unavailable. Reliability primitives ensure that outgoing actions are persisted first, published after commit, retried deterministically, and replayed safely after restart.

Core implementation lives in:

- [RealtimeReliabilityStore](../app/src/main/java/com/example/groupaac/data/realtime/reliability/RealtimeReliabilityStore.kt)
- [OutboxDispatcher](../app/src/main/java/com/example/groupaac/data/realtime/reliability/OutboxDispatcher.kt)
- [DefaultSessionRealtimeSync](../app/src/main/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSync.kt)

## Persisted Reliability Tables

Room entities include:

- `outbox_events`
- `processed_events`
- `channel_cursors`
- `display_state`

Related entity definitions live under [data/realtime/reliability](../app/src/main/java/com/example/groupaac/data/realtime/reliability).

## Atomic Publish Pattern

For every outgoing action, Android writes the domain mutation and the outbox row in one Room transaction.

This pattern now covers:

- messages
- session lifecycle and settings changes
- facilitator request / approve / decline / cancel
- membership events
- AAC signal events
- display commands

Representative repository entry points:

- [MessageRepository](../app/src/main/java/com/example/groupaac/data/repository/MessageRepository.kt)
- [SessionRepository](../app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt)
- [SignalRepository](../app/src/main/java/com/example/groupaac/data/repository/SignalRepository.kt)

Newly created messages begin as `PENDING`, not `SENT`. Saving a message remains a separate boolean flag and is not overloaded onto transport state.

## Outbox State Machine

Transport rows move through:

- `PENDING`
- `SENDING`
- `SENT`
- `FAILED`

Dispatcher behavior:

- publishes only after the enclosing transaction commits
- catches publish exceptions
- records attempt counts and next retry time
- exposes explicit retry for a failed row
- recovers stale `SENDING` rows left behind by interruption

## Retry Behavior

`OutboxDispatcher` applies:

- bounded backoff
- maximum attempts
- application-scope immediate dispatch
- WorkManager fallback for restart/background recovery

On app restart, unsent rows are discovered and retried.

## Cursor And Replay

Channel cursors store the maximum PubNub timetoken seen per channel.

Rules:

- cursor advancement is monotonic and never moves backward
- history replay starts strictly after the stored cursor
- replay ordering is by PubNub timetoken within each channel
- overlap between replayed history and live subscription delivery is deduplicated by `eventId`
- user-visible occurrence time remains the envelope `occurredAt`, not transport timetoken

## Display Recovery Rules

Display acknowledgements are applied transactionally:

- check acknowledgement freshness first
- update `display_state`
- update message display flags
- record processed event row
- advance the display-events cursor

Stale acknowledgements must not alter message display flags. Detailed protocol rules are in [Pi Display Protocol Contract](./pi-display-protocol-contract.md).

## Facilitator Approval Transaction

Host approval is intentionally self-contained:

1. approve the facilitator request
2. create or update facilitator membership
3. enqueue private `facilitator.approved`
4. enqueue public `member.joined`

Requester-side apply is also transactional:

- apply session shell
- apply membership
- apply request state
- record processed event
- advance cursor

Only after that transaction commits does the app:

- set the active session
- start facilitator subscriptions
- enter facilitator UI

This prevents requester activation from depending on public roster timing.

## Tests

Reliability coverage includes:

- [OutboxDispatcherTest](../app/src/test/java/com/example/groupaac/data/realtime/reliability/OutboxDispatcherTest.kt)
- [DefaultSessionRealtimeSyncTest](../app/src/test/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSyncTest.kt)
- [SessionRepositoryTest](../app/src/test/java/com/example/groupaac/data/repository/SessionRepositoryTest.kt)
- [SignalRepositoryTest](../app/src/test/java/com/example/groupaac/data/repository/SignalRepositoryTest.kt)

Executed result summary is in [Live Realtime Integration Status](./pubnub-implementation-status.md).
