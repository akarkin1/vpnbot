import json
import logging
from typing import Any, Dict, Optional

import requests

from .config import DEFAULT_TELEGRAM_API_BASE

log = logging.getLogger(__name__)

TIMEOUT_SECONDS = 10


class TelegramClient:
    def __init__(self, token, api_base=DEFAULT_TELEGRAM_API_BASE, session=None):
        self._token = token
        self._base_url = f"{api_base.rstrip('/')}/bot{token}"
        self._session = session or requests.Session()

    def send_message(self, chat_id, text, reply_markup=None, silent=False) -> bool:
        payload = {
            "chat_id": chat_id,
            "text": text,
            "parse_mode": "HTML",
            "disable_web_page_preview": True,
            "disable_notification": silent,
        }
        return self._call("sendMessage", payload, reply_markup)

    def edit_message(self, chat_id, message_id, text, reply_markup=None) -> bool:
        payload = {
            "chat_id": chat_id,
            "message_id": message_id,
            "text": text,
            "parse_mode": "HTML",
            "disable_web_page_preview": True,
        }
        return self._call("editMessageText", payload, reply_markup)

    def _call(self, method: str, payload: Dict[str, Any], reply_markup: Optional[str]) -> bool:
        try:
            if reply_markup:
                payload["reply_markup"] = _as_object(reply_markup)
            response = self._session.post(f"{self._base_url}/{method}", json=payload, timeout=TIMEOUT_SECONDS)
            if response.status_code == 200 and response.json().get("ok") is True:
                return True
            log.warning("Telegram %s failed: HTTP %s %s", method, response.status_code, response.text)
        except Exception as e:
            # requests puts the URL (and so the bot token) into its exception messages
            log.warning("Telegram %s failed: %s", method, str(e).replace(self._token, "***"))
        return False


def _as_object(reply_markup) -> Any:
    return json.loads(reply_markup) if isinstance(reply_markup, str) else reply_markup
