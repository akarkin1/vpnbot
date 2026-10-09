import logging
import re
from dataclasses import dataclass
from typing import Mapping, Optional

log = logging.getLogger(__name__)

DEFAULT_TELEGRAM_API_BASE = "https://api.telegram.org"
DEFAULT_INACTIVITY_TIMEOUT = 600
DEFAULT_STATUS_CHECK_INTERVAL = 60


@dataclass(frozen=True)
class AgentConfig:
    hostname: str
    tailscale_secret_id: str
    tailscale_secret_region: str
    inactivity_timeout: int = DEFAULT_INACTIVITY_TIMEOUT
    status_check_interval: int = DEFAULT_STATUS_CHECK_INTERVAL
    bot_token_secret_id: Optional[str] = None
    bot_token_secret_region: Optional[str] = None
    telegram_api_base: str = DEFAULT_TELEGRAM_API_BASE
    chat_id: Optional[str] = None
    message_id: Optional[int] = None
    ready_text: Optional[str] = None
    ready_markup: Optional[str] = None
    idle_warning_text: Optional[str] = None
    stopped_text: Optional[str] = None
    stopped_markup: Optional[str] = None
    stopped_card_text: Optional[str] = None

    @staticmethod
    def from_env(env: Mapping[str, str]) -> "AgentConfig":
        """Reads the configuration; raises ValueError when a required variable is missing."""
        return AgentConfig(
            hostname=_required(env, "TAILSCALE_HOSTNAME"),
            tailscale_secret_id=_required(env, "TAILSCALE_TOKEN_SECRET_ID"),
            tailscale_secret_region=_required(env, "TAILSCALE_TOKEN_SECRET_REGION"),
            inactivity_timeout=int(_optional(env, "INACTIVITY_TIMEOUT") or DEFAULT_INACTIVITY_TIMEOUT),
            status_check_interval=int(_optional(env, "STATUS_CHECK_INTERVAL") or DEFAULT_STATUS_CHECK_INTERVAL),
            bot_token_secret_id=_optional(env, "TG_BOT_TOKEN_SECRET_ID"),
            bot_token_secret_region=_optional(env, "TG_BOT_TOKEN_SECRET_REGION"),
            telegram_api_base=_optional(env, "TG_API_BASE") or DEFAULT_TELEGRAM_API_BASE,
            chat_id=_telegram_id(env, "TG_CHAT_ID"),
            message_id=_int_or_none(_telegram_id(env, "TG_MESSAGE_ID")),
            ready_text=_optional(env, "TG_READY_TEXT"),
            ready_markup=_optional(env, "TG_READY_MARKUP"),
            idle_warning_text=_optional(env, "TG_IDLE_WARNING_TEXT"),
            stopped_text=_optional(env, "TG_STOPPED_TEXT"),
            stopped_markup=_optional(env, "TG_STOPPED_MARKUP"),
            stopped_card_text=_optional(env, "TG_STOPPED_CARD_TEXT"),
        )


def _optional(env: Mapping[str, str], name: str) -> Optional[str]:
    return env.get(name) or None


def _telegram_id(env: Mapping[str, str], name: str) -> Optional[str]:
    """An integer id as a string; blank or invalid is None, which only disables notifications."""
    value = (env.get(name) or "").strip()
    if not value:
        return None
    if not re.fullmatch(r"-?[0-9]+", value):
        log.warning("Ignoring invalid %s %r, Telegram notifications are disabled", name, value)
        return None
    return value


def _int_or_none(value: Optional[str]) -> Optional[int]:
    return int(value) if value is not None else None


def _required(env: Mapping[str, str], name: str) -> str:
    value = _optional(env, name)
    if value is None:
        raise ValueError(f"Environment variable {name} is required")
    return value
