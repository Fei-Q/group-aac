from __future__ import annotations

import copy
import json
import os
import secrets
import threading
import time
import uuid
from dataclasses import asdict, dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any, Callable, Mapping, Protocol


PROTOCOL_VERSION = 1
DISPLAY_PAIRING_TYPE = "group-aac-display"
SESSION_INVITATION_TYPE = "group-aac-session"

DISPLAY_BIND_SESSION = "display.bind_session"
DISPLAY_BOUND = "display.bound"
DISPLAY_BIND_FAILED = "display.bind_failed"
DISPLAY_UNBIND_SESSION = "display.unbind_session"
DISPLAY_UNBOUND = "display.unbound"

MAX_PERSISTED_REPLIES = 100


class PiLifecycle(str, Enum):
    IDLE = "IDLE"
    PAIRING_AVAILABLE = "PAIRING_AVAILABLE"
    BINDING = "BINDING"
    BOUND = "BOUND"
    SESSION_ACTIVE = "SESSION_ACTIVE"
    UNBINDING = "UNBINDING"
    ERROR = "ERROR"


class ProtocolError(ValueError):
    pass


@dataclass(frozen=True)
class DisplayIdentity:
    display_id: str
    display_name: str
    protocol_version: int = PROTOCOL_VERSION


@dataclass(frozen=True)
class SessionBinding:
    session_id: str
    join_code: str
    session_name: str
    host_user_id: str
    display_id: str
    display_mode: str
    actual_started_at: int
    expires_at: int

    @classmethod
    def from_invitation(
        cls,
        invitation: Mapping[str, Any],
    ) -> "SessionBinding":
        return cls(
            session_id=_required_string(
                invitation,
                "sessionId",
            ),
            join_code=_normalize_join_code(
                _required_string(
                    invitation,
                    "joinCode",
                )
            ),
            session_name=_required_string(
                invitation,
                "sessionName",
            ),
            host_user_id=_required_string(
                invitation,
                "hostUserId",
            ),
            display_id=_required_string(
                invitation,
                "displayId",
            ),
            display_mode=_required_string(
                invitation,
                "displayMode",
            ),
            actual_started_at=_required_int(
                invitation,
                "actualStartedAt",
            ),
            expires_at=_required_int(
                invitation,
                "expiresAt",
            ),
        )


@dataclass
class PersistedPiState:
    lifecycle: PiLifecycle = PiLifecycle.IDLE
    binding: SessionBinding | None = None
    processed_replies: dict[str, dict[str, Any]] = field(
        default_factory=dict
    )
    current_message_id: str | None = None
    is_pinned: bool = False
    display_mode: str = "AUTO_LATEST"
    command_origin: str | None = None
    processed_display_replies: dict[
        str, dict[str, Any]
    ] = field(default_factory=dict)
    last_error: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "lifecycle": self.lifecycle.value,
            "binding": (
                asdict(self.binding)
                if self.binding is not None
                else None
            ),
            "processedReplies": self.processed_replies,
            "currentMessageId": self.current_message_id,
            "isPinned": self.is_pinned,
            "displayMode": self.display_mode,
            "commandOrigin": self.command_origin,
            "processedDisplayReplies": (
                self.processed_display_replies
            ),
            "lastError": self.last_error,
        }

    @classmethod
    def from_dict(
        cls,
        value: Mapping[str, Any],
    ) -> "PersistedPiState":
        raw_binding = value.get("binding")

        binding = None
        if isinstance(raw_binding, Mapping):
            binding = SessionBinding(
                session_id=str(
                    raw_binding["session_id"]
                ),
                join_code=str(
                    raw_binding["join_code"]
                ),
                session_name=str(
                    raw_binding["session_name"]
                ),
                host_user_id=str(
                    raw_binding["host_user_id"]
                ),
                display_id=str(
                    raw_binding["display_id"]
                ),
                display_mode=str(
                    raw_binding["display_mode"]
                ),
                actual_started_at=int(
                    raw_binding["actual_started_at"]
                ),
                expires_at=int(
                    raw_binding["expires_at"]
                ),
            )

        raw_replies = value.get(
            "processedReplies",
            {},
        )

        replies = (
            dict(raw_replies)
            if isinstance(raw_replies, Mapping)
            else {}
        )
        raw_display_replies = value.get(
            "processedDisplayReplies",
            {},
        )
        display_replies = (
            dict(raw_display_replies)
            if isinstance(
                raw_display_replies,
                Mapping,
            )
            else {}
        )

        return cls(
            lifecycle=PiLifecycle(
                value.get(
                    "lifecycle",
                    PiLifecycle.IDLE.value,
                )
            ),
            binding=binding,
            processed_replies=replies,
            current_message_id=(
                value.get("currentMessageId")
            ),
            is_pinned=bool(
                value.get("isPinned", False)
            ),
            display_mode=str(
                value.get(
                    "displayMode",
                    "AUTO_LATEST",
                )
            ),
            command_origin=value.get(
                "commandOrigin"
            ),
            processed_display_replies=display_replies,
            last_error=value.get("lastError"),
        )


