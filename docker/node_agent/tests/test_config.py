import unittest

from node_agent.config import AgentConfig
from node_agent.tests import fakes

OPTIONAL_FIELDS = (
    "bot_token_secret_id", "bot_token_secret_region", "chat_id", "message_id",
    "ready_text", "ready_markup", "idle_warning_text", "stopped_text", "stopped_markup",
    "stopped_card_text",
)


class AgentConfigTest(unittest.TestCase):

    def test_required_vars(self):
        """AC-P5: required vars are read"""
        config = AgentConfig.from_env(fakes.required_env())

        self.assertEqual("fra-node-1", config.hostname)
        self.assertEqual("ts-secret", config.tailscale_secret_id)
        self.assertEqual("eu-central-1", config.tailscale_secret_region)

    def test_defaults(self):
        """AC-P5: defaults for timeout, interval and API base"""
        config = AgentConfig.from_env(fakes.required_env())

        self.assertEqual(600, config.inactivity_timeout)
        self.assertEqual(60, config.status_check_interval)
        self.assertEqual("https://api.telegram.org", config.telegram_api_base)

    def test_optional_vars_are_none_when_absent(self):
        """AC-P5: optional vars None when absent"""
        config = AgentConfig.from_env(fakes.required_env())

        for field in OPTIONAL_FIELDS:
            with self.subTest(field=field):
                self.assertIsNone(getattr(config, field))

    def test_ints_are_parsed(self):
        """AC-P5: INACTIVITY_TIMEOUT, STATUS_CHECK_INTERVAL and TG_MESSAGE_ID parsed as int"""
        config = AgentConfig.from_env(fakes.full_env())

        self.assertEqual((900, 30, 42),
                         (config.inactivity_timeout, config.status_check_interval, config.message_id))
        for value in (config.inactivity_timeout, config.status_check_interval, config.message_id):
            self.assertIsInstance(value, int)

    def test_optional_vars_are_read(self):
        """AC-P5: optional vars are read when present"""
        config = AgentConfig.from_env(fakes.full_env())

        self.assertEqual("tg-secret", config.bot_token_secret_id)
        self.assertEqual("eu-west-1", config.bot_token_secret_region)
        self.assertEqual("http://telegram.local", config.telegram_api_base)
        self.assertEqual("-1001234", str(config.chat_id))
        self.assertEqual(fakes.READY_TEXT, config.ready_text)
        self.assertEqual(fakes.READY_MARKUP, config.ready_markup)
        self.assertEqual(fakes.IDLE_WARNING_TEXT, config.idle_warning_text)
        self.assertEqual(fakes.STOPPED_TEXT, config.stopped_text)
        self.assertEqual(fakes.STOPPED_MARKUP, config.stopped_markup)
        self.assertEqual(fakes.STOPPED_CARD_TEXT, config.stopped_card_text)

    def test_invalid_message_id_is_none(self):
        """AC-P5/D-9: an invalid TG_MESSAGE_ID gives None, no exception"""
        for value in ("null", "abc", ""):
            with self.subTest(TG_MESSAGE_ID=value):
                env = fakes.full_env()
                env["TG_MESSAGE_ID"] = value

                self.assertIsNone(AgentConfig.from_env(env).message_id)

    def test_invalid_message_id_logs_warning(self):
        """AC-P5/D-9: an invalid TG_MESSAGE_ID logs a warning"""
        env = fakes.full_env()
        env["TG_MESSAGE_ID"] = "abc"

        with self.assertLogs(level="WARNING"):
            AgentConfig.from_env(env)

    def test_blank_chat_id_is_none(self):
        """AC-P5/D-9: a blank TG_CHAT_ID gives None, no exception"""
        for value in ("", "   "):
            with self.subTest(TG_CHAT_ID=value):
                env = fakes.full_env()
                env["TG_CHAT_ID"] = value

                self.assertIsNone(AgentConfig.from_env(env).chat_id)

    def test_missing_required_var_is_rejected(self):
        """AC-P5: a missing required var is an error"""
        env = fakes.required_env()
        del env["TAILSCALE_HOSTNAME"]

        with self.assertRaises(Exception):
            AgentConfig.from_env(env)


if __name__ == "__main__":
    unittest.main()
