"""Tests for `agent.run`, `agent.fetch_public_ip` and `agent.fetch_task_id`.

Assumptions about how `agent.run` is wired (§5, §7):
- `node_agent.agent` imports `get_secret`, `Tailscale`, `TelegramClient` by name
  (`from node_agent.<module> import <name>` or the relative equivalent), so they are patched
  as `node_agent.agent.<name>`; `run` creates one `Tailscale` and at most one `TelegramClient`.
- `run` calls the module-level `fetch_public_ip` and `fetch_task_id` (patched, so no network
  access; `fetch_task_id` returns None unless a test sets a task id).
- The monitor loop waits with `time.sleep` (patched). The tests use a 2 s timeout and a 1 s
  interval, so even another waiting mechanism keeps them short.
- The real `AgentConfig`, `Notifier` and `IdleMonitor` are used.
"""

import signal
import unittest
from unittest import mock

from node_agent import agent
from node_agent.config import AgentConfig
from tests import fakes
from tests.fakes import (LINKS_ROW, MENU_BUTTON, READY_MARKUP_WITH_STOP, TASK_ARN, TASK_ID, FakeResponse,
                         FakeSession, FakeTailscale, FakeTelegram, bind, markup_json, stop_button)

PUBLIC_IP = "203.0.113.7"
METADATA_URI = "http://169.254.170.2/v4/0123456789abcdef0123456789abcdef-1234567890"
SECRETS = {"ts-secret": "tskey-123", "tg-secret": "bot-token"}


def telegram_client(token, api_base="https://api.telegram.org", session=None):
    """Signature of the `TelegramClient` constructor, used to compare constructor calls."""


def fetch_task_id(env, session=None):
    """Signature of `agent.fetch_task_id` (§6), used to compare calls."""


def agent_env(**overrides):
    """Full env with a 2 s idle timeout and a 1 s check interval: one warning, then a stop."""
    env = fakes.full_env()
    env.update(INACTIVITY_TIMEOUT="2", STATUS_CHECK_INTERVAL="1")
    for name, value in overrides.items():
        if value is None:
            env.pop(name, None)
        else:
            env[name] = value
    return env