class JsonStateStore:
    def __init__(self, path: Path):
        self.path = path

    def load(self) -> PersistedPiState:
        if not self.path.exists():
            return PersistedPiState()

        value = json.loads(
            self.path.read_text(
                encoding="utf-8"
            )
        )

        if not isinstance(value, Mapping):
            raise ValueError(
                "Pi state file must contain a JSON object."
            )

        return PersistedPiState.from_dict(value)

    def save(
        self,
        state: PersistedPiState,
    ) -> None:
        self.path.parent.mkdir(
            parents=True,
            exist_ok=True,
        )

        temporary_path = self.path.with_suffix(
            self.path.suffix + ".tmp"
        )

        temporary_path.write_text(
            json.dumps(
                state.to_dict(),
                indent=2,
                sort_keys=True,
            ),
            encoding="utf-8",
        )

        os.replace(
            temporary_path,
            self.path,
        )


class SessionSubscriptionHooks(Protocol):
    def activate_session(
        self,
        session_id: str,
    ) -> None:
        ...

    def deactivate_session(self) -> None:
        ...


class PiBindingStateMachine:
    def __init__(
        self,
        identity: DisplayIdentity,
        state_store: JsonStateStore,
        hooks: SessionSubscriptionHooks,
        pairing_ttl_ms: int = 300_000,
        now_provider: Callable[[], int] | None = None,
    ):
        self.identity = identity
        self.state_store = state_store
        self.hooks = hooks
        self.pairing_ttl_ms = pairing_ttl_ms
        self.now_provider = (
            now_provider
            if now_provider is not None
            else lambda: int(time.time() * 1000)
        )

        self._lock = threading.RLock()
        self._state = state_store.load()

        self._pairing_nonce: str | None = None
        self._pairing_expires_at: int | None = None

    @property
    def lifecycle(self) -> PiLifecycle:
        with self._lock:
            return self._state.lifecycle

    @property
    def binding(self) -> SessionBinding | None:
        with self._lock:
            return self._state.binding

    def start(self) -> None:
        with self._lock:
            binding = self._state.binding

            if binding is None:
                self._enter_pairing_available()
                return

            if binding.expires_at <= self.now_provider():
                self._state.binding = None
                self._enter_pairing_available()
                return

            try:
                self.hooks.activate_session(
                    binding.session_id
                )
            except Exception as error:
                self._state.lifecycle = (
                    PiLifecycle.ERROR
                )
                self._state.last_error = str(error)
                self.state_store.save(self._state)
                return

            self._state.lifecycle = (
                PiLifecycle.SESSION_ACTIVE
            )
            self._state.last_error = None
            self.state_store.save(self._state)

    def pairing_payload(
        self,
    ) -> dict[str, Any] | None:
        with self._lock:
            if self._state.binding is not None:
                return None

            self._ensure_pairing_is_current()

            return {
                "type": DISPLAY_PAIRING_TYPE,
                "protocolVersion": (
                    self.identity.protocol_version
                ),
                "displayId": (
                    self.identity.display_id
                ),
                "displayName": (
                    self.identity.display_name
                ),
                "pairingNonce": (
                    self._pairing_nonce
                ),
                "pairingExpiresAt": (
                    self._pairing_expires_at
                ),
            }

    def tick(self) -> bool:
        """
        Refreshes an expired idle pairing nonce.

        Returns True when pairing artifacts should be rewritten.
        """
        with self._lock:
            if self._state.binding is not None:
                return False

            previous_nonce = self._pairing_nonce
            self._ensure_pairing_is_current()

            return previous_nonce != self._pairing_nonce

    def handle_control_event(
        self,
        raw_event: Mapping[str, Any],
    ) -> dict[str, Any] | None:
        with self._lock:
            event_type = raw_event.get("type")

            if event_type not in {
                DISPLAY_BIND_SESSION,
                DISPLAY_UNBIND_SESSION,
            }:
                return None

            event_id = _required_string(
                raw_event,
                "eventId",
            )

            existing_reply = (
                self._state.processed_replies.get(
                    event_id
                )
            )

            if existing_reply is not None:
                return copy.deepcopy(existing_reply)

            session_id = _required_string(
                raw_event,
                "sessionId",
            )

            expires_at = _optional_int(
                raw_event,
                "expiresAt",
            )

            if (
                expires_at is not None
                and expires_at
                <= self.now_provider()
            ):
                return self._record_reply(
                    event_id=event_id,
                    reply=self._build_reply(
                        command=raw_event,
                        event_type=DISPLAY_BIND_FAILED,
                        reason="command_expired",
                    ),
                )

            if event_type == DISPLAY_BIND_SESSION:
                return self._handle_bind(raw_event)

            return self._handle_unbind(raw_event)

    def handle_session_display_event(
        self,
        raw_event: Mapping[str, Any],
    ) -> dict[str, Any] | None:
        with self._lock:
            binding = self._state.binding
            if (
                binding is None
                or self._state.lifecycle
                != PiLifecycle.SESSION_ACTIVE
            ):
                return None

            event_type = raw_event.get("type")
            if event_type not in {
                "display.show_message",
                "display.restore_message",
                "display.pin_message",
                "display.unpin_message",
                "display.clear",
                "display.mode_changed",
            }:
                return None

            event_id = _required_string(
                raw_event,
                "eventId",
            )
            existing_reply = (
                self._state.processed_display_replies.get(
                    event_id
                )
            )
            if existing_reply is not None:
                return copy.deepcopy(existing_reply)

            session_id = _required_string(
                raw_event,
                "sessionId",
            )
            if session_id != binding.session_id:
                return None

            payload = _required_mapping(
                raw_event,
                "payload",
            )

            ack_type = self._apply_display_command(
                event_type=event_type,
                payload=payload,
                session_id=session_id,
            )
            self.state_store.save(self._state)
            return self._record_display_reply(
                event_id=event_id,
                reply=self._build_display_reply(
                    command=raw_event,
                    event_type=ack_type,
                ),
            )

    def _handle_bind(
        self,
        command: Mapping[str, Any],
    ) -> dict[str, Any]:
        event_id = _required_string(
            command,
            "eventId",
        )
        session_id = _required_string(
            command,
            "sessionId",
        )
        payload = _required_mapping(
            command,
            "payload",
        )

        display_id = _required_string(
            payload,
            "displayId",
        )

        if display_id != self.identity.display_id:
            return self._fail(
                command,
                "display_id_mismatch",
            )

        current_binding = self._state.binding

        if current_binding is not None:
            if current_binding.session_id == session_id:
                return self._record_reply(
                    event_id=event_id,
                    reply=self._build_reply(
                        command=command,
                        event_type=DISPLAY_BOUND,
                    ),
                )

            return self._fail(
                command,
                "display_already_bound",
            )

        protocol_version = _required_int(
            payload,
            "protocolVersion",
        )

        if protocol_version != PROTOCOL_VERSION:
            return self._fail(
                command,
                "unsupported_protocol_version",
            )

        self._ensure_pairing_is_current()

        pairing_nonce = _required_string(
            payload,
            "pairingNonce",
        )
        pairing_expires_at = _required_int(
            payload,
            "pairingExpiresAt",
        )

        if pairing_expires_at <= self.now_provider():
            return self._fail(
                command,
                "pairing_expired",
            )

        if pairing_nonce != self._pairing_nonce:
            return self._fail(
                command,
                "pairing_nonce_mismatch",
            )

        if (
            pairing_expires_at
            != self._pairing_expires_at
        ):
            return self._fail(
                command,
                "pairing_expiry_mismatch",
            )

        invitation = _required_mapping(
            payload,
            "session",
        )

        try:
            binding = self._validate_invitation(
                invitation=invitation,
                expected_session_id=session_id,
            )
        except ProtocolError as error:
            return self._fail(
                command,
                str(error),
            )

        self._state.lifecycle = PiLifecycle.BINDING
        self._state.last_error = None
        self.state_store.save(self._state)

        try:
            self.hooks.activate_session(
                binding.session_id
            )
        except Exception as error:
            self._state.lifecycle = (
                PiLifecycle.PAIRING_AVAILABLE
            )
            self._state.last_error = str(error)
            self.state_store.save(self._state)

            return self._fail(
                command,
                "session_subscription_failed",
            )

        self._state.binding = binding
        self._state.lifecycle = (
            PiLifecycle.SESSION_ACTIVE
        )
        self._state.last_error = None

        self._pairing_nonce = None
        self._pairing_expires_at = None

        self.state_store.save(self._state)

        return self._record_reply(
            event_id=event_id,
            reply=self._build_reply(
                command=command,
                event_type=DISPLAY_BOUND,
            ),
        )

    def _handle_unbind(
        self,
        command: Mapping[str, Any],
    ) -> dict[str, Any]:
        event_id = _required_string(
            command,
            "eventId",
        )
        session_id = _required_string(
            command,
            "sessionId",
        )
        payload = _required_mapping(
            command,
            "payload",
        )

        display_id = _required_string(
            payload,
            "displayId",
        )

        if display_id != self.identity.display_id:
            return self._fail(
                command,
                "display_id_mismatch",
            )

        current_binding = self._state.binding

        if current_binding is None:
            self._enter_pairing_available()

            return self._record_reply(
                event_id=event_id,
                reply=self._build_reply(
                    command=command,
                    event_type=DISPLAY_UNBOUND,
                ),
            )

        if current_binding.session_id != session_id:
            return self._fail(
                command,
                "session_mismatch",
            )

        self._state.lifecycle = (
            PiLifecycle.UNBINDING
        )
        self.state_store.save(self._state)

        try:
            self.hooks.deactivate_session()
        except Exception as error:
            self._state.lifecycle = PiLifecycle.ERROR
            self._state.last_error = str(error)
            self.state_store.save(self._state)

            return self._fail(
                command,
                "session_unsubscribe_failed",
            )

        self._state.binding = None
        self._clear_display_state()
        self._state.last_error = None
        self._enter_pairing_available()

        return self._record_reply(
            event_id=event_id,
            reply=self._build_reply(
                command=command,
                event_type=DISPLAY_UNBOUND,
            ),
        )

    def _validate_invitation(
        self,
        invitation: Mapping[str, Any],
        expected_session_id: str,
    ) -> SessionBinding:
        invitation_type = _required_string(
            invitation,
            "type",
        )

        if invitation_type != SESSION_INVITATION_TYPE:
            raise ProtocolError(
                "invalid_session_invitation_type"
            )

        version = _required_int(
            invitation,
            "protocolVersion",
        )

        if version != PROTOCOL_VERSION:
            raise ProtocolError(
                "unsupported_session_protocol_version"
            )

        status = _required_string(
            invitation,
            "status",
        )

        if status != "LIVE":
            raise ProtocolError(
                "session_not_live"
            )

        binding = SessionBinding.from_invitation(
            invitation
        )

        if binding.session_id != expected_session_id:
            raise ProtocolError(
                "session_id_mismatch"
            )

        if (
            binding.display_id
            != self.identity.display_id
        ):
            raise ProtocolError(
                "invitation_display_id_mismatch"
            )

        if binding.expires_at <= self.now_provider():
            raise ProtocolError(
                "session_invitation_expired"
            )

        return binding

    def _fail(
        self,
        command: Mapping[str, Any],
        reason: str,
    ) -> dict[str, Any]:
        event_id = _required_string(
            command,
            "eventId",
        )

        return self._record_reply(
            event_id=event_id,
            reply=self._build_reply(
                command=command,
                event_type=DISPLAY_BIND_FAILED,
                reason=reason,
            ),
        )

    def _build_reply(
        self,
        command: Mapping[str, Any],
        event_type: str,
        reason: str | None = None,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "protocolVersion": PROTOCOL_VERSION,
            "displayId": self.identity.display_id,
            "state": self._state.lifecycle.value,
        }

        if reason is not None:
            payload["reason"] = reason

        return {
            "eventId": str(uuid.uuid4()),
            "type": event_type,
            "sessionId": _required_string(
                command,
                "sessionId",
            ),
            "actorUserId": self.identity.display_id,
            "occurredAt": self.now_provider(),
            "inReplyToEventId": _required_string(
                command,
                "eventId",
            ),
            "payload": payload,
        }

    def _build_display_reply(
        self,
        command: Mapping[str, Any],
        event_type: str,
    ) -> dict[str, Any]:
        binding = self._state.binding
        if binding is None:
            raise ProtocolError("missing_active_binding")

        return {
            "eventId": str(uuid.uuid4()),
            "type": event_type,
            "sessionId": binding.session_id,
            "actorUserId": self.identity.display_id,
            "occurredAt": self.now_provider(),
            "inReplyToEventId": _required_string(
                command,
                "eventId",
            ),
            "payload": {
                "displayState": {
                    "sessionId": binding.session_id,
                    "currentMessageId": (
                        self._state.current_message_id
                    ),
                    "isPinned": self._state.is_pinned,
                    "displayMode": (
                        self._state.display_mode
                    ),
                    "commandOrigin": (
                        self._state.command_origin
                    ),
                }
            },
        }

    def _record_reply(
        self,
        event_id: str,
        reply: dict[str, Any],
    ) -> dict[str, Any]:
        self._state.processed_replies[
            event_id
        ] = copy.deepcopy(reply)

        while (
            len(self._state.processed_replies)
            > MAX_PERSISTED_REPLIES
        ):
            oldest_event_id = next(
                iter(
                    self._state
                    .processed_replies
                )
            )
            del self._state.processed_replies[
                oldest_event_id
            ]

        self.state_store.save(self._state)

        return reply

    def _record_display_reply(
        self,
        event_id: str,
        reply: dict[str, Any],
    ) -> dict[str, Any]:
        self._state.processed_display_replies[
            event_id
        ] = copy.deepcopy(reply)

        while (
            len(
                self._state
                .processed_display_replies
            )
            > MAX_PERSISTED_REPLIES
        ):
            oldest_event_id = next(
                iter(
                    self._state
                    .processed_display_replies
                )
            )
            del self._state.processed_display_replies[
                oldest_event_id
            ]

        self.state_store.save(self._state)
        return reply

    def _apply_display_command(
        self,
        event_type: str,
        payload: Mapping[str, Any],
        session_id: str,
    ) -> str:
        if event_type in {
            "display.show_message",
            "display.restore_message",
        }:
            display = _required_mapping(
                payload,
                "display",
            )
            self._state.current_message_id = (
                _required_string(
                    display,
                    "currentMessageId",
                )
            )
            self._state.is_pinned = _required_bool(
                display,
                "isPinned",
            )
            self._state.display_mode = (
                _required_string(
                    display,
                    "displayMode",
                )
            )
            self._state.command_origin = (
                _required_optional_string(
                    display,
                    "commandOrigin",
                )
            )
            return (
                "display.restored"
                if event_type
                == "display.restore_message"
                else "display.rendered"
            )

        if event_type == "display.pin_message":
            display = _required_mapping(
                payload,
                "display",
            )
            self._state.is_pinned = True
            self._state.display_mode = (
                _required_string(
                    display,
                    "displayMode",
                )
            )
            self._state.command_origin = (
                _required_optional_string(
                    display,
                    "commandOrigin",
                )
            )
            current_message_id = (
                _required_optional_string(
                    display,
                    "currentMessageId",
                )
            )
            if current_message_id is not None:
                self._state.current_message_id = (
                    current_message_id
                )
            return "display.pinned"

        if event_type == "display.unpin_message":
            display = _required_mapping(
                payload,
                "display",
            )
            self._state.is_pinned = False
            self._state.display_mode = (
                _required_string(
                    display,
                    "displayMode",
                )
            )
            self._state.command_origin = (
                _required_optional_string(
                    display,
                    "commandOrigin",
                )
            )
            current_message_id = (
                _required_optional_string(
                    display,
                    "currentMessageId",
                )
            )
            if current_message_id is not None:
                self._state.current_message_id = (
                    current_message_id
                )
            return "display.unpinned"

        if event_type == "display.clear":
            display = _required_mapping(
                payload,
                "display",
            )
            self._state.current_message_id = None
            self._state.is_pinned = False
            self._state.display_mode = (
                _required_string(
                    display,
                    "displayMode",
                )
            )
            self._state.command_origin = (
                _required_optional_string(
                    display,
                    "commandOrigin",
                )
            )
            return "display.cleared"

        if event_type == "display.mode_changed":
            display = _required_mapping(
                payload,
                "display",
            )
            mode_session_id = _required_string(
                display,
                "sessionId",
            )
            if mode_session_id != session_id:
                raise ProtocolError(
                    "session_id_mismatch"
                )
            self._state.display_mode = (
                _required_string(
                    display,
                    "displayMode",
                )
            )
            return "display.state"

        raise ProtocolError("unsupported_command")

    def _clear_display_state(self) -> None:
        self._state.current_message_id = None
        self._state.is_pinned = False
        self._state.display_mode = "AUTO_LATEST"
        self._state.command_origin = None
        self._state.processed_display_replies.clear()

    def _enter_pairing_available(self) -> None:
        self._state.lifecycle = (
            PiLifecycle.PAIRING_AVAILABLE
        )
        self._state.last_error = None

        self._rotate_pairing_nonce()
        self.state_store.save(self._state)

    def _ensure_pairing_is_current(self) -> None:
        now = self.now_provider()

        if (
            self._pairing_nonce is None
            or self._pairing_expires_at is None
            or self._pairing_expires_at <= now
        ):
            self._rotate_pairing_nonce()

    def _rotate_pairing_nonce(self) -> None:
        self._pairing_nonce = (
            secrets.token_urlsafe(18)
        )
        self._pairing_expires_at = (
            self.now_provider()
            + self.pairing_ttl_ms
        )


