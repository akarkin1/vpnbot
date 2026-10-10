import unittest

from node_agent.monitor import Action, IdleMonitor


def observe_idle(monitor, times):
    return [monitor.observe(0) for _ in range(times)]


class IdleMonitorTest(unittest.TestCase):

    def setUp(self):
        self.monitor = IdleMonitor(inactivity_timeout=600, check_interval=60)

    def test_connected_devices_mean_nothing_to_do(self):
        """AC-P1: active peers -> NONE"""
        self.assertEqual(Action.NONE, self.monitor.observe(2))

    def test_no_action_before_480_seconds_idle(self):
        """AC-P1: 60..420 s idle -> NONE"""
        self.assertEqual([Action.NONE] * 7, observe_idle(self.monitor, 7))

    def test_warns_at_480_seconds_idle(self):
        """AC-P1: warns at 480 s idle (600/60)"""
        observe_idle(self.monitor, 7)

        self.assertEqual(Action.WARN, self.monitor.observe(0))

    def test_warns_only_once_per_idle_period(self):
        """AC-P1: warns once at 480 s idle, 540 s idle -> NONE"""
        observe_idle(self.monitor, 8)

        self.assertEqual(Action.NONE, self.monitor.observe(0))

    def test_stops_at_600_seconds_idle(self):
        """AC-P1: stops at 600 s idle (600/60)"""
        actions = observe_idle(self.monitor, 10)

        self.assertEqual([Action.NONE] * 7 + [Action.WARN, Action.NONE, Action.STOP], actions)

    def test_activity_resets_idle_time(self):
        """AC-P1: activity resets the idle time"""
        observe_idle(self.monitor, 7)
        self.monitor.observe(1)

        self.assertEqual([Action.NONE] * 7 + [Action.WARN], observe_idle(self.monitor, 8))

    def test_activity_resets_warning_flag(self):
        """AC-P1: activity resets the warning, the next idle period warns again"""
        observe_idle(self.monitor, 8)
        self.monitor.observe(1)

        actions = observe_idle(self.monitor, 10)

        self.assertEqual([Action.NONE] * 7 + [Action.WARN, Action.NONE, Action.STOP], actions)

    # --- RESUME (stop-in-place §4.3) ---

    def test_resume_when_peers_come_back_after_warning(self):
        """AC-P3: active peers right after a WARN -> RESUME"""
        observe_idle(self.monitor, 8)

        self.assertEqual(Action.RESUME, self.monitor.observe(1))

    def test_resume_when_peers_come_back_later_in_warned_period(self):
        """AC-P3: WARN, then idle NONE checks, then active peers -> RESUME"""
        self.assertEqual([Action.NONE] * 7 + [Action.WARN, Action.NONE], observe_idle(self.monitor, 9))

        self.assertEqual(Action.RESUME, self.monitor.observe(3))

    def test_resume_only_once(self):
        """AC-P3: RESUME exactly once; further checks with active peers -> NONE"""
        observe_idle(self.monitor, 8)

        actions = [self.monitor.observe(1) for _ in range(3)]

        self.assertEqual([Action.RESUME, Action.NONE, Action.NONE], actions)

    def test_no_resume_without_prior_warning(self):
        """AC-P3: active peers after an idle period that was not warned about -> NONE"""
        observe_idle(self.monitor, 7)

        self.assertEqual(Action.NONE, self.monitor.observe(1))

    def test_no_resume_when_always_active(self):
        """AC-P3: active peers from the start -> never RESUME"""
        self.assertEqual([Action.NONE] * 3, [self.monitor.observe(1) for _ in range(3)])

    def test_new_idle_period_after_resume_warns_and_stops_again(self):
        """AC-P3: after RESUME the idle time and the warning reset -> a new idle period warns again"""
        observe_idle(self.monitor, 8)
        self.assertEqual(Action.RESUME, self.monitor.observe(1))

        actions = observe_idle(self.monitor, 10)

        self.assertEqual([Action.NONE] * 7 + [Action.WARN, Action.NONE, Action.STOP], actions)

    def test_resume_after_each_warning(self):
        """AC-P3: every warned idle period ended by peers gives one RESUME"""
        actions = []
        for _ in range(2):
            actions += observe_idle(self.monitor, 8)
            actions += [self.monitor.observe(1), self.monitor.observe(1)]

        self.assertEqual(2 * ([Action.NONE] * 7 + [Action.WARN, Action.RESUME, Action.NONE]), actions)

    def test_no_resume_after_unwarned_period_following_resume(self):
        """AC-P3: WARN, RESUME, short idle period without a warning, peers -> NONE"""
        observe_idle(self.monitor, 8)
        self.monitor.observe(1)
        observe_idle(self.monitor, 3)

        self.assertEqual(Action.NONE, self.monitor.observe(1))

    def test_stop_wins_over_warning_when_timeout_is_reached(self):
        """AC-P1: idle time >= timeout -> STOP even if never warned"""
        monitor = IdleMonitor(inactivity_timeout=60, check_interval=60)

        self.assertEqual(Action.STOP, monitor.observe(0))

    def test_custom_warning_before(self):
        """AC-P1: warning_before sets how early the warning comes"""
        monitor = IdleMonitor(inactivity_timeout=600, check_interval=60, warning_before=300)

        self.assertEqual([Action.NONE] * 4 + [Action.WARN], observe_idle(monitor, 5))


if __name__ == "__main__":
    unittest.main()
