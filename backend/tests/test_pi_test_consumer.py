from backend.pi_test_consumer import PiTestConsumer


def display_command(
    event_id: str,
    event_type: str,
    *,
    message_id: str | None = "msg-1",
    origin: str | None = "MANUAL_SHOW",
):
    payload: dict[str, object] = {}
    if message_id is not None:
        payload["message"] = {"messageId": message_id}
        payload["currentMessageId"] = message_id
    if origin is not None:
        payload["commandOrigin"] = origin
    return {
        "eventId": event_id,
        "type": event_type,
        "sessionId": "session-1",
        "actorUserId": "host_1",
        "payload": payload,
    }


def test_consumer_tracks_display_and_pin_state():
    consumer = PiTestConsumer(session_id="session-1")

    rendered = consumer.apply_command(
        event=display_command("evt-show", "display.show_message"),
        timetoken=100,
    )
    pinned = consumer.apply_command(
        event=display_command("evt-pin", "display.pin_message"),
        timetoken=101,
    )

    assert rendered is not None
    assert rendered.event["type"] == "display.rendered"
    assert pinned is not None
    assert pinned.event["type"] == "display.pinned"
    snapshot = consumer.snapshot()
    assert snapshot.current_message_id == "msg-1"
    assert snapshot.is_pinned is True


def test_consumer_rejects_stale_and_duplicate_commands():
    consumer = PiTestConsumer(session_id="session-1")
    consumer.apply_command(
        event=display_command("evt-show", "display.show_message"),
        timetoken=100,
    )

    duplicate = consumer.apply_command(
        event=display_command("evt-show", "display.show_message"),
        timetoken=101,
    )
    stale = consumer.apply_command(
        event=display_command("evt-old", "display.clear", message_id=None, origin=None),
        timetoken=100,
    )

    assert duplicate is None
    assert stale is not None
    assert stale.event["type"] == "display.failed"
    assert stale.event["payload"]["reason"] == "stale_or_duplicate_command"


def test_pinned_auto_latest_is_rejected_but_manual_restore_succeeds():
    consumer = PiTestConsumer(session_id="session-1")
    consumer.apply_command(
        event=display_command("evt-show", "display.show_message"),
        timetoken=100,
    )
    consumer.apply_command(
        event=display_command("evt-pin", "display.pin_message"),
        timetoken=101,
    )

    rejected = consumer.apply_command(
        event=display_command(
            "evt-auto",
            "display.show_message",
            message_id="msg-2",
            origin="AUTO_LATEST",
        ),
        timetoken=102,
    )
    restored = consumer.apply_command(
        event=display_command(
            "evt-restore",
            "display.restore_message",
            message_id="msg-3",
            origin="MANUAL_RESTORE",
        ),
        timetoken=103,
    )

    assert rejected is not None
    assert rejected.event["type"] == "display.failed"
    assert restored is not None
    assert restored.event["type"] == "display.restored"
    assert consumer.snapshot().current_message_id == "msg-3"
    assert consumer.snapshot().is_pinned is True


def test_clear_resets_message_and_pin_state():
    consumer = PiTestConsumer(session_id="session-1")
    consumer.apply_command(
        event=display_command("evt-show", "display.show_message"),
        timetoken=100,
    )
    consumer.apply_command(
        event=display_command("evt-pin", "display.pin_message"),
        timetoken=101,
    )

    cleared = consumer.apply_command(
        event=display_command("evt-clear", "display.clear", message_id=None, origin=None),
        timetoken=102,
    )

    assert cleared is not None
    assert cleared.event["type"] == "display.cleared"
    snapshot = consumer.snapshot()
    assert snapshot.current_message_id is None
    assert snapshot.is_pinned is False
