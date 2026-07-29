from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import patch

from group_aac_pi.state_machine import (
    DISPLAY_BIND_FAILED,
    DISPLAY_BOUND,
    DISPLAY_UNBOUND,
    DisplayIdentity,
    JsonStateStore,
    PiBindingStateMachine,
)

from .test_state_machine import RecordingHooks


def shared_fixtures() -> dict[str, object]:
    for parent in Path(__file__).resolve().parents:
        candidate = (
            parent
            / "docs"
            / "pi-display-protocol-fixtures.json"
        )
        if candidate.exists():
            with candidate.open(
                "r",
                encoding="utf-8",
            ) as handle:
                return json.load(handle)

    raise AssertionError(
        "Unable to locate docs/pi-display-protocol-fixtures.json"
    )


def build_machine(
    tmp_path: Path,
    now: int,
) -> PiBindingStateMachine:
    machine = PiBindingStateMachine(
        identity=DisplayIdentity(
            display_id="pi-lab-01",
            display_name="Therapy Room Display",
        ),
        state_store=JsonStateStore(
            tmp_path / "pi_state.json"
        ),
        hooks=RecordingHooks(),
        pairing_ttl_ms=30_000,
        now_provider=lambda: now,
    )
    machine.start()
    return machine


def test_shared_fixture_channels_match_contract() -> None:
    channels = shared_fixtures()["channels"]

    assert channels == {
        "displayControl": "display.{displayId}.control",
        "displayDeviceEvents": "display.{displayId}.events",
        "sessionDisplay": "session.{sessionId}.display",
        "sessionDisplayEvents": "session.{sessionId}.display.events",
    }


def test_shared_idle_pairing_fixture_matches_reference_schema() -> None:
    fixture = shared_fixtures()["fixtures"]["idlePairing"]

    assert fixture["type"] == "group-aac-display"
    assert fixture["protocolVersion"] == 1
    assert fixture["displayId"] == "pi-lab-01"
    assert fixture["displayName"] == "Therapy Room Display"
    assert fixture["pairingNonce"] == "pairing-nonce-001"


def test_shared_invitation_fixtures_validate_or_fail(tmp_path: Path) -> None:
    fixtures = shared_fixtures()["fixtures"]
    machine = build_machine(
        tmp_path=tmp_path,
        now=1_785_427_200_000,
    )
    machine._pairing_nonce = fixtures["idlePairing"][
        "pairingNonce"
    ]
    machine._pairing_expires_at = fixtures[
        "idlePairing"
    ]["pairingExpiresAt"]

    active = machine._validate_invitation(
        invitation=fixtures["activeSessionInvitation"],
        expected_session_id="session-123",
    )
    assert active.join_code == "1234-5678"

    with patch(
        "group_aac_pi.state_machine.uuid.uuid4",
        return_value="reply-bind-failed-001",
    ):
        malformed_reply = machine.handle_control_event(
            {
                **fixtures["bindCommand"],
                "payload": {
                    **fixtures["bindCommand"]["payload"],
                    "session": fixtures["malformedInvitation"],
                },
            }
        )

    assert malformed_reply is not None
    assert malformed_reply["type"] == DISPLAY_BIND_FAILED
    assert malformed_reply["payload"]["reason"] == (
        "invalid_session_invitation_type"
    )

    with patch(
        "group_aac_pi.state_machine.uuid.uuid4",
        return_value="reply-bind-failed-002",
    ):
        expired_reply = machine.handle_control_event(
            {
                **fixtures["bindCommand"],
                "eventId": "bind-002",
                "payload": {
                    **fixtures["bindCommand"]["payload"],
                    "session": fixtures["expiredInvitation"],
                },
            }
        )

    assert expired_reply is not None
    assert expired_reply["type"] == DISPLAY_BIND_FAILED
    assert expired_reply["payload"]["reason"] == (
        "session_invitation_expired"
    )


def test_shared_bind_and_unbind_fixtures_round_trip_through_reference(
    tmp_path: Path,
) -> None:
    fixtures = shared_fixtures()["fixtures"]
    machine = build_machine(
        tmp_path=tmp_path,
        now=1_785_427_200_500,
    )

    machine._pairing_nonce = fixtures["idlePairing"][
        "pairingNonce"
    ]
    machine._pairing_expires_at = fixtures[
        "idlePairing"
    ]["pairingExpiresAt"]

    with patch(
        "group_aac_pi.state_machine.uuid.uuid4",
        return_value="reply-bound-001",
    ):
        bound_reply = machine.handle_control_event(
            fixtures["bindCommand"]
        )

    assert bound_reply == fixtures["boundReply"]
    assert machine.binding is not None
    assert machine.binding.session_id == "session-123"

    machine.now_provider = lambda: 1_785_429_000_500

    with patch(
        "group_aac_pi.state_machine.uuid.uuid4",
        return_value="reply-unbound-001",
    ):
        unbound_reply = machine.handle_control_event(
            fixtures["unbindCommand"]
        )

    assert unbound_reply == fixtures["unboundReply"]
    assert machine.binding is None
