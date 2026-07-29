from __future__ import annotations

from pathlib import Path
from types import SimpleNamespace

from group_aac_pi.runtime import (
    PiConfig,
    PubNubPiRuntime,
)

from .test_state_machine import (
    bind_command,
    display_command,
)


def build_runtime(
    tmp_path: Path,
) -> PubNubPiRuntime:
    runtime = PubNubPiRuntime(
        PiConfig(
            publish_key="demo",
            subscribe_key="demo",
            display_id="pi-1",
            display_name="Room Display",
            state_path=tmp_path / "state.json",
            artifacts_dir=tmp_path / "artifacts",
            pairing_ttl_ms=10_000,
        )
    )
    return runtime


def test_session_display_acknowledgement_publishes_to_session_event_channel(
    tmp_path: Path,
) -> None:
    runtime = build_runtime(tmp_path)
    published: list[tuple[str, dict[str, object]]] = []
    runtime._publish = (
        lambda channel, message: published.append(
            (channel, message)
        )
    )
    runtime.activate_session = (
        lambda session_id: setattr(
            runtime,
            "active_session_id",
            session_id,
        )
    )
    runtime.deactivate_session = (
        lambda: setattr(
            runtime,
            "active_session_id",
            None,
        )
    )

    runtime.machine.start()
    command = bind_command(runtime.machine)
    command["expiresAt"] = 9_999_999_999_999
    command["payload"]["session"]["expiresAt"] = (
        9_999_999_999_999
    )
    runtime.machine.handle_control_event(command)

    runtime._on_session_display_message(
        SimpleNamespace(
            message=display_command(
                "display.show_message",
                "show-1",
                current_message_id="msg-1",
                is_pinned=True,
                display_mode="MANUAL",
                command_origin="MANUAL_SHOW",
            )
        )
    )

    assert len(published) == 1
    channel, message = published[0]
    assert channel == "session.session-1.display.events"
    assert message["type"] == "display.rendered"
    assert message["actorUserId"] == "pi-1"
    assert message["inReplyToEventId"] == "show-1"
