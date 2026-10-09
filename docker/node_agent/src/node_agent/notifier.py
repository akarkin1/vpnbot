import html
import json
import logging
from typing import Callable, Optional

from .config import AgentConfig
from .telegram import TelegramClient

log = logging.getLogger(__name__)

HOSTNAME_PLACEHOLDER = "{{HOSTNAME}}"
PUBLIC_IP_PLACEHOLDER = "{{PUBLIC_IP}}"
TASK_ID_PLACEHOLDER = "{{TASK_ID}}"


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

    def ready(self, public_ip, task_id=None) -> None:
        text = self._config.ready_text
        if self.enabled and text:
            self._safely(lambda: self._telegram.edit_message(
                self._config.chat_id, self._config.message_id, self.render(text, public_ip),
                reply_markup=self._ready_markup(task_id)))

    def _ready_markup(self, task_id) -> Optional[str]:
        """TG_READY_MARKUP with the task id filled in; without a task id, its buttons are removed."""
        markup = self._config.ready_markup
        if not markup or TASK_ID_PLACEHOLDER not in markup:
            return markup
        if task_id:
            return markup.replace(TASK_ID_PLACEHOLDER, task_id)
        keyboard = json.loads(markup)
        rows = [[button for button in row if TASK_ID_PLACEHOLDER not in button.get("callback_data", "")]
                for row in keyboard.get("inline_keyboard", [])]
        keyboard["inline_keyboard"] = [row for row in rows if row]
        return json.dumps(keyboard) if keyboard["inline_keyboard"] else None

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
