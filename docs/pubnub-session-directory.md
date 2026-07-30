# PubNub Session Directory

## Purpose

Maps an eight-digit monitor code to a versioned session invitation.

## Metadata ID

join.<eightDigits>

Example:

join.48371265

## Stored custom fields

- protocolVersion
- joinCode
- sessionId
- sessionName
- hostUserId
- displayId
- sessionStatus
- displayMode
- createdAt
- actualStartedAt
- expiresAt

All values are stored as scalar strings.

## Lifecycle

- DRAFT/SCHEDULED: no directory entry
- Pi binding acknowledged: register LIVE entry
- Session updated: replace full metadata object
- ENDED/CANCELLED: make unresolvable and remove