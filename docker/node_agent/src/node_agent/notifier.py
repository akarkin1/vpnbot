import html
import logging
from typing import Callable, Optional

from .config import AgentConfig
from .telegram import TelegramClient

log = logging.getLogger(__name__)

HOSTNAME_PLACEHOLDER = "{{HOSTNAME}}"
PUBLIC_IP_PLACEHOLDER = "{{PUBLIC_IP}}"


class Notifier:
    """Edits the node card and sends node messages; the texts come pre-rendered from the Lambda."""

    def __init__(self, config: AgentConfig, telegram: Optional[TelegramClient]):
        self._config = config
        self._telegram = telegram

    @property
    def enabled(self) -> bool:
        return (self._telegram is not None
                and bool(self._config.chat_id)
                and self._config.message_id is not None)

    def render(self, text, public_ip=None) -> str:
        # Texts are sent with HTML parse mode, so the values are escaped.
        text = text.replace(HOSTNAME_PLACEHOLDER, html.escape(self._config.hostname))
        if public_ip is not None:
            text = text.replace(PUBLIC_IP_PLACEHOLDER, html.escape(public_ip))
        return text

    def ready(self, public_ip) -> None:
        text = self._config.ready_text
        if self.enabled and text:
            self._safely(lambda: self._telegram.edit_message(
                self._config.chat_id, self._config.message_id, self.render(text, public_ip),
                reply_markup=self._config.ready_markup))

    def idle_warning(self) -> None:
        text = self._config.idle_warning_text
        if self.enabled and text:
            self._safely(lambda: self._telegram.send_message(
                self._config.chat_id, self.render(text), silent=True))

    def stopped_idle(self) -> None:
        text = self._config.stopped_text
        if self.enabled and text:
            self._safely(lambda: self._telegram.send_message(
                self._config.chat_id, self.render(text), reply_markup=self._config.stopped_markup))
        self.stopped()

    def stopped(self) -> None:
        text = self._config.stopped_card_text
        if self.enabled and text:
            self._safely(lambda: self._telegram.edit_message(
                self._config.chat_id, self._config.message_id, self.render(text)))

    @staticmethod
    def _safely(call: Callable[[], object]) -> None:
        try:
            call()
        except Exception:
            log.exception("Telegram notification failed")
