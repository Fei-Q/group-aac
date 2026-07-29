# Pi C++ Integration Checklist

Date: 2026-07-29
Status: implementation handoff checklist for the external production Raspberry Pi client

## Required Responsibilities

The external C++ client is responsible for:

- owning the physical Raspberry Pi display process
- generating and refreshing the idle pairing QR
- subscribing to `display.<displayId>.control`
- publishing binding replies to `display.<displayId>.events`
- persisting accepted binding state across restarts
- enforcing one active bound session per display
- subscribing to `session.<sessionId>.display` after a successful bind
- later publishing session display acknowledgements to `session.<sessionId>.display.events`

The external C++ client is not responsible for:

- creating sessions
- resolving join codes through App Context
- acting as a custom backend for Android
- replacing the Android repository or participant join pipeline

## Protocol Checklist

- Support `protocolVersion = 1`.
- Accept display pairing QR payloads with `type = group-aac-display`.
- Accept participant invitation QR payloads with `type = group-aac-session`.
- Treat `pairingExpiresAt`, `actualStartedAt`, and `expiresAt` as epoch milliseconds.
- Reject unsupported protocol versions.
- Reject expired commands and expired invitations.
- Normalize join codes to `1234-5678`.
- Never place secrets or credentials in QR payloads.

## Binding Checklist

- Subscribe to `display.<displayId>.control`.
- On `display.bind_session`, validate `displayId`, nonce, pairing expiry, command expiry, and invitation fields.
- Return `display.bound` on success.
- Return `display.bind_failed` with a concrete reason on failure.
- Persist the accepted binding and reply cache before acknowledging success.
- Replaying the same command `eventId` must return the same stored reply.
- A new bind for a different session while already bound must fail with `display_already_bound`.

## Unbinding Checklist

- Accept `display.unbind_session` on `display.<displayId>.control`.
- Return `display.unbound` when the current session is unbound.
- Returning `display.unbound` while already unbound is acceptable and idempotent.
- If `sessionId` does not match the current binding, return `display.bind_failed` with `session_mismatch`.
- Restore the display to idle pairing after successful unbind.

## Restart Checklist

- Persist binding state locally.
- Persist duplicate reply history keyed by command `eventId`.
- On restart with a valid unexpired binding, re-enter the active session subscription.
- On restart with an expired binding, clear it and return to pairing mode.

## Validation Checklist

- Use [pi-display-protocol-fixtures.json](./pi-display-protocol-fixtures.json) as the shared fixture pack.
- Cross-check behavior against [pi-display-protocol-contract.md](./pi-display-protocol-contract.md).
- Confirm the C++ client matches Android channel names exactly.
- Confirm the C++ client matches Python reference reasons and idempotency behavior.
