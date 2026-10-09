import logging
import os
import signal
import time
from typing import Optional

import requests

from .config import AgentConfig
from .monitor import Action, IdleMonitor
from .notifier import Notifier
from .secrets import get_secret
from .tailscale import Tailscale
from .telegram import TelegramClient

log = logging.getLogger(__name__)

CHECK_IP_URL = "https://checkip.amazonaws.com"
CHECK_IP_TIMEOUT_SECONDS = 5
UNKNOWN_IP = "—"


class _Terminated(BaseException):
    """Raised by the SIGTERM handler; a BaseException so that no `except Exception` swallows it."""


def fetch_public_ip(session=None) -> str:
    try:
        response = (session or requests).get(CHECK_IP_URL, timeout=CHECK_IP_TIMEOUT_SECONDS)
        if response.status_code == 200:
            return response.text.strip() or UNKNOWN_IP
        log.warning("Public IP lookup failed: HTTP %s", response.status_code)
    except Exception as e:
        log.warning("Public IP lookup failed: %s", e)
    return UNKNOWN_IP


def run(env=os.environ) -> int:
    """Runs the node until it is idle for too long or receives SIGTERM; returns the exit code."""
    try:
        config = AgentConfig.from_env(env)
    except ValueError as e:
        log.error("Invalid configuration: %s", e)
        return 1
    try:
        auth_key = get_secret(config.tailscale_secret_id, config.tailscale_secret_region)
    except Exception:
        log.exception("Cannot read the Tailscale auth key")
        return 1

    notifier = Notifier(config, _telegram_client(config))
    log.info("Telegram notifications are %s", "enabled" if notifier.enabled else "disabled")
    tailscale = Tailscale()
    previous_handler = signal.signal(signal.SIGTERM, _on_sigterm)
    try:
        return _run_node(config, auth_key, notifier, tailscale)
    except _Terminated:
        log.info("SIGTERM received, stopping the node")
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        notifier.stopped()
        _shutdown(tailscale)
        return 0
    finally:
        signal.signal(signal.SIGTERM, previous_handler)


def _run_node(config: AgentConfig, auth_key: str, notifier: Notifier, tailscale: Tailscale) -> int:
    tailscale.start_daemon()
    if not tailscale.up(auth_key, config.hostname):
        log.error("Tailscale did not come up, giving up")
        notifier.stopped()
        tailscale.stop_daemon()
        return 1
    notifier.ready(fetch_public_ip())

    monitor = IdleMonitor(config.inactivity_timeout, config.status_check_interval)
    while True:
        time.sleep(config.status_check_interval)
        active_peers = tailscale.active_peer_count()
        action = monitor.observe(active_peers)
        log.info("Active peers: %d, action: %s", active_peers, action.name)
        if action is Action.WARN:
            notifier.idle_warning()
        elif action is Action.STOP:
            log.info("No active peers for %d s, stopping the node", config.inactivity_timeout)
            signal.signal(signal.SIGTERM, signal.SIG_IGN)
            notifier.stopped_idle()
            _shutdown(tailscale)
            return 0


def _telegram_client(config: AgentConfig) -> Optional[TelegramClient]:
    if not config.bot_token_secret_id:
        return None
    try:
        token = get_secret(config.bot_token_secret_id, config.bot_token_secret_region)
    except Exception as e:
        log.warning("Cannot read the bot token, Telegram notifications are disabled: %s", e)
        return None
    return TelegramClient(token, config.telegram_api_base)


def _shutdown(tailscale: Tailscale) -> None:
    tailscale.logout()
    tailscale.stop_daemon()


def _on_sigterm(signum, frame) -> None:
    raise _Terminated()
