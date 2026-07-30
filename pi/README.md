# Group AAC Raspberry Pi Reference Simulator

This package is the Python reference simulator and protocol test harness for the Group AAC Raspberry Pi display workflow.

## Role In The Project

- Android is one protocol implementation and the current producer for pairing, binding, and participant invitation payloads.
- `pi/group_aac_pi` is a Python simulator and reference implementation used for tests, fixtures, and integration handoff.
- The production Raspberry Pi client is an external C++ application and is not replaced or generated here.

This package is not:

- a FastAPI service
- a custom Python backend for Android
- the production Raspberry Pi executable

## Contract Sources

The external C++ client should implement the frozen contract documented in:

- [../docs/pi-display-protocol-contract.md](../docs/pi-display-protocol-contract.md)
- [../docs/pi-display-protocol-fixtures.json](../docs/pi-display-protocol-fixtures.json)
- [../docs/pi-cpp-integration-checklist.md](../docs/pi-cpp-integration-checklist.md)

## Python Reference Responsibilities

The Python reference simulator models:

- idle pairing QR generation and rotation
- `display.<displayId>.control` bind and unbind handling
- `display.<displayId>.events` replies
- persisted binding and duplicate reply behavior
- one-active-session enforcement
- restart recovery for a valid persisted binding
- participant invitation validation semantics

It does not own session creation, join-code directory lookup, or Android membership flows.

## Production C++ Boundary

The production C++ Raspberry Pi client is responsible for:

- rendering the idle pairing QR while unbound
- validating `display.bind_session` and `display.unbind_session`
- publishing `display.bound`, `display.bind_failed`, and `display.unbound`
- persisting binding state across restarts
- subscribing to `session.<sessionId>.display` once bound

The production C++ client must not place secrets, credentials, or PubNub keys into QR payloads.

## Local Verification

From the repository root:

```bash
cd pi
source .venv/bin/activate
python -m pytest tests -q
```

Android and Python both load the same shared fixture file from `docs/pi-display-protocol-fixtures.json`.
