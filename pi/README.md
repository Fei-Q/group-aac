# Group AAC Raspberry Pi Protocol Simulator

This package is the Python-side simulator and reference implementation
for the Group AAC display-pairing milestone.

## What This Is

- a local simulator for Android display-pairing and launch flows
- a reference implementation of the PubNub pairing protocol
- an automated state-machine and runtime test harness
- a source of protocol fixtures and expected behavior for cross-checking
  the external production Pi implementation

## What This Is Not

- not a FastAPI service
- not a custom Python backend for the Android app
- not the production Raspberry Pi application

The current Android app flow does not depend on a custom Python backend.
Session creation is local to Android, and live short-code lookup uses
PubNub App Context metadata.

## Production Pi Boundary

The production Raspberry Pi display client is implemented separately in
C++ outside this repository.

The C++ implementation must remain compatible with the protocol frozen
here, including:

- `display.<displayId>.control`
- `display.<displayId>.events`
- pairing QR payload fields and versioning
- bind / bound / bind-failed / unbind / unbound semantics
- session invitation payload shape
- acknowledgement correlation and idempotency behavior

## Local Verification

From the repository root:

```bash
cd pi
source .venv/bin/activate
python -m pytest tests -q
python -m compileall group_aac_pi
```
