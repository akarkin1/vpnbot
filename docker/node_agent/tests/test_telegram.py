import contextlib
import logging
import unittest

from node_agent.telegram import TelegramClient
from tests.fakes import FakeResponse, FakeSession, network_error, posted_json

TOKEN = "123:abc"
MARKUP = '{"inline_keyboard":[[{"text":"Menu","callback_data":"m"}]]}'


def sent_message_response(message_id):
    """Telegram's answer to sendMessage: the sent Message object."""
    return FakeResponse(200, {"ok": True, "result": {
        "message_id": message_id, "chat": {"id": -1001234, "type": "group"}, "date": 1700000000, "text": "hi"}})


@contextlib.contextmanager
def captured_logs():
    """Every record logged inside the block (any logger, any level), formatted with its traceback."""
    logs = []
    formatter = logging.Formatter("%(name)s %(levelname)s %(message)s")
    handler = logging.Handler(logging.DEBUG)
    handler.emit = lambda record: logs.append(formatter.format(record))
    root = logging.getLogger()
    level = root.level
    root.addHandler(handler)
    root.setLevel(logging.DEBUG)
    try:
        yield logs
    finally:
        root.removeHandler(handler)
        root.setLevel(level)


class TelegramClientTest(unittest.TestCase):

    def setUp(self):
        self.session = FakeSession()
        self.client = TelegramClient(TOKEN, api_base="http://telegram.local", session=self.session)

    def only_post(self):
        self.assertEqual(1, len(self.session.posts))
        return self.session.posts[0]

    def test_send_message_url(self):
        """AC-P3: sendMessage is posted to {api_base}/bot{token}/sendMessage"""
        self.client.send_message(-1001234, "hi")

        url, _ = self.only_post()
        self.assertEqual("http://telegram.local/bot123:abc/sendMessage", url)

    def test_default_api_base(self):
        """AC-P3: api_base defaults to https://api.telegram.org"""
        session = FakeSession()

        TelegramClient(TOKEN, session=session).send_message(-1001234, "hi")

        self.assertEqual("https://api.telegram.org/bot123:abc/sendMessage", session.posts[0][0])

    def test_request_timeout_is_10_seconds(self):
        """AC-P3: requests use a 10 s timeout"""
        self.client.send_message(-1001234, "hi")

        _, kwargs = self.only_post()
        self.assertEqual(10, kwargs.get("timeout"))

    def test_send_message_payload(self):
        """AC-P3: sendMessage payload per §5"""
        self.client.send_message(-1001234, "<b>hi</b>")

        _, kwargs = self.only_post()
        self.assertEqual({
            "chat_id": -1001234,
            "text": "<b>hi</b>",
            "parse_mode": "HTML",
            "disable_web_page_preview": True,
            "disable_notification": False,
        }, posted_json(kwargs))

    def test_send_message_silent(self):
        """AC-P3: silent=True -> disable_notification true"""
        self.client.send_message(-1001234, "hi", silent=True)

        _, kwargs = self.only_post()
        self.assertIs(True, posted_json(kwargs)["disable_notification"])

    def test_send_message_reply_markup_is_sent_as_object(self):
        """AC-P3: reply_markup JSON string is sent as an object"""
        self.client.send_message(-1001234, "hi", reply_markup=MARKUP)

        _, kwargs = self.only_post()
        self.assertEqual({"inline_keyboard": [[{"text": "Menu", "callback_data": "m"}]]},
                         posted_json(kwargs)["reply_markup"])

    def test_edit_message_url_and_payload(self):
        """AC-P3: editMessageText URL and payload per §5"""
        self.client.edit_message(-1001234, 42, "<b>card</b>")

        url, kwargs = self.only_post()
        self.assertEqual("http://telegram.local/bot123:abc/editMessageText", url)
        self.assertEqual({
            "chat_id": -1001234,
            "message_id": 42,
            "text": "<b>card</b>",
            "parse_mode": "HTML",
            "disable_web_page_preview": True,
        }, posted_json(kwargs))

    def test_edit_message_reply_markup_is_sent_as_object(self):
        """AC-P3: editMessageText reply_markup is sent as an object"""
        self.client.edit_message(-1001234, 42, "card", reply_markup=MARKUP)

        _, kwargs = self.only_post()
        self.assertEqual({"inline_keyboard": [[{"text": "Menu", "callback_data": "m"}]]},
                         posted_json(kwargs)["reply_markup"])

    # --- send_message result (stop-in-place §4.3) ---

    def test_send_message_returns_sent_message_id(self):
        """AC-P1: HTTP 200 with ok=true -> result.message_id as an int"""
        self.session.response = sent_message_response(777)

        message_id = self.client.send_message(-1001234, "hi")

        self.assertEqual(777, message_id)
        self.assertIsInstance(message_id, int)

    def test_send_message_returns_none_on_http_error(self):
        """AC-P1: HTTP error -> None, no exception"""
        self.session.response = FakeResponse(400, {"ok": False, "description": "Bad Request"})

        self.assertIsNone(self.client.send_message(-1001234, "hi"))

    def test_send_message_returns_none_on_http_error_with_message_id(self):
        """AC-P1: HTTP error -> None even if the body has a message id"""
        self.session.response = FakeResponse(500, {"ok": True, "result": {"message_id": 777}})

        self.assertIsNone(self.client.send_message(-1001234, "hi"))

    def test_send_message_returns_none_on_http_error_without_json_body(self):
        """AC-P1: HTTP error with a non-JSON body -> None, no exception"""
        self.session.response = FakeResponse(502, text="<html>Bad Gateway</html>")

        self.assertIsNone(self.client.send_message(-1001234, "hi"))

    def test_send_message_returns_none_when_telegram_says_not_ok(self):
        """AC-P1: HTTP 200 with "ok": false -> None"""
        self.session.response = FakeResponse(200, {"ok": False, "description": "chat not found"})

        self.assertIsNone(self.client.send_message(-1001234, "hi"))

    def test_send_message_returns_none_on_network_exception(self):
        """AC-P1: network exception -> None, no exception"""
        self.session.error = network_error()

        self.assertIsNone(self.client.send_message(-1001234, "hi"))

    def test_send_message_returns_none_without_message_id(self):
        """AC-P1: ok=true but no result.message_id -> None, no exception"""
        for body in ({"ok": True}, {"ok": True, "result": {}}, {"ok": True, "result": {"chat": {"id": 1}}}):
            with self.subTest(body=body):
                self.session.response = FakeResponse(200, body)

                self.assertIsNone(self.client.send_message(-1001234, "hi"))

    def test_send_message_returns_none_on_ok_response_without_json_body(self):
        """AC-P1: HTTP 200 with a non-JSON body -> None, no exception"""
        self.session.response = FakeResponse(200, text="<html>ok</html>")

        self.assertIsNone(self.client.send_message(-1001234, "hi"))

    # --- edit_message result (unchanged) ---

    def test_edit_message_returns_true_on_ok_response(self):
        """AC-P3 (2a): editMessageText HTTP 200 with ok=true -> True"""
        self.assertIs(True, self.client.edit_message(-1001234, 42, "hi"))

    def test_edit_message_returns_false_on_failure(self):
        """AC-P3 (2a): editMessageText HTTP error, ok=false or exception -> False, no exception"""
        failures = (
            ("http error", FakeResponse(400, {"ok": False, "description": "Bad Request"}), None),
            ("not ok", FakeResponse(200, {"ok": False, "description": "message is not modified"}), None),
            ("exception", None, network_error()),
        )
        for name, response, error in failures:
            with self.subTest(name):
                session = FakeSession(response, error)
                client = TelegramClient(TOKEN, api_base="http://telegram.local", session=session)

                self.assertIs(False, client.edit_message(-1001234, 42, "hi"))

    # --- delete_message (stop-in-place §4.3) ---

    def test_delete_message_url_and_payload(self):
        """AC-P2: deleteMessage is posted to {api_base}/bot{token}/deleteMessage with chat_id and message_id"""
        self.client.delete_message(-1001234, 100)

        url, kwargs = self.only_post()
        self.assertEqual("http://telegram.local/bot123:abc/deleteMessage", url)
        self.assertEqual({"chat_id": -1001234, "message_id": 100}, posted_json(kwargs))

    def test_delete_message_request_timeout_is_10_seconds(self):
        """AC-P2: deleteMessage uses the same 10 s timeout"""
        self.client.delete_message(-1001234, 100)

        _, kwargs = self.only_post()
        self.assertEqual(10, kwargs.get("timeout"))

    def test_delete_message_returns_true_on_success(self):
        """AC-P2: HTTP 200 with ok=true -> True"""
        self.session.response = FakeResponse(200, {"ok": True, "result": True})

        self.assertIs(True, self.client.delete_message(-1001234, 100))

    def test_delete_message_returns_false_on_http_error(self):
        """AC-P2: HTTP error -> False, no exception"""
        self.session.response = FakeResponse(400, {"ok": False, "description": "message to delete not found"})

        self.assertIs(False, self.client.delete_message(-1001234, 100))

    def test_delete_message_returns_false_on_http_error_without_json_body(self):
        """AC-P2: HTTP error with a non-JSON body -> False, no exception"""
        self.session.response = FakeResponse(502, text="<html>Bad Gateway</html>")

        self.assertIs(False, self.client.delete_message(-1001234, 100))

    def test_delete_message_returns_false_when_telegram_says_not_ok(self):
        """AC-P2: HTTP 200 with "ok": false -> False"""
        self.session.response = FakeResponse(200, {"ok": False, "description": "message can't be deleted"})

        self.assertIs(False, self.client.delete_message(-1001234, 100))

    def test_delete_message_returns_false_on_network_exception(self):
        """AC-P2: network exception -> False, no exception"""
        self.session.error = network_error()

        self.assertIs(False, self.client.delete_message(-1001234, 100))

    # --- the bot token is never logged ---

    def test_token_is_not_logged_when_a_call_raises(self):
        """AC-P2: an exception whose message holds the URL (and so the token) is logged without the token"""
        self.session.error = network_error(
            "HTTPConnectionPool(host='telegram.local', port=80): Max retries exceeded with url: "
            "/bot123:abc/deleteMessage (Caused by NewConnectionError('connection refused'))")
        calls = (
            ("delete_message", lambda: self.client.delete_message(-1001234, 100)),
            ("send_message", lambda: self.client.send_message(-1001234, "hi")),
            ("edit_message", lambda: self.client.edit_message(-1001234, 42, "hi")),
        )
        for name, call in calls:
            with self.subTest(name), captured_logs() as logs:
                call()

                self.assertTrue(logs, "the failure is logged")
                self.assertNotIn(TOKEN, "\n".join(logs))

    def test_token_is_not_logged_on_http_error(self):
        """AC-P2: deleteMessage HTTP error is logged without the token"""
        self.session.response = FakeResponse(401, {"ok": False, "description": "Unauthorized"})

        with captured_logs() as logs:
            self.client.delete_message(-1001234, 100)

        self.assertTrue(logs, "the failure is logged")
        self.assertNotIn(TOKEN, "\n".join(logs))


if __name__ == "__main__":
    unittest.main()
