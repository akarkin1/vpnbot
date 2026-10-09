"""Hand-written fakes and fixtures shared by the node agent tests."""

import inspect
import json
import subprocess

# --- environment -----------------------------------------------------------

READY_TEXT = "\U0001F7E2 <b>{{HOSTNAME}}</b> · Frankfurt\n<code>{{PUBLIC_IP}}</code>"
READY_MARKUP = '{"inline_keyboard":[[{"text":"Menu","callback_data":"m"}]]}'
IDLE_WARNING_TEXT = "⚠️ <b>{{HOSTNAME}}</b> will stop in 2 minutes"
STOPPED_TEXT = "\U0001F6D1 <b>{{HOSTNAME}}</b> was stopped"
STOPPED_MARKUP = '{"inline_keyboard":[[{"text":"Start again","callback_data":"r"}]]}'
STOPPED_CARD_TEXT = "⚪ <b>{{HOSTNAME}}</b> · Frankfurt\nStopped"


def required_env():
    """Only the variables §5 marks as required."""
    return {
        "TAILSCALE_HOSTNAME": "fra-node-1",
        "TAILSCALE_TOKEN_SECRET_ID": "ts-secret",
        "TAILSCALE_TOKEN_SECRET_REGION": "eu-central-1",
    }


def full_env():
    """Every variable of §5 and §4.4, as the Lambda and the task definition set them."""
    env = required_env()
    env.update({
        "INACTIVITY_TIMEOUT": "900",
        "STATUS_CHECK_INTERVAL": "30",
        "TG_BOT_TOKEN_SECRET_ID": "tg-secret",
        "TG_BOT_TOKEN_SECRET_REGION": "eu-west-1",
        "TG_API_BASE": "http://telegram.local",
        "TG_CHAT_ID": "-1001234",
        "TG_MESSAGE_ID": "42",
        "TG_READY_TEXT": READY_TEXT,
        "TG_READY_MARKUP": READY_MARKUP,
        "TG_IDLE_WARNING_TEXT": IDLE_WARNING_TEXT,
        "TG_STOPPED_TEXT": STOPPED_TEXT,
        "TG_STOPPED_MARKUP": STOPPED_MARKUP,
        "TG_STOPPED_CARD_TEXT": STOPPED_CARD_TEXT,
    })
    return env


# --- `tailscale status --json` --------------------------------------------

def status_json(peers):
    """A `tailscale status --json` document; `peers` is the value of `Peer` (or the
    sentinel `MISSING` to leave the key out)."""
    document = {
        "BackendState": "Running",
        "Self": {"HostName": "fra-node-1", "Active": True, "ExitNode": False},
    }
    if peers is not MISSING:
        document["Peer"] = peers
    return json.dumps(document)


MISSING = object()

PEERS_TWO_ACTIVE = {
    "nodekey:aaa": {"HostName": "laptop", "Active": True, "ExitNode": False},
    "nodekey:bbb": {"HostName": "phone", "Active": False, "ExitNode": False},
    "nodekey:ccc": {"HostName": "tablet", "Active": True, "ExitNode": False},
    "nodekey:ddd": {"HostName": "other-exit", "Active": False, "ExitNode": True},
}


# --- subprocess ------------------------------------------------------------

def argv(command):
    """A command as a list, whether it was passed as a list or as a string."""
    return command.split() if isinstance(command, str) else list(command)


class FakeRun:
    """Stands in for `subprocess.run`. Returns the queued results in order (the last one
    repeats) and honours `check=True`, `text=True` and `universal_newlines=True`."""

    def __init__(self, *results):
        self.results = list(results)
        self.commands = []

    def __call__(self, args, **kwargs):
        self.commands.append(argv(args))
        result = self.results.pop(0) if len(self.results) > 1 else self.results[0]
        if isinstance(result, BaseException):
            raise result
        returncode, stdout = result
        if kwargs.get("check") and returncode != 0:
            raise subprocess.CalledProcessError(returncode, args, output=stdout)
        as_text = kwargs.get("text") or kwargs.get("universal_newlines") or kwargs.get("encoding")
        out = stdout if as_text else stdout.encode()
        return subprocess.CompletedProcess(args, returncode, stdout=out, stderr="" if as_text else b"")


