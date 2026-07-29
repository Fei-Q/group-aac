from __future__ import annotations

from pathlib import Path
from typing import Any

from group_aac_pi.state_machine import (
    DISPLAY_BIND_FAILED,
    DISPLAY_BIND_SESSION,
    DISPLAY_BOUND,
    DISPLAY_UNBIND_SESSION,
    DISPLAY_UNBOUND,
    DisplayIdentity,
    JsonStateStore,
    PiBindingStateMachine,
    PiLifecycle,
)


class RecordingHooks:
    def __init__(self):
        self.activated_sessions: list[str] = []
        self.deactivation_count = 0

    def activate_session(
        self,
        session_id: str,
    ) -> None:
        self.activated_sessions.append(
            session_id
        )

    def deactivate_session(self) -> None:
        self.deactivation_count += 1


def build_machine(
    state_path: Path,
    now: int = 1_000,
) -> tuple[
    PiBindingStateMachine,
    RecordingHooks,
    list[int],
]:
    clock = [now]
    hooks = RecordingHooks()

    machine = PiBindingStateMachine(
        identity=DisplayIdentity(
            display_id="pi-1",
            display_name="Room Display",
        ),
        state_store=JsonStateStore(
            state_path
        ),
        hooks=hooks,
        pairing_ttl_ms=10_000,
        now_provider=lambda: clock[0],
    )

    machine.start()

    return machine, hooks, clock


def bind_command(
    machine: PiBindingStateMachine,
    event_id: str = "bind-1",
    session_id: str = "session-1",
    pairing_nonce: str | None = None,
    pairing_expires_at: int | None = None,
    display_id: str = "pi-1",
) -> dict[str, Any]:
    pairing = None

    if (
        pairing_nonce is None
        or pairing_expires_at is None
    ):
        pairing = machine.pairing_payload()
        assert pairing is not None

    nonce = (
        pairing_nonce
        if pairing_nonce is not None
        else pairing["pairingNonce"]
    )

    expiry = (
        pairing_expires_at
        if pairing_expires_at is not None
        else pairing["pairingExpiresAt"]
    )

    return {
        "eventId": event_id,
        "type": DISPLAY_BIND_SESSION,
        "sessionId": session_id,
        "actorUserId": "host-1",
        "occurredAt": 1_000,
        "expiresAt": 5_000,
        "payload": {
            "protocolVersion": 1,
            "displayId": display_id,
            "pairingNonce": nonce,
            "pairingExpiresAt": expiry,
            "session": {
                "type": "group-aac-session",
                "protocolVersion": 1,
                "sessionId": session_id,
                "joinCode": "1234-5678",
                "sessionName": "Friday Group",
                "hostUserId": "host-1",
                "displayId": display_id,
                "status": "LIVE",
                "displayMode": "AUTO_LATEST",
                "actualStartedAt": 1_000,
                "expiresAt": 100_000,
            },
        },
    }


def unbind_command(
    session_id: str = "session-1",
    event_id: str = "unbind-1",
) -> dict[str, Any]:
    return {
        "eventId": event_id,
        "type": DISPLAY_UNBIND_SESSION,
        "sessionId": session_id,
        "actorUserId": "host-1",
        "occurredAt": 2_000,
        "expiresAt": 5_000,
        "payload": {
            "protocolVersion": 1,
            "displayId": "pi-1",
        },
    }


def test_bind_success(
    tmp_path: Path,
) -> None:
    machine, hooks, _ = build_machine(
        tmp_path / "state.json"
    )

    reply = machine.handle_control_event(
        bind_command(machine)
    )

    assert reply is not None
    assert reply["type"] == DISPLAY_BOUND
    assert (
        reply["inReplyToEventId"]
        == "bind-1"
    )
    assert machine.lifecycle == (
        PiLifecycle.SESSION_ACTIVE
    )
    assert machine.binding is not None
    assert machine.binding.session_id == (
        "session-1"
    )
    assert hooks.activated_sessions == [
        "session-1"
    ]
    assert machine.pairing_payload() is None