class RunTest(unittest.TestCase):

    def setUp(self):
        self.events = []
        self.tailscale = FakeTailscale(self.events)
        self.telegram = FakeTelegram(self.events)
        self.secrets = dict(SECRETS)
        self.secret_calls = []
        self.telegram_client_class = mock.Mock(return_value=self.telegram)
        self.fetch_task_id = mock.Mock(return_value=None)

        sigterm_handler = signal.getsignal(signal.SIGTERM)
        self.addCleanup(signal.signal, signal.SIGTERM, sigterm_handler)
        for target, replacement in (
                ("node_agent.agent.get_secret", mock.Mock(side_effect=self.get_secret)),
                ("node_agent.agent.Tailscale", mock.Mock(return_value=self.tailscale)),
                ("node_agent.agent.TelegramClient", self.telegram_client_class),
                ("node_agent.agent.fetch_public_ip", mock.Mock(return_value=PUBLIC_IP)),
                ("node_agent.agent.fetch_task_id", self.fetch_task_id),
                ("time.sleep", mock.Mock()),
        ):
            # create=True: `fetch_task_id` is new in 2b; the 2a tests keep running without it.
            patcher = mock.patch(target, replacement, create=True)
            patcher.start()
            self.addCleanup(patcher.stop)

    def get_secret(self, secret_id, region, *args, **kwargs):
        self.secret_calls.append((secret_id, region))
        value = self.secrets[secret_id]
        if isinstance(value, Exception):
            raise value
        return value

    def expected_telegram_calls(self, env):
        config = AgentConfig.from_env(env)
        chat_id, message_id = config.chat_id, config.message_id
        return [
            ("edit_message", {"chat_id": chat_id, "message_id": message_id,
                              "text": "\U0001F7E2 <b>fra-node-1</b> · Frankfurt\n<code>203.0.113.7</code>",
                              "reply_markup": fakes.READY_MARKUP}),
            ("send_message", {"chat_id": chat_id, "text": "⚠️ <b>fra-node-1</b> will stop in 2 minutes",
                              "reply_markup": None, "silent": True}),
            ("send_message", {"chat_id": chat_id, "text": "\U0001F6D1 <b>fra-node-1</b> was stopped",
                              "reply_markup": fakes.STOPPED_MARKUP, "silent": False}),
            ("edit_message", {"chat_id": chat_id, "message_id": message_id,
                              "text": "⚪ <b>fra-node-1</b> · Frankfurt\nStopped",
                              "reply_markup": None}),
        ]

    # --- start-up failures ---

    def test_tailscale_secret_failure_exits_with_1(self):
        """AC-P6: Tailscale secret failure -> 1"""
        self.secrets["ts-secret"] = RuntimeError("AccessDeniedException")

        self.assertEqual(1, agent.run(agent_env()))

    def test_tailscale_secret_failure_does_not_start_tailscale(self):
        """AC-P6: Tailscale secret failure -> tailscaled is not started"""
        self.secrets["ts-secret"] = RuntimeError("AccessDeniedException")

        agent.run(agent_env())

        self.assertNotIn("tailscale.start_daemon", self.events)
        self.assertNotIn("tailscale.up", self.events)

    def test_up_failure_exits_with_1(self):
        """AC-P6: `up` failure -> 1"""
        self.tailscale.up_result = False

        self.assertEqual(1, agent.run(agent_env()))

    def test_up_failure_does_not_report_ready(self):
        """AC-P6: `up` failure -> the ready card is not shown"""
        self.tailscale.up_result = False

        agent.run(agent_env())

        self.assertNotIn(self.expected_telegram_calls(agent_env())[0], self.telegram.calls)

    def test_up_failure_edits_progress_message_to_stopped_card(self):
        """AC-P6/D-5: `up` failure -> only the progress message is edited to the stopped card, no markup"""
        self.tailscale.up_result = False

        self.assertEqual(1, agent.run(agent_env()))
        self.assertEqual([self.expected_telegram_calls(agent_env())[3]], self.telegram.calls)

    # --- start-up ---

    def test_reads_secrets_from_configured_ids_and_regions(self):
        """AC-P6: Tailscale key and bot token are read from their secret ids and regions"""
        agent.run(agent_env())

        self.assertEqual([("ts-secret", "eu-central-1"), ("tg-secret", "eu-west-1")], self.secret_calls)

    def test_brings_tailscale_up_with_secret_key_and_hostname(self):
        """AC-P6: tailscaled is started, then `up` with the secret key and TAILSCALE_HOSTNAME"""
        agent.run(agent_env())

        self.assertEqual(["tailscale.start_daemon", "tailscale.up"], self.events[:2])
        self.assertEqual([{"auth_key": "tskey-123", "hostname": "fra-node-1"}], self.tailscale.up_calls)

    def test_telegram_client_uses_bot_token_and_api_base(self):
        """AC-P6: the Telegram client gets the bot token from the secret and TG_API_BASE"""
        agent.run(agent_env())

        args, kwargs = self.telegram_client_class.call_args
        arguments = bind(telegram_client, args, kwargs)
        self.assertEqual(("bot-token", "http://telegram.local"), (arguments["token"], arguments["api_base"]))

    # --- task id on the ready card (2b §5) ---

    def ready_markup(self):
        """The reply_markup of the ready card edit (the first Telegram call)."""
        method, arguments = self.telegram.calls[0]
        self.assertEqual("edit_message", method)
        return arguments["reply_markup"]

    def test_fetches_task_id_with_run_env_after_tailscale_is_up(self):
        """AC-P7/§5: `run` calls fetch_task_id(env) once, after `tailscale up`"""
        env = agent_env()
        self.fetch_task_id.side_effect = lambda *args, **kwargs: self.events.append("fetch_task_id")

        agent.run(env)

        self.assertEqual(1, self.fetch_task_id.call_count)
        args, kwargs = self.fetch_task_id.call_args
        self.assertEqual(env, bind(fetch_task_id, args, kwargs)["env"])
        self.assertEqual(["tailscale.start_daemon", "tailscale.up", "fetch_task_id"], self.events[:3])

    def test_ready_card_stop_button_gets_fetched_task_id(self):
        """AC-P8/§5: `run` passes the fetched task id to `ready` -> Stop callback_data STOP:<region>:<task id>"""
        self.fetch_task_id.return_value = TASK_ID

        agent.run(agent_env(TG_READY_MARKUP=READY_MARKUP_WITH_STOP))

        self.assertEqual(markup_json(LINKS_ROW, [stop_button(TASK_ID), MENU_BUTTON]), self.ready_markup())

    def test_ready_card_without_task_id_has_no_stop_button(self):
        """AC-P8/§5: task id unknown -> ready card without the Stop button, node still runs"""
        self.fetch_task_id.return_value = None

        self.assertEqual(0, agent.run(agent_env(TG_READY_MARKUP=READY_MARKUP_WITH_STOP)))
        self.assertEqual(markup_json(LINKS_ROW, [MENU_BUTTON]), self.ready_markup())

    # --- idle stop ---

    def test_idle_stop_exits_with_0(self):
        """AC-P6: idle STOP path returns 0"""
        self.assertEqual(0, agent.run(agent_env()))

    def test_idle_stop_notifies_ready_warning_and_stop(self):
        """AC-P6: idle STOP path notifies: ready card, silent warning, stopped message, stopped card"""
        agent.run(agent_env())

        self.assertEqual(self.expected_telegram_calls(agent_env()), self.telegram.calls)

    def test_idle_stop_logs_out_and_stops_daemon_after_notifying(self):
        """AC-P6: idle STOP path notifies, then logs out, then terminates tailscaled"""
        agent.run(agent_env())

        self.assertEqual(["telegram.send_message", "telegram.edit_message", "tailscale.logout",
                          "tailscale.stop_daemon"], self.events[-4:])

    def test_connected_devices_postpone_idle_stop(self):
        """AC-P6: checks with active peers reset the idle time"""
        self.tailscale.peer_counts = [1, 1, 1]

        self.assertEqual(0, agent.run(agent_env()))
        self.assertEqual(5, self.tailscale.status_calls)

    # --- notifications disabled ---

    def test_bot_token_failure_disables_notifications_only(self):
        """AC-P6: bot token secret failure -> no notifications, node still runs and stops with 0"""
        self.secrets["tg-secret"] = RuntimeError("ResourceNotFoundException")

        self.assertEqual(0, agent.run(agent_env()))
        self.assertEqual([], self.telegram.calls)
        self.assertIn("tailscale.logout", self.events)

    def test_without_bot_token_secret_id_notifications_are_disabled(self):
        """AC-P6: no TG_BOT_TOKEN_SECRET_ID -> no token lookup, no notifications, exit 0"""
        self.assertEqual(0, agent.run(agent_env(TG_BOT_TOKEN_SECRET_ID=None)))
        self.assertEqual([("ts-secret", "eu-central-1")], self.secret_calls)
        self.assertEqual([], self.telegram.calls)

    def test_without_message_id_notifications_are_disabled(self):
        """AC-P6: no TG_MESSAGE_ID -> no notifications, node works as before"""
        self.assertEqual(0, agent.run(agent_env(TG_MESSAGE_ID=None)))
        self.assertEqual([], self.telegram.calls)

    def test_invalid_message_id_disables_notifications_only(self):
        """AC-P6/D-9: TG_MESSAGE_ID "null" -> node starts, no notifications, idle stop returns 0"""
        self.assertEqual(0, agent.run(agent_env(TG_MESSAGE_ID="null")))
        self.assertEqual([], self.telegram.calls)
        self.assertEqual(["tailscale.start_daemon", "tailscale.up", "tailscale.logout",
                          "tailscale.stop_daemon"], self.events)

    def test_blank_chat_id_disables_notifications_only(self):
        """AC-P6/D-9: blank TG_CHAT_ID -> node starts, no notifications, idle stop returns 0"""
        self.assertEqual(0, agent.run(agent_env(TG_CHAT_ID="   ")))
        self.assertEqual([], self.telegram.calls)
        self.assertEqual(["tailscale.start_daemon", "tailscale.up", "tailscale.logout",
                          "tailscale.stop_daemon"], self.events)