def ok(stdout=""):
    return 0, stdout


def failed(stdout=""):
    return 1, stdout


class FakeProcess:
    def __init__(self):
        self.terminated = False
        self.killed = False

    def terminate(self):
        self.terminated = True

    def kill(self):
        self.killed = True

    def wait(self, timeout=None):
        return 0

    def poll(self):
        return 0 if self.terminated or self.killed else None


class FakePopen:
    """Stands in for `subprocess.Popen`."""

    def __init__(self):
        self.commands = []
        self.processes = []

    def __call__(self, args, **kwargs):
        self.commands.append(argv(args))
        process = FakeProcess()
        self.processes.append(process)
        return process


# --- HTTP (requests-like) ----------------------------------------------------

class FakeResponse:
    def __init__(self, status_code=200, body=None, text=None):
        self.status_code = status_code
        self._body = body
        self.text = text if text is not None else json.dumps(body)
        self.content = self.text.encode()
        self.ok = 200 <= status_code < 400

    def json(self):
        if self._body is None:
            raise ValueError("response body is not JSON")
        return self._body

    def raise_for_status(self):
        if not self.ok:
            raise IOError("HTTP %d" % self.status_code)


class FakeSession:
    """Records `post`/`get` calls and answers with a fixed response or raises an error."""

    def __init__(self, response=None, error=None):
        self.response = response if response is not None else FakeResponse(200, {"ok": True, "result": {}})
        self.error = error
        self.posts = []
        self.gets = []

    def post(self, url, **kwargs):
        self.posts.append((url, kwargs))
        return self._answer()

    def get(self, url, **kwargs):
        self.gets.append((url, kwargs))
        return self._answer()

    def _answer(self):
        if self.error is not None:
            raise self.error
        return self.response


def posted_json(kwargs):
    """The JSON payload of a recorded `post`, sent either as `json=` or as a JSON `data=`."""
    if "json" in kwargs:
        return kwargs["json"]
    return json.loads(kwargs["data"])


def network_error():
    try:
        import requests
        return requests.ConnectionError("connection refused")
    except ImportError:
        return ConnectionError("connection refused")


# --- collaborators of Notifier and agent.run -------------------------------

class FakeTelegram:
    """Same method signatures as the `TelegramClient` contract; records calls as dicts of
    bound arguments (so positional and keyword calls compare equal)."""

    def __init__(self, events=None, result=True):
        self.calls = []
        self.events = events if events is not None else []
        self.result = result

    def send_message(self, chat_id, text, reply_markup=None, silent=False):
        self._record("send_message", locals())
        return self.result

    def edit_message(self, chat_id, message_id, text, reply_markup=None):
        self._record("edit_message", locals())
        return self.result

    def _record(self, method, arguments):
        arguments = {k: v for k, v in arguments.items() if k != "self"}
        self.calls.append((method, arguments))
        self.events.append("telegram." + method)


class FakeTailscale:
    """Same method signatures as the `Tailscale` contract. `peer_counts` are returned by
    successive `active_peer_count` calls (then 0); a runaway monitor loop is cut off."""

    MAX_STATUS_CALLS = 50

    def __init__(self, events, up_result=True, peer_counts=()):
        self.events = events
        self.up_result = up_result
        self.peer_counts = list(peer_counts)
        self.up_calls = []
        self.status_calls = 0

    def start_daemon(self):
        self.events.append("tailscale.start_daemon")

    def up(self, auth_key, hostname, attempts=120, delay=1.0):
        self.events.append("tailscale.up")
        self.up_calls.append({"auth_key": auth_key, "hostname": hostname})
        return self.up_result

    def active_peer_count(self):
        self.status_calls += 1
        if self.status_calls > self.MAX_STATUS_CALLS:
            raise AssertionError("monitor loop did not stop")
        return self.peer_counts.pop(0) if self.peer_counts else 0

    def logout(self):
        self.events.append("tailscale.logout")

    def stop_daemon(self):
        self.events.append("tailscale.stop_daemon")


def bind(function, args, kwargs):
    """Arguments of a call bound to the parameter names of `function`."""
    bound = inspect.signature(function).bind(*args, **kwargs)
    bound.apply_defaults()
    return dict(bound.arguments)