def test_wrong_nonce_is_rejected(
    tmp_path: Path,
) -> None:
    machine, hooks, _ = build_machine(
        tmp_path / "state.json"
    )

    reply = machine.handle_control_event(
        bind_command(
            machine,
            pairing_nonce="wrong-nonce",
        )
    )

    assert reply is not None
    assert reply["type"] == (
        DISPLAY_BIND_FAILED
    )
    assert reply["payload"]["reason"] == (
        "pairing_nonce_mismatch"
    )
    assert machine.binding is None
    assert hooks.activated_sessions == []


def test_wrong_display_id_is_rejected(
    tmp_path: Path,
) -> None:
    machine, _, _ = build_machine(
        tmp_path / "state.json"
    )

    reply = machine.handle_control_event(
        bind_command(
            machine,
            display_id="other-pi",
        )
    )

    assert reply is not None
    assert reply["type"] == (
        DISPLAY_BIND_FAILED
    )
    assert reply["payload"]["reason"] == (
        "display_id_mismatch"
    )


def test_duplicate_bind_is_idempotent(
    tmp_path: Path,
) -> None:
    machine, hooks, _ = build_machine(
        tmp_path / "state.json"
    )

    command = bind_command(machine)

    first_reply = (
        machine.handle_control_event(command)
    )
    second_reply = (
        machine.handle_control_event(command)
    )

    assert first_reply == second_reply
    assert hooks.activated_sessions == [
        "session-1"
    ]


def test_different_session_cannot_rebind(
    tmp_path: Path,
) -> None:
    machine, hooks, _ = build_machine(
        tmp_path / "state.json"
    )

    pairing = machine.pairing_payload()
    assert pairing is not None

    machine.handle_control_event(
        bind_command(
            machine,
            pairing_nonce=pairing["pairingNonce"],
            pairing_expires_at=pairing[
                "pairingExpiresAt"
            ],
        )
    )

    reply = machine.handle_control_event(
        bind_command(
            machine,
            event_id="bind-2",
            session_id="session-2",
            pairing_nonce=pairing["pairingNonce"],
            pairing_expires_at=pairing[
                "pairingExpiresAt"
            ],
        )
    )

    assert reply is not None
    assert reply["type"] == (
        DISPLAY_BIND_FAILED
    )
    assert reply["payload"]["reason"] == (
        "display_already_bound"
    )
    assert hooks.activated_sessions == [
        "session-1"
    ]


def test_unbind_returns_to_pairing(
    tmp_path: Path,
) -> None:
    machine, hooks, _ = build_machine(
        tmp_path / "state.json"
    )

    machine.handle_control_event(
        bind_command(machine)
    )

    reply = machine.handle_control_event(
        unbind_command()
    )

    assert reply is not None
    assert reply["type"] == DISPLAY_UNBOUND
    assert machine.binding is None
    assert machine.lifecycle == (
        PiLifecycle.PAIRING_AVAILABLE
    )
    assert hooks.deactivation_count == 1
    assert machine.pairing_payload() is not None


def test_binding_restores_after_restart(
    tmp_path: Path,
) -> None:
    state_path = tmp_path / "state.json"

    first_machine, _, _ = build_machine(
        state_path
    )

    first_machine.handle_control_event(
        bind_command(first_machine)
    )

    restored_hooks = RecordingHooks()

    restored_machine = PiBindingStateMachine(
        identity=DisplayIdentity(
            display_id="pi-1",
            display_name="Room Display",
        ),
        state_store=JsonStateStore(
            state_path
        ),
        hooks=restored_hooks,
        now_provider=lambda: 2_000,
    )

    restored_machine.start()

    assert restored_machine.lifecycle == (
        PiLifecycle.SESSION_ACTIVE
    )
    assert restored_hooks.activated_sessions == [
        "session-1"
    ]


def test_expired_command_is_rejected(
    tmp_path: Path,
) -> None:
    machine, hooks, clock = build_machine(
        tmp_path / "state.json"
    )

    command = bind_command(machine)
    command["expiresAt"] = 1_500
    clock[0] = 2_000

    reply = machine.handle_control_event(
        command
    )

    assert reply is not None
    assert reply["type"] == (
        DISPLAY_BIND_FAILED
    )
    assert reply["payload"]["reason"] == (
        "command_expired"
    )
    assert hooks.activated_sessions == []