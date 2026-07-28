# Pi Display Protocol Contract

Date: 2026-07-28
Status: Android-side contract only. Raspberry Pi implementation is not present in this repository.

## Channels

- `session.<sessionId>.display`
  - Android facilitator/host publishes explicit display commands for the active session.
- `session.<sessionId>.display.events`
  - Raspberry Pi publishes acknowledgements and current display state updates.
- `display.<displayId>.control`
  - Reserved for device bind/unbind control outside the session-specific display flow.

## Event Envelope

All messages use the canonical realtime envelope already implemented on Android:

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

Transport metadata is outside the payload:

- PubNub channel name
- PubNub timetoken
- optional publisher user ID

The Pi should compare timetokens only within the display command stream for a given session.

## Commands

### `display.show_message`

Purpose:
- Render a group message as the current focal shared-display item.

Requirements:
- Payload must include a full message snapshot.
- Pi must not assume it already received `message.created`.

### `display.restore_message`

Purpose:
- Re-render a previously displayed message by explicit facilitator action.

Requirements:
- Payload shape matches `display.show_message`.

### `display.pin_message`

Purpose:
- Mark the currently displayed message as pinned.

Requirements:
- Payload includes the `currentMessageId`.
- While pinned, the Pi should reject older or equal-timetoken replacement commands.

### `display.unpin_message`

Purpose:
- Remove the pin while keeping the current message visible.

### `display.clear`

Purpose:
- Clear the current focal content without unbinding the display from the session.

Requirements:
- Clearing must also remove the active pin.

## Acknowledgements And State Reports

### `display.rendered`

Purpose:
- Confirm that `display.show_message` was rendered.

Requirements:
- Set `inReplyToEventId` to the originating command event ID.

### `display.restored`

Purpose:
- Confirm that `display.restore_message` was rendered.

Requirements:
- Set `inReplyToEventId` to the originating command event ID.

### `display.cleared`

Purpose:
- Confirm that focal content was cleared.

Requirements:
- Set `inReplyToEventId` to the originating command event ID.

### `display.pinned`

Purpose:
- Confirm that the current message is pinned.

Requirements:
- Set `inReplyToEventId` to the originating command event ID.

### `display.unpinned`

Purpose:
- Confirm that the current message remains visible but is no longer pinned.

Requirements:
- Set `inReplyToEventId` to the originating command event ID.

### `display.state`

Purpose:
- Report the Pi's current display state during reconnect or reconciliation.

Payload requirements:
- `sessionId`
- `currentMessageId` or `null`
- `isPinned`
- `displayMode`

## Ordering Rules

- Android stores the last applied display command timetoken in Room.
- Pi should reject a command whose timetoken is older than or equal to the last applied command timetoken for that session.
- Acknowledgements should reflect the post-command state and reference the source command through `inReplyToEventId`.

## Current Android Expectations

- Only eligible group messages auto-display.
- Private facilitator messages, notes, profile data, and AAC signals must never auto-display.
- New sessions default to `AUTO_LATEST`.
- `APPROVAL_REQUIRED` remains an advanced session setting.

## Fixtures

Companion JSON fixtures live in [`docs/pi-display-protocol-fixtures.json`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/docs/pi-display-protocol-fixtures.json).