class FetchPublicIpTest(unittest.TestCase):
    """§5 step 6: the IP shown on the node card."""

    def test_returns_stripped_body(self):
        """AC-P6 (support): public IP = stripped body of checkip.amazonaws.com"""
        session = FakeSession(FakeResponse(200, text="203.0.113.7\n"))

        self.assertEqual(PUBLIC_IP, agent.fetch_public_ip(session=session))

    def test_queries_checkip_with_5_second_timeout(self):
        """AC-P6 (support): GET https://checkip.amazonaws.com with a 5 s timeout"""
        session = FakeSession(FakeResponse(200, text="203.0.113.7\n"))

        agent.fetch_public_ip(session=session)

        url, kwargs = session.gets[0]
        self.assertEqual("https://checkip.amazonaws.com", url.rstrip("/"))
        self.assertEqual(5, kwargs.get("timeout"))

    def test_failure_returns_dash(self):
        """AC-P6 (support): failure -> —"""
        session = FakeSession(error=fakes.network_error())

        self.assertEqual("—", agent.fetch_public_ip(session=session))


class FetchTaskIdTest(unittest.TestCase):
    """§5: the ECS task id from the task metadata endpoint v4."""

    def env(self):
        return {"ECS_CONTAINER_METADATA_URI_V4": METADATA_URI}

    def test_returns_last_segment_of_task_arn(self):
        """AC-P7: task id = last `/` segment of TaskARN in the metadata response"""
        session = FakeSession(FakeResponse(200, {"Cluster": "vpn-cluster", "TaskARN": TASK_ARN,
                                                 "Family": "tailscale-node", "Revision": "7"}))

        self.assertEqual(TASK_ID, agent.fetch_task_id(self.env(), session=session))

    def test_queries_task_metadata_with_5_second_timeout(self):
        """AC-P7: one GET $ECS_CONTAINER_METADATA_URI_V4/task with a 5 s timeout"""
        session = FakeSession(FakeResponse(200, {"TaskARN": TASK_ARN}))

        agent.fetch_task_id(self.env(), session=session)

        self.assertEqual(1, len(session.gets))
        url, kwargs = session.gets[0]
        self.assertEqual(METADATA_URI + "/task", url)
        self.assertEqual(5, kwargs.get("timeout"))

    def test_without_metadata_variable_returns_none_without_request(self):
        """AC-P7: no ECS_CONTAINER_METADATA_URI_V4 -> None, no request"""
        session = FakeSession(FakeResponse(200, {"TaskARN": TASK_ARN}))

        self.assertIsNone(agent.fetch_task_id({}, session=session))
        self.assertEqual([], session.gets)

    def test_http_error_returns_none(self):
        """AC-P7: HTTP error -> None (even if the body has a TaskARN)"""
        session = FakeSession(FakeResponse(500, {"TaskARN": TASK_ARN}))

        self.assertIsNone(agent.fetch_task_id(self.env(), session=session))

    def test_network_error_returns_none(self):
        """AC-P7: network exception -> None"""
        session = FakeSession(error=fakes.network_error())

        self.assertIsNone(agent.fetch_task_id(self.env(), session=session))

    def test_bad_json_returns_none(self):
        """AC-P7: response body that is not JSON -> None"""
        session = FakeSession(FakeResponse(200, text="<html>Bad Gateway</html>"))

        self.assertIsNone(agent.fetch_task_id(self.env(), session=session))

    def test_missing_task_arn_returns_none(self):
        """AC-P7: JSON without TaskARN -> None"""
        session = FakeSession(FakeResponse(200, {"Cluster": "vpn-cluster"}))

        self.assertIsNone(agent.fetch_task_id(self.env(), session=session))


if __name__ == "__main__":
    unittest.main()
