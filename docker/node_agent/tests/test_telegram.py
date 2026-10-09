import unittest

from node_agent.telegram import TelegramClient
from tests.fakes import FakeResponse, FakeSession, network_error, posted_json

TOKEN = "123:abc"
MARKUP = '{"inline_keyboard":[[{"text":"Menu","callback_data":"m"}]]}'


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

    def test_returns_true_on_ok_response(self):
        """AC-P3: HTTP 200 with ok=true -> True"""
        self.assertIs(True, self.client.send_message(-1001234, "hi"))
        self.assertIs(True, self.client.edit_message(-1001234, 42, "hi"))

    def test_returns_false_on_http_error(self):
        """AC-P3: HTTP error -> False, no exception"""
        self.session.response = FakeResponse(400, {"ok": False, "description": "Bad Request"})

        self.assertIs(False, self.client.send_message(-1001234, "hi"))
        self.assertIs(False, self.client.edit_message(-1001234, 42, "hi"))

    def test_returns_false_on_http_error_without_json_body(self):
        """AC-P3: HTTP error with a non-JSON body -> False, no exception"""
        self.session.response = FakeResponse(502, text="<html>Bad Gateway</html>")

        self.assertIs(False, self.client.send_message(-1001234, "hi"))

    def test_returns_false_when_telegram_says_not_ok(self):
        """AC-P3: HTTP 200 with "ok": false -> False"""
        self.session.response = FakeResponse(200, {"ok": False, "description": "message is not modified"})

        self.assertIs(False, self.client.send_message(-1001234, "hi"))
        self.assertIs(False, self.client.edit_message(-1001234, 42, "hi"))

    def test_returns_false_on_network_exception(self):
        """AC-P3: network exception -> False, no exception"""
        self.session.error = network_error()

        self.assertIs(False, self.client.send_message(-1001234, "hi"))
        self.assertIs(False, self.client.edit_message(-1001234, 42, "hi"))


if __name__ == "__main__":
    unittest.main()
