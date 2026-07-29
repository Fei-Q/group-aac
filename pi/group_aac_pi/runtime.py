from __future__ import annotations

import logging
import os
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from pubnub.pnconfiguration import PNConfiguration
from pubnub.pubnub import PubNub

from group_aac_pi.state_machine import (
    DisplayIdentity,
    JsonStateStore,
    PiBindingStateMachine,
    decode_event,
    write_pairing_artifacts,
)


LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True)
class PiConfig:
    publish_key: str
    subscribe_key: str
    display_id: str
    display_name: str
    state_path: Path
    artifacts_dir: Path
    pairing_ttl_ms: int

    @classmethod
    def from_environment(
        cls,
    ) -> "PiConfig":
        load_dotenv()

        publish_key = _required_environment(
            "PUBNUB_PUBLISH_KEY"
        )
        subscribe_key = _required_environment(
            "PUBNUB_SUBSCRIBE_KEY"
        )
        display_id = _required_environment(
            "GROUP_AAC_DISPLAY_ID"
        )
        display_name = _required_environment(
            "GROUP_AAC_DISPLAY_NAME"
        )

        state_path = Path(
            os.getenv(
                "GROUP_AAC_PI_STATE_PATH",
                "runtime/pi_state.json",
            )
        )

        artifacts_dir = Path(
            os.getenv(
                "GROUP_AAC_PI_ARTIFACTS_DIR",
                "runtime",
            )
        )

        pairing_ttl_seconds = int(
            os.getenv(
                "GROUP_AAC_PAIRING_TTL_SECONDS",
                "300",
            )
        )

        if pairing_ttl_seconds <= 0:
            raise ValueError(
                "GROUP_AAC_PAIRING_TTL_SECONDS "
                "must be positive."
            )

        return cls(
            publish_key=publish_key,
            subscribe_key=subscribe_key,
            display_id=display_id,
            display_name=display_name,
            state_path=state_path,
            artifacts_dir=artifacts_dir,
            pairing_ttl_ms=(
                pairing_ttl_seconds * 1000
            ),
        )


class PubNubPiRuntime:
    def __init__(
        self,
        config: PiConfig,
    ):
        self.config = config

        pnconfig = PNConfiguration()
        pnconfig.publish_key = (
            config.publish_key
        )
        pnconfig.subscribe_key = (
            config.subscribe_key
        )
        pnconfig.user_id = config.display_id
        pnconfig.enable_subscribe = True
        pnconfig.ssl = True

        self.pubnub = PubNub(pnconfig)

        self.control_channel = (
            f"display.{config.display_id}.control"
        )
        self.device_events_channel = (
            f"display.{config.display_id}.events"
        )

        self.control_subscription = None
        self.session_subscription = None
        self.active_session_id: str | None = None

        self.stop_event = threading.Event()

        self.machine = PiBindingStateMachine(
            identity=DisplayIdentity(
                display_id=config.display_id,
                display_name=config.display_name,
            ),
            state_store=JsonStateStore(
                config.state_path
            ),
            hooks=self,
            pairing_ttl_ms=(
                config.pairing_ttl_ms
            ),
        )

    def start(self) -> None:
        self.control_subscription = (
            self.pubnub
            .channel(self.control_channel)
            .subscription()
        )

        self.control_subscription.on_message = (
            self._on_control_message
        )
        self.control_subscription.subscribe()

        self.machine.start()
        self._write_current_artifacts()

        LOGGER.info(
            "Pi started: displayId=%s state=%s",
            self.config.display_id,
            self.machine.lifecycle.value,
        )
        LOGGER.info(
            "Control channel: %s",
            self.control_channel,
        )
        LOGGER.info(
            "Device events channel: %s",
            self.device_events_channel,
        )

    def activate_session(
        self,
        session_id: str,
    ) -> None:
        if self.active_session_id == session_id:
            return

        self.deactivate_session()

        channel = (
            f"session.{session_id}.display"
        )

        subscription = (
            self.pubnub
            .channel(channel)
            .subscription()
        )

        subscription.on_message = (
            self._on_session_display_message
        )
        subscription.subscribe()

        self.session_subscription = subscription
        self.active_session_id = session_id

        LOGGER.info(
            "Subscribed to active session display: %s",
            channel,
        )

    def deactivate_session(self) -> None:
        if self.session_subscription is not None:
            self.session_subscription.unsubscribe()
            self.session_subscription = None

        if self.active_session_id is not None:
            LOGGER.info(
                "Unsubscribed from session %s",
                self.active_session_id,
            )

        self.active_session_id = None

    def run_forever(self) -> None:
        try:
            while not self.stop_event.wait(1.0):
                if self.machine.tick():
                    self._write_current_artifacts()
        except KeyboardInterrupt:
            LOGGER.info("Stopping Pi client.")
        finally:
            self.close()

    def close(self) -> None:
        self.stop_event.set()

        self.deactivate_session()

        if self.control_subscription is not None:
            self.control_subscription.unsubscribe()
            self.control_subscription = None

        self.pubnub.unsubscribe_all()

        stop_method = getattr(
            self.pubnub,
            "stop",
            None,
        )

        if callable(stop_method):
            stop_method()

    def _on_control_message(
        self,
        message: Any,
    ) -> None:
        try:
            event = decode_event(message.message)

            reply = self.machine.handle_control_event(
                event
            )

            if reply is not None:
                self._publish(
                    self.device_events_channel,
                    reply,
                )

            self._write_current_artifacts()
        except Exception:
            LOGGER.exception(
                "Unable to process Pi control event."
            )

    def _on_session_display_message(
        self,
        message: Any,
    ) -> None:
        """
        Stage 3C only establishes the session subscription.

        Rendering and session-display acknowledgements remain part of
        the later display-state stage.
        """
        try:
            event = decode_event(message.message)

            LOGGER.info(
                "Received active-session display command: "
                "type=%s eventId=%s",
                event.get("type"),
                event.get("eventId"),
            )
        except Exception:
            LOGGER.exception(
                "Malformed session display command."
            )

    def _publish(
        self,
        channel: str,
        message: dict[str, Any],
    ) -> None:
        envelope = (
            self.pubnub
            .publish()
            .channel(channel)
            .message(message)
            .should_store(True)
            .sync()
        )

        if envelope.status.is_error():
            raise RuntimeError(
                "PubNub publish failed: "
                f"{envelope.status.category}"
            )

        LOGGER.info(
            "Published %s in reply to %s",
            message.get("type"),
            message.get("inReplyToEventId"),
        )

    def _write_current_artifacts(self) -> None:
        write_pairing_artifacts(
            payload=self.machine.pairing_payload(),
            artifacts_dir=(
                self.config.artifacts_dir
            ),
        )


def _required_environment(
    name: str,
) -> str:
    value = os.getenv(name, "").strip()

    if not value:
        raise RuntimeError(
            f"Missing required environment value: {name}"
        )

    return value