def write_pairing_artifacts(
    payload: Mapping[str, Any] | None,
    artifacts_dir: Path,
) -> None:
    artifacts_dir.mkdir(
        parents=True,
        exist_ok=True,
    )

    json_path = artifacts_dir / "pairing.json"
    qr_path = artifacts_dir / "pairing_qr.png"

    if payload is None:
        json_path.unlink(missing_ok=True)
        qr_path.unlink(missing_ok=True)
        return

    serialized = json.dumps(
        dict(payload),
        separators=(",", ":"),
        sort_keys=True,
    )

    json_path.write_text(
        json.dumps(
            dict(payload),
            indent=2,
            sort_keys=True,
        ),
        encoding="utf-8",
    )

    import qrcode

    qr = qrcode.make(serialized)
    qr.save(qr_path)


def decode_event(
    raw_message: Any,
) -> Mapping[str, Any]:
    if isinstance(raw_message, Mapping):
        return raw_message

    if isinstance(raw_message, str):
        parsed = json.loads(raw_message)

        if isinstance(parsed, Mapping):
            return parsed

    raise ProtocolError(
        "Realtime message must be a JSON object."
    )


def _required_mapping(
    value: Mapping[str, Any],
    key: str,
) -> Mapping[str, Any]:
    result = value.get(key)

    if not isinstance(result, Mapping):
        raise ProtocolError(
            f"missing_or_invalid_{key}"
        )

    return result


