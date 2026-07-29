# Pi Display Protocol Contract

Date: 2026-07-29
Status: frozen handoff contract for the external production C++ Raspberry Pi client

## Scope

This repository does not contain the production Raspberry Pi display application.

The protocol has three implementations with distinct roles:

- Android is the shipping protocol producer and one consumer of the contract.
- `pi/group_aac_pi` is a Python reference simulator and deterministic test harness.
- The professor's external C++ Raspberry Pi application is the production implementation.

The C++ client must match this contract without requiring a custom Python or FastAPI backend.

## Protocol Version

- `protocolVersion = 1`
- Both QR payloads and device-channel replies must reject unsupported versions.

## Epoch-Millisecond Fields

These fields are Unix epoch milliseconds:

- `pairingExpiresAt`
- `actualStartedAt`
- `expiresAt`
- realtime envelope `occurredAt`
- realtime envelope `expiresAt`

PubNub timetokens are transport ordering metadata and are not interchangeable with epoch milliseconds.

## QR Payloads

### Display Pairing QR

Shown only while the display has no active session binding.

```json
{
  "type": "group-aac-display",
  "protocolVersion": 1,
  "displayId": "pi-lab-01",
  "displayName": "Therapy Room Display",
  "pairingNonce": "pairing-nonce-001",
  "pairingExpiresAt": 1785427500000
}
```

Rules:

- no secrets, PubNub keys, auth tokens, or long-lived credentials in the QR
- `displayId` and `displayName` are required nonblank strings
- `pairingNonce` is short-lived and rotates when idle pairing expires
- `pairingExpiresAt` must be in the future when scanned

### Participant Session Invitation QR

Shown only for a LIVE session invitation flow. It is not the display-pairing QR.

```json
{
  "type": "group-aac-session",
  "protocolVersion": 1,
  "sessionId": "session-123",
  "joinCode": "1234-5678",
  "sessionName": "Friday Group",
  "hostUserId": "host-123",
  "displayId": "pi-lab-01",
  "status": "LIVE",
  "displayMode": "AUTO_LATEST",
  "actualStartedAt": 1785423600000,
  "expiresAt": 1785430800000
}
```

Rules:

- no secrets, PubNub keys, auth tokens, or private membership data in the QR
- `type` must equal `group-aac-session`
- `protocolVersion` must be supported
- `sessionId`, `sessionName`, `hostUserId`, and `displayId` must be nonblank
- `joinCode` must normalize to exactly eight digits formatted as `1234-5678`
- `status` must be `LIVE`
- `actualStartedAt` must be a valid positive epoch millisecond
- `expiresAt` must be later than both `actualStartedAt` and the current time

## Channels

Device-scoped pairing and binding channels:

- display control channel: `display.<displayId>.control`
- display device-event channel: `display.<displayId>.events`

Session-scoped display channels:

- session display channel: `session.<sessionId>.display`
- session display-event channel: `session.<sessionId>.display.events`

Current milestone ownership:

- Android publishes `display.bind_session` and `display.unbind_session` to `display.<displayId>.control`
- the Pi publishes `display.bound`, `display.bind_failed`, and `display.unbound` to `display.<displayId>.events`
- once bound, the Pi subscribes to `session.<sessionId>.display`
- later shared-display rendering acknowledgements use `session.<sessionId>.display.events`

## Realtime Envelope

Commands and replies use the shared realtime event envelope:

```json
{
  "eventId": "evt-123",
  "type": "display.bind_session",
  "sessionId": "session-123",
  "actorUserId": "host-123",
  "occurredAt": 1785427200000,
  "inReplyToEventId": null,
  "expiresAt": 1785427230000,
  "payload": {}
}
```

Rules:

- `eventId` is required and unique per command
- replies must set `inReplyToEventId` to the triggering command `eventId`
- `sessionId` in the bind command must match `payload.session.sessionId`
- `actorUserId` for device replies is the Pi `displayId`

## Bind Command

`display.bind_session` is published to `display.<displayId>.control`.

Payload:

- `protocolVersion`
- `displayId`
- `pairingNonce`
- `pairingExpiresAt`
- `session` containing the participant invitation payload

Validation requirements on the Pi:

- command `expiresAt` must still be in the future when handled
- `displayId` must match the device identity
- `protocolVersion` must be supported
- `pairingNonce` and `pairingExpiresAt` must match the current pairing QR
- participant invitation payload must satisfy the invitation rules above
- the display may have only one active session at a time

