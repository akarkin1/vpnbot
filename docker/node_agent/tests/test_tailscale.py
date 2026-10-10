import unittest
from unittest import mock

from node_agent.tailscale import Tailscale
from tests.fakes import (MISSING, PEERS_TWO_ACTIVE, FakeClock, FakePopen, FakeRun, failed,
                                    ok, status_json)

STATUS_COMMAND = ["tailscale", "status", "--json"]


def tailscale(run, popen=None, sleep=None):
    return Tailscale(run=run, popen=popen or FakePopen(), sleep=sleep or mock.Mock())


class ActivePeerCountTest(unittest.TestCase):

    def test_runs_tailscale_status_json(self):
        """AC-P2: runs `tailscale status --json`"""
        run = FakeRun(ok(status_json({})))

        tailscale(run).active_peer_count()

        self.assertEqual([STATUS_COMMAND], run.commands)

    def test_counts_only_active_peers(self):
        """AC-P2: counts peers whose Active is true (Self is not a peer)"""
        run = FakeRun(ok(status_json(PEERS_TWO_ACTIVE)))

        self.assertEqual(2, tailscale(run).active_peer_count())

    def test_peer_without_active_field_is_not_counted(self):
        """AC-P2: a peer without Active is not counted"""
        run = FakeRun(ok(status_json({"nodekey:aaa": {"HostName": "laptop", "ExitNode": False}})))

        self.assertEqual(0, tailscale(run).active_peer_count())

    def test_no_peers(self):
        """AC-P2: empty Peer -> 0"""
        run = FakeRun(ok(status_json({})))

        self.assertEqual(0, tailscale(run).active_peer_count())

    def test_missing_peer(self):
        """AC-P2: no Peer key -> 0"""
        run = FakeRun(ok(status_json(MISSING)))

        self.assertEqual(0, tailscale(run).active_peer_count())

    def test_null_peer(self):
        """AC-P2: null Peer -> 0"""
        run = FakeRun(ok(status_json(None)))

        self.assertEqual(0, tailscale(run).active_peer_count())

    def test_non_zero_exit(self):
        """AC-P2: non-zero exit -> 0, even if stdout lists active peers"""
        run = FakeRun(failed(status_json(PEERS_TWO_ACTIVE)))

        self.assertEqual(0, tailscale(run).active_peer_count())

    def test_invalid_json(self):
        """AC-P2: invalid JSON -> 0"""
        run = FakeRun(ok("Tailscale is stopped."))

        self.assertEqual(0, tailscale(run).active_peer_count())

    def test_command_cannot_be_started(self):
        """AC-P2: any error (here: the binary is missing) -> 0"""
        run = FakeRun(FileNotFoundError("tailscale"))

        self.assertEqual(0, tailscale(run).active_peer_count())


class TailscaleProcessTest(unittest.TestCase):
    """Commands used by `agent.run` (§5 steps 4, 5, 7, 8); they back AC-P6."""

    def test_start_daemon_runs_tailscaled_in_background(self):
        """AC-P6 (support): start_daemon starts tailscaled with userspace networking"""
        popen = FakePopen()

        tailscale(FakeRun(ok()), popen=popen).start_daemon()

        self.assertEqual([["tailscaled", "--tun=userspace-networking", "--no-logs-no-support"]],
                         popen.commands)

    def test_up_succeeds_on_first_attempt(self):
        """AC-P6 (support): up runs `tailscale up` once and returns True on success"""
        run = FakeRun(ok())

        result = tailscale(run).up("tskey-123", "fra-node-1")

        self.assertTrue(result)
        self.assertEqual([["tailscale", "up", "--authkey=tskey-123", "--hostname=fra-node-1",
                           "--advertise-exit-node"]], run.commands)

    def test_up_retries_until_success(self):
        """AC-P6 (support): up retries with the given delay until `tailscale up` succeeds"""
        run = FakeRun(failed(), failed(), ok())
        sleep = mock.Mock()

        result = tailscale(run, sleep=sleep).up("tskey-123", "fra-node-1", attempts=5, delay=1.0)

        self.assertTrue(result)
        self.assertEqual(3, len(run.commands))
        sleep.assert_called_with(1.0)

    def test_up_gives_up_after_all_attempts(self):
        """AC-P6 (support): up returns False after `attempts` failed runs"""
        run = FakeRun(failed())

        result = tailscale(run).up("tskey-123", "fra-node-1", attempts=3, delay=1.0)

        self.assertFalse(result)
        self.assertEqual(3, len(run.commands))

    def test_logout(self):
        """AC-P6 (support): logout runs `tailscale logout`"""
        run = FakeRun(ok())

        tailscale(run).logout()

        self.assertEqual([["tailscale", "logout"]], run.commands)

    def test_stop_daemon_terminates_tailscaled(self):
        """AC-P6 (support): stop_daemon terminates the started tailscaled"""
        popen = FakePopen()
        node = tailscale(FakeRun(ok()), popen=popen)
        node.start_daemon()

        node.stop_daemon()

        self.assertTrue(popen.processes[0].terminated)


class UpDeadlineTest(unittest.TestCase):
    """Decision D-4: `up` gives up 300 s after its first attempt, even with attempts left."""

    def setUp(self):
        self.clock = FakeClock(now=1000.0)
        self.started_at = []

    def tailscale(self, run, seconds_per_run=0.0):
        def timed_run(args, **kwargs):
            self.started_at.append(self.clock.now - 1000.0)
            self.clock.now += seconds_per_run
            return run(args, **kwargs)

        return Tailscale(run=timed_run, popen=FakePopen(), sleep=self.clock.sleep,
                         monotonic=self.clock.monotonic)

    def test_up_returns_false_after_300_seconds_with_attempts_left(self):
        """AC-P2/D-4: up returns False once 300 s have passed, although attempts remain"""
        node = self.tailscale(FakeRun(failed()))

        self.assertFalse(node.up("tskey-123", "fra-node-1", attempts=120, delay=120.0))

    def test_up_does_not_run_after_deadline(self):
        """AC-P2/D-4: no `tailscale up` is started 300 s or more after the first attempt"""
        node = self.tailscale(FakeRun(failed()))

        node.up("tskey-123", "fra-node-1", attempts=120, delay=120.0)

        self.assertEqual([0.0, 120.0, 240.0], self.started_at)

    def test_deadline_includes_time_spent_in_tailscale_up(self):
        """AC-P2/D-4: slow `tailscale up` runs count towards the 300 s"""
        node = self.tailscale(FakeRun(failed()), seconds_per_run=200.0)

        self.assertFalse(node.up("tskey-123", "fra-node-1", attempts=120, delay=1.0))
        self.assertEqual([0.0, 201.0], self.started_at)

    def test_up_succeeds_before_deadline(self):
        """AC-P2/D-4: a success within 300 s still returns True"""
        node = self.tailscale(FakeRun(failed(), failed(), ok()))

        self.assertTrue(node.up("tskey-123", "fra-node-1", attempts=120, delay=100.0))
        self.assertEqual([0.0, 100.0, 200.0], self.started_at)


if __name__ == "__main__":
    unittest.main()