def _required_string(
    value: Mapping[str, Any],
    key: str,
) -> str:
    result = value.get(key)

    if not isinstance(result, str):
        raise ProtocolError(
            f"missing_or_invalid_{key}"
        )

    result = result.strip()

    if not result:
        raise ProtocolError(
            f"missing_or_invalid_{key}"
        )

    return result


def _required_int(
    value: Mapping[str, Any],
    key: str,
) -> int:
    result = value.get(key)

    if isinstance(result, bool):
        raise ProtocolError(
            f"missing_or_invalid_{key}"
        )

    try:
        return int(result)
    except (TypeError, ValueError) as error:
        raise ProtocolError(
            f"missing_or_invalid_{key}"
        ) from error


def _optional_int(
    value: Mapping[str, Any],
    key: str,
) -> int | None:
    result = value.get(key)

    if result is None:
        return None

    return _required_int(value, key)


def _required_bool(
    value: Mapping[str, Any],
    key: str,
) -> bool:
    result = value.get(key)

    if not isinstance(result, bool):
        raise ProtocolError(
            f"missing_or_invalid_{key}"
        )

    return result


def _required_optional_string(
    value: Mapping[str, Any],
    key: str,
) -> str | None:
    result = value.get(key)
    if result is None:
        return None
    return _required_string(value, key)


def _normalize_join_code(
    raw_code: str,
) -> str:
    digits = "".join(
        character
        for character in raw_code
        if character.isdigit()
    )

    if len(digits) != 8:
        raise ProtocolError(
            "invalid_join_code"
        )

    return f"{digits[:4]}-{digits[4:]}"
