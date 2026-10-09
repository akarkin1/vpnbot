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

    def send_message(self, chat_id, text, reply_markup=None, silent=False) -> Optional[int]:
        """Returns the sent message's id or None if sending failed."""
        payload = {
            "chat_id": chat_id,
            "text": text,
            "parse_mode": "HTML",
            "disable_web_page_preview": True,
            "disable_notification": silent,
        }
        body = self._call("sendMessage", payload, reply_markup)
        if body is None:
            return None
        result = body.get("result")
        message_id = result.get("message_id") if isinstance(result, dict) else None
        if isinstance(message_id, int):
            return message_id
        log.warning("Telegram sendMessage returned no message id")
        return None

    def edit_message(self, chat_id, message_id, text, reply_markup=None) -> bool:
        payload = {
            "chat_id": chat_id,
            "message_id": message_id,
            "text": text,
            "parse_mode": "HTML",
            "disable_web_page_preview": True,
        }
        return self._call("editMessageText", payload, reply_markup) is not None

    def delete_message(self, chat_id, message_id) -> bool:
        payload = {
            "chat_id": chat_id,
            "message_id": message_id,
        }
        return self._call("deleteMessage", payload) is not None

    def _call(self, method: str, payload: Dict[str, Any],
              reply_markup: Optional[str] = None) -> Optional[Dict[str, Any]]:
        """Returns the response body on success, None on any failure."""
        try:
            if reply_markup:
                payload["reply_markup"] = _as_object(reply_markup)
            response = self._session.post(f"{self._base_url}/{method}", json=payload, timeout=TIMEOUT_SECONDS)
            if response.status_code == 200:
                body = response.json()
                if body.get("ok") is True:
                    return body
            log.warning("Telegram %s failed: HTTP %s %s", method, response.status_code, response.text)
        except Exception as e:
            # requests puts the URL (and so the bot token) into its exception messages
            log.warning("Telegram %s failed: %s", method, str(e).replace(self._token, "***"))
        return None


def _as_object(reply_markup) -> Any:
    return json.loads(reply_markup) if isinstance(reply_markup, str) else reply_markup
