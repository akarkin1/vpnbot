import unittest

from node_agent.config import AgentConfig
from node_agent.notifier import Notifier
from node_agent.tests import fakes
from node_agent.tests.fakes import FakeTelegram

READY = "\U0001F7E2 <b>fra-node-1</b> · Frankfurt\n<code>203.0.113.7</code>"
IDLE_WARNING = "⚠️ <b>fra-node-1</b> will stop in 2 minutes"
STOPPED = "\U0001F6D1 <b>fra-node-1</b> was stopped"
STOPPED_CARD = "⚪ <b>fra-node-1</b> · Frankfurt\nStopped"


def config(**overrides):
    """Config from the full env; an override of None removes that variable."""
    env = fakes.full_env()
    for name, value in overrides.items():
        if value is None:
            env.pop(name, None)
        else:
            env[name] = value
    return AgentConfig.from_env(env)


class NotifierTest(unittest.TestCase):

    def setUp(self):
        self.config = config()
        self.telegram = FakeTelegram()
        self.notifier = Notifier(self.config, self.telegram)

    def send(self, text, reply_markup=None, silent=False):
        return ("send_message",
                {"chat_id": self.config.chat_id, "text": text, "reply_markup": reply_markup, "silent": silent})

    def edit(self, text, reply_markup=None):
        return ("edit_message",
                {"chat_id": self.config.chat_id, "message_id": 42, "text": text, "reply_markup": reply_markup})

    # --- render ---

    def test_render_replaces_hostname(self):
        """AC-P4: {{HOSTNAME}} replaced with TAILSCALE_HOSTNAME"""
        self.assertEqual("<b>fra-node-1</b> fra-node-1",
                         self.notifier.render("<b>{{HOSTNAME}}</b> {{HOSTNAME}}"))

    def test_render_replaces_public_ip(self):
        """AC-P4: {{PUBLIC_IP}} replaced with the given public IP"""
        self.assertEqual("fra-node-1 203.0.113.7",
                         self.notifier.render("{{HOSTNAME}} {{PUBLIC_IP}}", public_ip="203.0.113.7"))

    def test_render_keeps_public_ip_placeholder_without_ip(self):
        """AC-P4: {{PUBLIC_IP}} replaced only if public_ip is given"""
        self.assertEqual("fra-node-1 {{PUBLIC_IP}}", self.notifier.render("{{HOSTNAME}} {{PUBLIC_IP}}"))

    # --- enabled ---

    def test_enabled_with_token_chat_and_message(self):
        """AC-P4: enabled with a Telegram client, TG_CHAT_ID and TG_MESSAGE_ID"""
        self.assertTrue(self.notifier.enabled)

    def test_disabled_without_token(self):
        """AC-P4: disabled without a token (no Telegram client); methods are no-ops"""
        notifier = Notifier(self.config, None)

        self.assertFalse(notifier.enabled)
        notifier.ready("203.0.113.7")
        notifier.idle_warning()
        notifier.stopped_idle()
        notifier.stopped()

    def test_disabled_without_chat_id(self):
        """AC-P4: disabled without TG_CHAT_ID; nothing is sent"""
        notifier = Notifier(config(TG_CHAT_ID=None), self.telegram)

        self.assertFalse(notifier.enabled)
        notifier.ready("203.0.113.7")
        notifier.idle_warning()
        notifier.stopped_idle()
        notifier.stopped()
        self.assertEqual([], self.telegram.calls)

    def test_disabled_without_message_id(self):
        """AC-P4: disabled without TG_MESSAGE_ID; nothing is sent"""
        notifier = Notifier(config(TG_MESSAGE_ID=None), self.telegram)

        self.assertFalse(notifier.enabled)
        notifier.ready("203.0.113.7")
        notifier.idle_warning()
        notifier.stopped_idle()
        notifier.stopped()
        self.assertEqual([], self.telegram.calls)

    # --- ready ---

    def test_ready_edits_progress_message_into_card(self):
        """AC-P4: ready edits TG_MESSAGE_ID with TG_READY_TEXT + TG_READY_MARKUP"""
        self.notifier.ready("203.0.113.7")

        self.assertEqual([self.edit(READY, reply_markup=fakes.READY_MARKUP)], self.telegram.calls)

    def test_ready_without_markup(self):
        """AC-P4: ready without TG_READY_MARKUP edits without markup"""
        notifier = Notifier(config(TG_READY_MARKUP=None), self.telegram)

        notifier.ready("203.0.113.7")

        self.assertEqual([self.edit(READY)], self.telegram.calls)

    def test_ready_without_text_is_no_op(self):
        """AC-P4: ready is a no-op without TG_READY_TEXT"""
        Notifier(config(TG_READY_TEXT=None), self.telegram).ready("203.0.113.7")

        self.assertEqual([], self.telegram.calls)

    # --- idle warning ---

    def test_idle_warning_is_sent_silently(self):
        """AC-P4: idle_warning sends TG_IDLE_WARNING_TEXT silently"""
        self.notifier.idle_warning()

        self.assertEqual([self.send(IDLE_WARNING, silent=True)], self.telegram.calls)

    def test_idle_warning_without_text_is_no_op(self):
        """AC-P4: idle_warning is a no-op without TG_IDLE_WARNING_TEXT"""
        Notifier(config(TG_IDLE_WARNING_TEXT=None), self.telegram).idle_warning()

        self.assertEqual([], self.telegram.calls)

    # --- stopped after idle ---

    def test_stopped_idle_sends_message_then_edits_card(self):
        """AC-P4: stopped_idle = send TG_STOPPED_TEXT + markup (not silent), then edit card"""
        self.notifier.stopped_idle()

        self.assertEqual([self.send(STOPPED, reply_markup=fakes.STOPPED_MARKUP, silent=False),
                          self.edit(STOPPED_CARD)], self.telegram.calls)

    def test_stopped_idle_edits_card_even_if_send_fails(self):
        """AC-P4: Telegram errors are swallowed; the card is still edited"""
        telegram = FakeTelegram(result=False)

        Notifier(self.config, telegram).stopped_idle()

        self.assertEqual(["send_message", "edit_message"], [method for method, _ in telegram.calls])

    # --- stopped (SIGTERM) ---

    def test_stopped_only_edits_card(self):
        """AC-P4: SIGTERM -> stopped edits the card with TG_STOPPED_CARD_TEXT only"""
        self.notifier.stopped()

        self.assertEqual([self.edit(STOPPED_CARD)], self.telegram.calls)

    def test_stopped_without_card_text_is_no_op(self):
        """AC-P4: stopped is a no-op without TG_STOPPED_CARD_TEXT"""
        Notifier(config(TG_STOPPED_CARD_TEXT=None), self.telegram).stopped()

        self.assertEqual([], self.telegram.calls)


if __name__ == "__main__":
    unittest.main()