## Unbind Command

`display.unbind_session` is published to `display.<displayId>.control`.

Payload:

- `protocolVersion`
- `displayId`

Rules:

- if the display is already unbound, returning `display.unbound` is valid and idempotent
- if the display is bound to a different session than the command `sessionId`, return `display.bind_failed` with reason `session_mismatch`

## Replies

Replies are published on `display.<displayId>.events`.

### `display.bound`

Success reply to `display.bind_session`.

Payload:

- `protocolVersion`
- `displayId`
- `state = "SESSION_ACTIVE"`

### `display.bind_failed`

Failure reply to a bind or unbind attempt that cannot be accepted.

Payload:

- `protocolVersion`
- `displayId`
- `state`
- `reason`

Canonical reasons already modeled by the reference simulator:

- `command_expired`
- `display_id_mismatch`
- `unsupported_protocol_version`
- `pairing_expired`
- `pairing_nonce_mismatch`
- `pairing_expiry_mismatch`
- `invalid_session_invitation_type`
- `unsupported_session_protocol_version`
- `session_not_live`
- `session_id_mismatch`
- `invitation_display_id_mismatch`
- `session_invitation_expired`
- `display_already_bound`
- `session_subscription_failed`
- `session_mismatch`
- `session_unsubscribe_failed`

### `display.unbound`

Success reply to `display.unbind_session`.

Payload:

- `protocolVersion`
- `displayId`
- `state = "PAIRING_AVAILABLE"`

## Idempotency And Duplicate Handling

- Duplicate command `eventId` values must return the same persisted reply body.
- Reprocessing the same bind command must not activate the session twice.
- Reprocessing the same unbind command must not deactivate twice.
- Duplicate command handling is keyed by the command `eventId`, not by PubNub timetoken.

## Stale Command Handling

- A command whose envelope `expiresAt` is already past must be rejected with `display.bind_failed` and `reason = "command_expired"`.
- A bind command that references an expired pairing QR must be rejected with `reason = "pairing_expired"`.
- A bind command that references a rotated nonce or mismatched pairing expiry must be rejected.

## Expiry Behavior

- Idle pairing QR expiry rotates the nonce and regenerates the pairing QR while unbound.
- Invitation expiry prevents new participant joins and prevents new display binding for that session invitation.
- A persisted binding whose invitation `expiresAt` is already in the past at restart must not be resumed.

## One-Active-Session Rule

- One physical display can be bound to at most one active session at a time.
- A second bind for the same session is idempotent and may return `display.bound`.
- A second bind for a different session while already bound must fail with `display_already_bound`.

## Restart And Persisted Binding Behavior

- The Pi persists the accepted binding and reply cache locally.
- On restart, if the persisted binding has not expired, the Pi resumes the session subscription and returns to `SESSION_ACTIVE`.
- On restart, if the persisted binding is expired, the Pi clears it and returns to idle pairing.

## QR Visibility Rules

- Pairing QR is shown only when the display is unbound and pairing is available.
- Pairing QR is hidden once a bind is accepted.
- Participant invitation QR is shown for participant joining flows, not for device pairing.
- Accepting `display.bind_session` does not itself instruct the Pi to show participant QR content.

## Join-Code Normalization

- A valid join code contains exactly eight digits after normalization.
- The canonical formatted form is `1234-5678`.
- Android and the Python reference both accept digit-only input and normalize it to the canonical form before use.

## State Transitions

The reference lifecycle is:

- `IDLE`
- `PAIRING_AVAILABLE`
- `BINDING`
- `SESSION_ACTIVE`
- `UNBINDING`
- `PAIRING_AVAILABLE`

Restart and failure cases:

- persisted valid binding on startup -> `SESSION_ACTIVE`
- persisted expired binding on startup -> `PAIRING_AVAILABLE`
- activation or unsubscription failure -> error is recorded and a failure reply is emitted when applicable

## Shared Fixtures

Canonical examples live in [pi-display-protocol-fixtures.json](./pi-display-protocol-fixtures.json).

Those fixtures are consumed by:

- Android unit tests in `app/src/test/.../DisplayPairingProtocolTest.kt`
- Python unit tests in `pi/tests/`

They are the handoff source of truth for the production C++ client.
