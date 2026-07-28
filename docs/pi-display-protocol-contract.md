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

The Pi should compare timetokens only within the display acknowledgement and reconciliation stream for a given session.
Android optimistic display updates use local timestamps separately and must never be compared against PubNub timetokens.

## Commands

### `display.show_message`

Purpose:
- Render a group message as the current focal shared-display item.

Requirements:
- Payload must include a full message snapshot.
- Pi must not assume it already received `message.created`.
- Payload includes `commandOrigin = AUTO_LATEST`.
- If the current content is pinned, the Pi must reject this automatic replacement and publish `display.failed`.

### `display.restore_message`

Purpose:
- Re-render a previously displayed message by explicit facilitator action.

Requirements:
- Payload shape matches `display.show_message`.
- Payload includes `commandOrigin = MANUAL_RESTORE`.
- Manual restore is allowed even while pinned. The selected content becomes the new pinned content.

### Manual show origin

- Explicit facilitator Show uses `commandOrigin = MANUAL_SHOW`.
- Manual Show is allowed even while pinned. The selected content becomes the new pinned content.

### `display.pin_message`

Purpose:
- Mark the currently displayed message as pinned.

Requirements:
- Payload includes the `currentMessageId`.
- While pinned, the Pi should reject older or equal-timetoken replacement commands.

### `display.unpin_message`

Purpose:
- Remove the pin while keeping the current message visible.

Requirements:
- The current content must remain visible after unpin succeeds.

### `display.clear`

Purpose:
- Clear the current focal content without unbinding the display from the session.

Requirements:
- Clearing must also remove the active pin.
- Clearing leaves the display bound to the session and sets `currentMessageId = null`.

### `display.mode_changed`

Purpose:
- Reconcile the live session display mode without treating the account preference as the live session setting.

Requirements:
- Payload includes the current `displayMode`.
- Payload may include the current `currentMessageId`, `isPinned`, and `commandOrigin` when known.
- `APPROVAL_REQUIRED` means the Pi must not auto-advance to new group messages without an explicit manual Show or Restore.

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
- `commandOrigin` when focal content exists

### `display.failed`

Purpose:
- Report that a display command was rejected or could not be rendered.

Requirements:
- Set `inReplyToEventId` to the originating command event ID.
- Include the post-failure display state.
- Use this for pinned automatic rejection, malformed payloads, or rendering failures.

## Ordering Rules

- Android stores the last applied display command timetoken in Room.
- Pi should reject a command whose timetoken is older than or equal to the last applied command timetoken for that session.
- Acknowledgements should reflect the post-command state and reference the source command through `inReplyToEventId`.
- State reconciliation through `display.state` should report the Pi's current post-command state and latest accepted ordering.

## Current Android Expectations

- Only eligible group messages auto-display.
- Private facilitator messages, notes, profile data, and AAC signals must never auto-display.
- New sessions default to `AUTO_LATEST`.
- `APPROVAL_REQUIRED` remains an advanced session setting.
- While pinned, Android expects automatic replacement to be rejected but manual Show and Restore to succeed.

## Fixtures

Companion JSON fixtures live in [`docs/pi-display-protocol-fixtures.json`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/docs/pi-display-protocol-fixtures.json).
