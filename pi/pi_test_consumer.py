from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(slots=True)
class DisplaySnapshot:
    session_id: str
    current_message_id: str | None = None
    is_pinned: bool = False
    last_command_timetoken: int | None = None


@dataclass(slots=True)
class PublishedAcknowledgement:
    channel: str
    event: dict[str, Any]


@dataclass(slots=True)
class PiTestConsumer:
    session_id: str
    display_id: str = "pi-test-consumer"
    current_message_id: str | None = None
    is_pinned: bool = False
    last_command_timetoken: int | None = None
    processed_event_ids: set[str] = field(default_factory=set)

    def display_channel(self) -> str:
        return f"session.{self.session_id}.display"

    def event_channel(self) -> str:
        return f"session.{self.session_id}.display.events"

    def apply_command(
        self,
        *,
        event: dict[str, Any],
        timetoken: int,
    ) -> PublishedAcknowledgement | None:
        event_id = event["eventId"]
        event_type = event["type"]

        if event_id in self.processed_event_ids:
            return None

        if self.last_command_timetoken is not None and timetoken <= self.last_command_timetoken:
            return self._acknowledgement(
                event_type="display.failed",
                in_reply_to_event_id=event_id,
                timetoken=timetoken,
                reason="stale_or_duplicate_command",
            )

        self.processed_event_ids.add(event_id)
        payload = event.get("payload", {})
        self.last_command_timetoken = timetoken

        if event_type in {"display.show_message", "display.restore_message"}:
            origin = payload.get("commandOrigin")
            message_id = payload.get("message", {}).get("messageId") or payload.get("messageId")
            if self.is_pinned and origin == "AUTO_LATEST":
                return self._acknowledgement(
                    event_type="display.failed",
                    in_reply_to_event_id=event_id,
                    timetoken=timetoken,
                    reason="pinned_auto_latest_rejected",
                )
            self.current_message_id = message_id
            ack_type = "display.restored" if event_type == "display.restore_message" else "display.rendered"
            return self._acknowledgement(
                event_type=ack_type,
                in_reply_to_event_id=event_id,
                timetoken=timetoken,
            )

        if event_type == "display.pin_message":
            self.is_pinned = True
            self.current_message_id = payload.get("currentMessageId", self.current_message_id)
            return self._acknowledgement(
                event_type="display.pinned",
                in_reply_to_event_id=event_id,
                timetoken=timetoken,
            )

        if event_type == "display.unpin_message":
            self.is_pinned = False
            self.current_message_id = payload.get("currentMessageId", self.current_message_id)
            return self._acknowledgement(
                event_type="display.unpinned",
                in_reply_to_event_id=event_id,
                timetoken=timetoken,
            )

        if event_type == "display.clear":
            self.current_message_id = None
            self.is_pinned = False
            return self._acknowledgement(
                event_type="display.cleared",
                in_reply_to_event_id=event_id,
                timetoken=timetoken,
            )

        if event_type == "display.mode_changed":
            return self._acknowledgement(
                event_type="display.state",
                in_reply_to_event_id=event_id,
                timetoken=timetoken,
            )

        return self._acknowledgement(
            event_type="display.failed",
            in_reply_to_event_id=event_id,
            timetoken=timetoken,
            reason="unsupported_command",
        )

    def snapshot(self) -> DisplaySnapshot:
        return DisplaySnapshot(
            session_id=self.session_id,
            current_message_id=self.current_message_id,
            is_pinned=self.is_pinned,
            last_command_timetoken=self.last_command_timetoken,
        )

    def _acknowledgement(
        self,
        *,
        event_type: str,
        in_reply_to_event_id: str,
        timetoken: int,
        reason: str | None = None,
    ) -> PublishedAcknowledgement:
        payload: dict[str, Any] = {
            "displayState": {
                "sessionId": self.session_id,
                "currentMessageId": self.current_message_id,
                "isPinned": self.is_pinned,
            }
        }
        if reason is not None:
            payload["reason"] = reason
        return PublishedAcknowledgement(
            channel=self.event_channel(),
            event={
                "eventId": f"ack-{in_reply_to_event_id}",
                "type": event_type,
                "sessionId": self.session_id,
                "actorUserId": self.display_id,
                "occurredAt": timetoken,
                "inReplyToEventId": in_reply_to_event_id,
                "payload": payload,
            },
        )
