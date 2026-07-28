# Pi Display Protocol Contract

Date: 2026-07-28
Status: Android-side contract and Python Pi test-consumer handoff

## Scope

This repository does not contain production Raspberry Pi code. The current handoff target is:

- shared protocol semantics implemented on Android in [DefaultSessionRealtimeSync](../app/src/main/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSync.kt)
- deterministic consumer behavior modeled in [backend/pi_test_consumer.py](../backend/pi_test_consumer.py)
- JSON fixtures in [pi-display-protocol-fixtures.json](./pi-display-protocol-fixtures.json)

## Channels

- `session.<sessionId>.display`
  - Android facilitator/host publishes display commands here.
- `session.<sessionId>.display.events`
  - Pi publishes acknowledgements and state reconciliation events here.
- `display.<displayId>.control`
  - Reserved for future device enrollment or out-of-band control.

## Event Envelope

Every command and acknowledgement uses the canonical realtime envelope:

```json
{
  "eventId": "evt-123",
  "type": "display.show_message",
  "sessionId": "session-abc",
  "actorUserId": "host_1",
  "occurredAt": 1785259200000,
  "inReplyToEventId": null,
  "expiresAt": null,
  "payload": {}
}
```

Transport metadata is separate from the payload:

- channel name
- PubNub timetoken
- optional publisher user ID

## Display Mode Rules

- New sessions copy the account default at creation time:
  - `monitorRequireManualApproval = false` -> `AUTO_LATEST`
  - `monitorRequireManualApproval = true` -> `APPROVAL_REQUIRED`
- After session creation, the live session mode comes from `SessionEntity.displayMode`, not from the account preference.
- Android publishes both:
  - `session.settings_changed` on the session public channel
  - `display.mode_changed` on the session display channel

## Command Origin Rules

Supported command origins:

- `AUTO_LATEST`
- `MANUAL_SHOW`
- `MANUAL_RESTORE`

Required behavior:

- while pinned, reject `AUTO_LATEST` replacement
- while pinned, allow `MANUAL_SHOW`
- while pinned, allow `MANUAL_RESTORE`
- manual replacement keeps the display pinned to the newly selected content
- unpin keeps current content visible
- clear removes current content and also clears pin state

## Commands

### `display.show_message`

- Sent for eligible group messages only
- Never auto-display:
  - private messages
  - notes
  - profiles
  - AAC signals
- Payload key: `display`
- Payload includes:
  - `sessionId`
  - `displayMode`
  - `isPinned`
  - `commandOrigin`
  - fully renderable message snapshot

### `display.restore_message`

- Explicit facilitator restore of a previously shown message
- Uses the same payload shape as `display.show_message`
- `commandOrigin` must be `MANUAL_RESTORE`

### `display.pin_message`

- Payload key: `displayState`
- Must include current `currentMessageId`
- Marks the current item pinned

### `display.unpin_message`

- Payload key: `displayState`
- Keeps the current content visible while removing the pin

### `display.clear`

- Payload key: `displayState`
- Clears visible content and removes the pin

### `display.mode_changed`

- Payload key: `displayState`
- Announces the live session display mode and optionally the current message/pin/origin state

## Acknowledgements

Pi acknowledgements use `inReplyToEventId` to point back to the command event:

- `display.rendered`
- `display.restored`
- `display.cleared`
- `display.pinned`
- `display.unpinned`
- `display.failed`
- `display.state`

`display.failed` should be used for:

- pinned automatic replacement rejection
- malformed payloads
- unsupported commands
- stale command rejection
- rendering failures

## Ordering And Freshness

- PubNub timetokens are ordering metadata only.
- Never compare `System.currentTimeMillis()` with a PubNub timetoken.
- Android stores `lastAppliedCommandTimetoken` in `display_state`.
- Pi should reject a command whose timetoken is older than or equal to the last accepted command timetoken for that session.
- Android checks acknowledgement freshness first, then transactionally updates:
  - `display_state`
  - message display flags
  - processed event row
  - channel cursor
- A stale acknowledgement must not change Room display flags.

## Python Pi Consumer Behavior

[backend/pi_test_consumer.py](../backend/pi_test_consumer.py) currently models:

- duplicate event-id rejection
- stale timetoken rejection
- pinned `AUTO_LATEST` rejection
- show/restore pin preservation
- unpin-without-clearing semantics
- clear-removes-pin semantics
- acknowledgement publication on the display-events channel

It is not yet a live PubNub subscriber. It is a protocol reference and deterministic test harness.

## Physical Pi Handoff

When replacing the Python harness with a physical Pi implementation, preserve:

- channel names
- payload keys (`display`, `displayState`)
- acknowledgement types
- `inReplyToEventId` usage
- timetoken ordering rules
- pinned automatic rejection
- manual replacement semantics

Recommended next implementation step:

1. Subscribe to `session.<sessionId>.display`.
2. Persist `currentMessageId`, `isPinned`, and `lastCommandTimetoken`.
3. Emit acknowledgements on `session.<sessionId>.display.events`.
4. Use [pi-display-protocol-fixtures.json](./pi-display-protocol-fixtures.json) as the initial fixture pack.
