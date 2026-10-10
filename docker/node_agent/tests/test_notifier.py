import unittest

from node_agent.config import AgentConfig
from node_agent.notifier import Notifier
from tests import fakes
from tests.fakes import (LINKS_ROW, MENU_BUTTON, READY_MARKUP_WITH_STOP, TASK_ID, TASK_ID_PLACEHOLDER,
                         FakeTelegram, canonical_markup, markup_json, stop_button)

READY = "\U0001F7E2 <b>fra-node-1</b> · Frankfurt\n<code>203.0.113.7</code>"
IDLE_WARNING = "⚠️ <b>fra-node-1</b> will stop in 2 minutes"
STOPPED = "⚪ <b>fra-node-1</b> · Frankfurt\n\U0001F6D1 Stopped: no devices were connected for 10 minutes."
STOPPED_CARD = "⚪ <b>fra-node-1</b> · Frankfurt\nStopped"
WARNING_ID = FakeTelegram.FIRST_MESSAGE_ID


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

    def delete(self, message_id=WARNING_ID):
        return ("delete_message", {"chat_id": self.config.chat_id, "message_id": message_id})

    def warning_sent(self):
        return self.send(IDLE_WARNING, silent=True)

    def stopped_idle_edit(self):
        return self.edit(STOPPED, reply_markup=canonical_markup(fakes.STOPPED_MARKUP))

    def stopped_card_edit(self):
        return self.edit(STOPPED_CARD, reply_markup=canonical_markup(fakes.STOPPED_CARD_MARKUP))

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

    # --- ready: task id in the Stop button (2b §4.5, §5) ---

    def ready_with_markup(self, markup, *args, **kwargs):
        """Calls `ready` with TG_READY_MARKUP = `markup`; returns the recorded Telegram calls."""
        Notifier(config(TG_READY_MARKUP=markup), self.telegram).ready(*args, **kwargs)
        return self.telegram.calls

    def test_ready_replaces_task_id_placeholder_in_markup(self):
        """AC-P8: {{TASK_ID}} replaced with the task id -> Stop callback_data is STOP:<region>:<task id>"""
        calls = self.ready_with_markup(READY_MARKUP_WITH_STOP, "203.0.113.7", task_id=TASK_ID)

        self.assertEqual([self.edit(READY, reply_markup=markup_json(
            LINKS_ROW, [stop_button(TASK_ID), MENU_BUTTON]))], calls)

    def test_ready_accepts_task_id_as_second_positional_argument(self):
        """AC-P8: `ready(public_ip, task_id)` - task id passed positionally"""
        calls = self.ready_with_markup(READY_MARKUP_WITH_STOP, "203.0.113.7", TASK_ID)

        self.assertEqual([self.edit(READY, reply_markup=markup_json(
            LINKS_ROW, [stop_button(TASK_ID), MENU_BUTTON]))], calls)

    def test_ready_replaces_every_task_id_placeholder(self):
        """AC-P8: every {{TASK_ID}} in the markup is replaced"""
        markup = markup_json([stop_button(TASK_ID_PLACEHOLDER)], [stop_button(TASK_ID_PLACEHOLDER), MENU_BUTTON])

        calls = self.ready_with_markup(markup, "203.0.113.7", task_id=TASK_ID)

        self.assertEqual([self.edit(READY, reply_markup=markup_json(
            [stop_button(TASK_ID)], [stop_button(TASK_ID), MENU_BUTTON]))], calls)

    def test_ready_without_task_id_removes_stop_button_only(self):
        """AC-P8: task_id=None -> Stop button removed; links and Menu untouched"""
        calls = self.ready_with_markup(READY_MARKUP_WITH_STOP, "203.0.113.7", task_id=None)

        self.assertEqual([self.edit(READY, reply_markup=markup_json(LINKS_ROW, [MENU_BUTTON]))], calls)

    def test_ready_task_id_defaults_to_none(self):
        """AC-P8: `ready(public_ip)` without a task id -> Stop button removed"""
        calls = self.ready_with_markup(READY_MARKUP_WITH_STOP, "203.0.113.7")

        self.assertEqual([self.edit(READY, reply_markup=markup_json(LINKS_ROW, [MENU_BUTTON]))], calls)

    def test_ready_without_task_id_drops_rows_left_empty(self):
        """AC-P8: task_id=None -> a row holding only the Stop button is dropped, other rows keep their order"""
        markup = markup_json(LINKS_ROW, [stop_button(TASK_ID_PLACEHOLDER)], [MENU_BUTTON])

        calls = self.ready_with_markup(markup, "203.0.113.7", task_id=None)

        self.assertEqual([self.edit(READY, reply_markup=markup_json(LINKS_ROW, [MENU_BUTTON]))], calls)

    def test_ready_without_task_id_removes_every_button_with_placeholder(self):
        """AC-P8: task_id=None -> every button whose callback_data contains {{TASK_ID}} is removed"""
        other = {"text": "Details", "callback_data": "USE:eu-central-1:" + TASK_ID_PLACEHOLDER}
        markup = markup_json([other, MENU_BUTTON], [stop_button(TASK_ID_PLACEHOLDER)])

        calls = self.ready_with_markup(markup, "203.0.113.7", task_id=None)

        self.assertEqual([self.edit(READY, reply_markup=markup_json([MENU_BUTTON]))], calls)

    def test_ready_without_task_id_and_nothing_left_sends_no_markup(self):
        """AC-P8: task_id=None and only the Stop button -> card edited without reply_markup"""
        calls = self.ready_with_markup(markup_json([stop_button(TASK_ID_PLACEHOLDER)]), "203.0.113.7",
                                       task_id=None)

        self.assertEqual(1, len(calls))
        method, arguments = calls[0]
        self.assertEqual("edit_message", method)
        self.assertEqual(READY, arguments["text"])
        self.assertFalse(arguments["reply_markup"], "no markup is sent when no button is left")

    def test_ready_without_task_id_keeps_markup_without_placeholder(self):
        """AC-P8: task_id=None and no {{TASK_ID}} in the markup -> markup unchanged"""
        markup = markup_json(LINKS_ROW, [MENU_BUTTON])

        calls = self.ready_with_markup(markup, "203.0.113.7", task_id=None)

        self.assertEqual([self.edit(READY, reply_markup=markup)], calls)

    def test_ready_with_task_id_and_no_markup(self):
        """AC-P8: a task id without TG_READY_MARKUP -> card edited without markup"""
        calls = self.ready_with_markup(None, "203.0.113.7", task_id=TASK_ID)

        self.assertEqual([self.edit(READY)], calls)

    # --- idle warning ---

    def test_idle_warning_is_sent_silently(self):
        """AC-P4: idle_warning sends TG_IDLE_WARNING_TEXT silently"""
        self.notifier.idle_warning()

        self.assertEqual([self.send(IDLE_WARNING, silent=True)], self.telegram.calls)

    def test_idle_warning_without_text_is_no_op(self):
        """AC-P4: idle_warning is a no-op without TG_IDLE_WARNING_TEXT"""
        Notifier(config(TG_IDLE_WARNING_TEXT=None), self.telegram).idle_warning()

        self.assertEqual([], self.telegram.calls)

    def test_idle_warning_send_failure_is_swallowed(self):
        """stop-in-place §4.3: a failing or raising warning send does not raise"""
        for telegram in (FakeTelegram(failing={"send_message"}), FakeTelegram(raising={"send_message"})):
            with self.subTest(failing=telegram.failing, raising=telegram.raising):
                Notifier(self.config, telegram).idle_warning()

                self.assertEqual(["send_message"], telegram.methods())

    # --- stopped after idle (stop-in-place §4.3) ---

    def test_stopped_idle_edits_node_message_with_stopped_text_and_markup(self):
        """AC-P4: stopped_idle = one edit of TG_MESSAGE_ID with TG_STOPPED_TEXT + TG_STOPPED_MARKUP"""
        self.notifier.stopped_idle()

        self.assertEqual([self.stopped_idle_edit()], self.telegram.calls)

    def test_stopped_idle_sends_no_message(self):
        """AC-P4: stopped_idle sends no new message, also after a warning"""
        self.notifier.idle_warning()

        self.notifier.stopped_idle()

        self.assertEqual(1, self.telegram.methods().count("send_message"), "only the warning is sent")
        self.assertEqual("edit_message", self.telegram.methods()[-1])

    def test_stopped_idle_deletes_sent_warning_first(self):
        """AC-P4: a sent warning is deleted (by its message id), then the node message is edited"""
        self.notifier.idle_warning()

        self.notifier.stopped_idle()

        self.assertEqual([self.warning_sent(), self.delete(WARNING_ID), self.stopped_idle_edit()],
                         self.telegram.calls)

    def test_stopped_idle_without_warning_deletes_nothing(self):
        """AC-P4: no warning was sent (no TG_IDLE_WARNING_TEXT) -> no delete"""
        notifier = Notifier(config(TG_IDLE_WARNING_TEXT=None), self.telegram)
        notifier.idle_warning()

        notifier.stopped_idle()

        self.assertEqual([self.stopped_idle_edit()], self.telegram.calls)

    def test_stopped_idle_no_delete_when_warning_send_failed(self):
        """AC-P4: the warning send failed (None) or raised -> no delete, the node message is edited"""
        for telegram in (FakeTelegram(failing={"send_message"}), FakeTelegram(raising={"send_message"})):
            with self.subTest(failing=telegram.failing, raising=telegram.raising):
                notifier = Notifier(self.config, telegram)
                notifier.idle_warning()

                notifier.stopped_idle()

                self.assertEqual(["send_message", "edit_message"], telegram.methods())
                self.assertEqual(self.stopped_idle_edit(), telegram.calls[-1])

    def test_stopped_idle_edits_even_if_delete_fails(self):
        """AC-P4: a failing or raising delete never prevents the edit"""
        for telegram in (FakeTelegram(failing={"delete_message"}), FakeTelegram(raising={"delete_message"})):
            with self.subTest(failing=telegram.failing, raising=telegram.raising):
                notifier = Notifier(self.config, telegram)
                notifier.idle_warning()

                notifier.stopped_idle()

                self.assertEqual([self.warning_sent(), self.delete(), self.stopped_idle_edit()], telegram.calls)

    def test_stopped_idle_edit_failure_is_swallowed(self):
        """AC-P4: every Telegram call is wrapped; a raising edit does not raise"""
        for telegram in (FakeTelegram(result=False), FakeTelegram(raising={"send_message", "delete_message",
                                                                             "edit_message"})):
            with self.subTest(result=telegram.result, raising=telegram.raising):
                notifier = Notifier(self.config, telegram)
                notifier.idle_warning()

                notifier.stopped_idle()

                self.assertEqual("edit_message", telegram.methods()[-1])

    def test_stopped_idle_without_stopped_markup(self):
        """AC-P4: TG_STOPPED_TEXT without TG_STOPPED_MARKUP -> edit without markup"""
        Notifier(config(TG_STOPPED_MARKUP=None), self.telegram).stopped_idle()

        self.assertEqual([self.edit(STOPPED)], self.telegram.calls)

    def test_stopped_idle_falls_back_to_stopped_card(self):
        """AC-P4: without TG_STOPPED_TEXT -> stopped(): card text + card markup"""
        Notifier(config(TG_STOPPED_TEXT=None), self.telegram).stopped_idle()

        self.assertEqual([self.stopped_card_edit()], self.telegram.calls)

    def test_stopped_idle_fallback_deletes_warning_once(self):
        """AC-P4: without TG_STOPPED_TEXT, a sent warning is deleted once, then the card is edited"""
        notifier = Notifier(config(TG_STOPPED_TEXT=None), self.telegram)
        notifier.idle_warning()

        notifier.stopped_idle()

        self.assertEqual([self.warning_sent(), self.delete(), self.stopped_card_edit()], self.telegram.calls)

    # --- stopped (🛑 tap / SIGTERM, Tailscale failure) ---

    def test_stopped_edits_card_with_card_text_and_markup(self):
        """AC-P5: stopped edits TG_MESSAGE_ID with TG_STOPPED_CARD_TEXT + TG_STOPPED_CARD_MARKUP"""
        self.notifier.stopped()

        self.assertEqual([self.stopped_card_edit()], self.telegram.calls)

    def test_stopped_without_card_markup_edits_without_markup(self):
        """AC-P5: no TG_STOPPED_CARD_MARKUP (Lambda before stop-in-place) -> edit without markup"""
        Notifier(config(TG_STOPPED_CARD_MARKUP=None), self.telegram).stopped()

        self.assertEqual([self.edit(STOPPED_CARD)], self.telegram.calls)

    def test_stopped_deletes_sent_warning_first(self):
        """AC-P5: a sent warning is deleted, then the card is edited"""
        self.notifier.idle_warning()

        self.notifier.stopped()

        self.assertEqual([self.warning_sent(), self.delete(), self.stopped_card_edit()], self.telegram.calls)

    def test_stopped_no_delete_when_warning_send_failed(self):
        """AC-P5: the warning send failed -> no delete"""
        telegram = FakeTelegram(failing={"send_message"})
        notifier = Notifier(self.config, telegram)
        notifier.idle_warning()

        notifier.stopped()

        self.assertEqual(["send_message", "edit_message"], telegram.methods())

    def test_stopped_edits_even_if_delete_fails(self):
        """AC-P5: a failing or raising delete never prevents the edit"""
        for telegram in (FakeTelegram(failing={"delete_message"}), FakeTelegram(raising={"delete_message"})):
            with self.subTest(failing=telegram.failing, raising=telegram.raising):
                notifier = Notifier(self.config, telegram)
                notifier.idle_warning()

                notifier.stopped()

                self.assertEqual([self.warning_sent(), self.delete(), self.stopped_card_edit()], telegram.calls)

    def test_stopped_without_card_text_is_no_op(self):
        """AC-P5: stopped makes no call without TG_STOPPED_CARD_TEXT (and no warning)"""
        Notifier(config(TG_STOPPED_CARD_TEXT=None), self.telegram).stopped()

        self.assertEqual([], self.telegram.calls)

    def test_stopped_without_card_text_still_deletes_warning(self):
        """AC-P5/§1.2: the warning is deleted when the node stops, even without TG_STOPPED_CARD_TEXT"""
        notifier = Notifier(config(TG_STOPPED_CARD_TEXT=None), self.telegram)
        notifier.idle_warning()

        notifier.stopped()

        self.assertEqual([self.warning_sent(), self.delete()], self.telegram.calls)

    # --- activity resumed (stop-in-place §4.3) ---

    def test_activity_resumed_deletes_warning(self):
        """AC-P6: activity_resumed deletes the sent warning, nothing else"""
        self.notifier.idle_warning()

        self.notifier.activity_resumed()

        self.assertEqual([self.warning_sent(), self.delete(WARNING_ID)], self.telegram.calls)

    def test_activity_resumed_twice_deletes_once(self):
        """AC-P6: a second activity_resumed does nothing"""
        self.notifier.idle_warning()

        self.notifier.activity_resumed()
        self.notifier.activity_resumed()

        self.assertEqual([self.warning_sent(), self.delete()], self.telegram.calls)

    def test_activity_resumed_without_warning_does_nothing(self):
        """AC-P6: no warning sent -> no Telegram call"""
        self.notifier.activity_resumed()

        self.assertEqual([], self.telegram.calls)

    def test_activity_resumed_after_failed_warning_does_nothing(self):
        """AC-P6: the warning send failed (None) or raised -> no delete"""
        for telegram in (FakeTelegram(failing={"send_message"}), FakeTelegram(raising={"send_message"})):
            with self.subTest(failing=telegram.failing, raising=telegram.raising):
                notifier = Notifier(self.config, telegram)
                notifier.idle_warning()

                notifier.activity_resumed()

                self.assertEqual(["send_message"], telegram.methods())

    def test_activity_resumed_forgets_warning_even_if_delete_fails(self):
        """AC-P6: a failing or raising delete is swallowed and the warning is forgotten"""
        for telegram in (FakeTelegram(failing={"delete_message"}), FakeTelegram(raising={"delete_message"})):
            with self.subTest(failing=telegram.failing, raising=telegram.raising):
                notifier = Notifier(self.config, telegram)
                notifier.idle_warning()

                notifier.activity_resumed()
                notifier.activity_resumed()

                self.assertEqual(["send_message", "delete_message"], telegram.methods())

    def test_stop_after_activity_resumed_deletes_nothing(self):
        """AC-P6: the deleted warning is forgotten -> a later stop only edits the node message"""
        self.notifier.idle_warning()
        self.notifier.activity_resumed()

        self.notifier.stopped_idle()

        self.assertEqual([self.warning_sent(), self.delete(), self.stopped_idle_edit()], self.telegram.calls)

    def test_next_warning_is_deleted_by_its_own_id(self):
        """AC-P6: warn, resume, warn again, stop -> each warning is deleted by its own message id"""
        self.notifier.idle_warning()
        self.notifier.activity_resumed()
        self.notifier.idle_warning()

        self.notifier.stopped_idle()

        self.assertEqual([self.warning_sent(), self.delete(WARNING_ID),
                          self.warning_sent(), self.delete(WARNING_ID + 1),
                          self.stopped_idle_edit()], self.telegram.calls)

    # --- disabled notifier (stop-in-place) ---

    def test_disabled_notifier_makes_no_telegram_calls(self):
        """AC-P8: no token / chat id / message id -> warning, resume and both stops make no Telegram call"""
        cases = (
            ("no token", self.config, None),
            ("no chat id", config(TG_CHAT_ID=None), self.telegram),
            ("no message id", config(TG_MESSAGE_ID=None), self.telegram),
            ("invalid message id", config(TG_MESSAGE_ID="null"), self.telegram),
        )
        for name, agent_config, telegram in cases:
            with self.subTest(name):
                notifier = Notifier(agent_config, telegram)

                notifier.idle_warning()
                notifier.activity_resumed()
                notifier.idle_warning()
                notifier.stopped_idle()
                notifier.stopped()

                self.assertFalse(notifier.enabled)
                self.assertEqual([], self.telegram.calls)


if __name__ == "__main__":
    unittest.main()
