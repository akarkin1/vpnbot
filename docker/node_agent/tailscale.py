import json
import logging
import subprocess
import time
from typing import List, Optional

log = logging.getLogger(__name__)

COMMAND_TIMEOUT_SECONDS = 30
DAEMON_STOP_TIMEOUT_SECONDS = 10
UP_DEADLINE_SECONDS = 300


class Tailscale:
    """Runs the tailscale CLI and the tailscaled daemon."""

    def __init__(self, run=subprocess.run, popen=subprocess.Popen, sleep=time.sleep,
                 monotonic=time.monotonic):
        self._run = run
        self._popen = popen
        self._sleep = sleep
        self._monotonic = monotonic
        self._daemon = None

    def start_daemon(self) -> None:
        log.info("Starting tailscaled")
        self._daemon = self._popen(["tailscaled", "--tun=userspace-networking", "--no-logs-no-support"])

    def up(self, auth_key, hostname, attempts=120, delay=1.0) -> bool:
        command = ["tailscale", "up", f"--authkey={auth_key}", f"--hostname={hostname}", "--advertise-exit-node"]
        started = self._monotonic()
        for attempt in range(1, attempts + 1):
            if attempt > 1:
                self._sleep(delay)
                if self._monotonic() - started >= UP_DEADLINE_SECONDS:
                    log.info("tailscale up did not succeed within %d s", UP_DEADLINE_SECONDS)
                    break
            result = self._execute(command)
            if result is not None and result.returncode == 0:
                log.info("Tailscale is up as %s", hostname)
                return True
            log.info("tailscale up failed (attempt %d of %d)", attempt, attempts)
        return False

    def active_peer_count(self) -> int:
        result = self._execute(["tailscale", "status", "--json"])
        if result is None or result.returncode != 0:
            return 0
        try:
            peers = json.loads(result.stdout).get("Peer") or {}
            return sum(1 for peer in peers.values() if peer.get("Active") is True)
        except (ValueError, TypeError, AttributeError) as e:
            log.warning("Cannot parse tailscale status: %s", e)
            return 0

    def logout(self) -> None:
        log.info("Logging out of Tailscale")
        self._execute(["tailscale", "logout"])

    def stop_daemon(self) -> None:
        if self._daemon is None:
            return
        log.info("Stopping tailscaled")
        self._daemon.terminate()
        try:
            self._daemon.wait(timeout=DAEMON_STOP_TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            self._daemon.kill()
        self._daemon = None

    def _execute(self, command: List[str]) -> Optional[subprocess.CompletedProcess]:
        """Runs a command; returns None when it cannot be run or times out."""
        try:
            result = self._run(command, capture_output=True, text=True, timeout=COMMAND_TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            # not logging the exception itself: its message contains the command with the auth key
            log.warning("%s %s timed out", command[0], command[1])
            return None
        except (OSError, subprocess.SubprocessError) as e:
            log.warning("%s %s failed: %s", command[0], command[1], e)
            return None
        if result.returncode != 0:
            log.warning("%s %s exited with %s: %s", command[0], command[1], result.returncode,
                        (result.stderr or "").strip())
        return result
