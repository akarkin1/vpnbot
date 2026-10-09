from enum import Enum


class Action(Enum):
    NONE = "none"
    WARN = "warn"
    STOP = "stop"


class IdleMonitor:
    """Decides, one check at a time, when an idle node is warned about and stopped."""

    def __init__(self, inactivity_timeout, check_interval, warning_before=120):
        self._timeout = inactivity_timeout
        self._interval = check_interval
        self._warning_before = warning_before
        self._idle_time = 0
        self._warned = False

    def observe(self, active_peers: int) -> Action:
        if active_peers > 0:
            self._idle_time = 0
            self._warned = False
            return Action.NONE
        self._idle_time += self._interval
        if self._idle_time >= self._timeout:
            return Action.STOP
        if not self._warned and self._timeout - self._idle_time <= self._warning_before:
            self._warned = True
            return Action.WARN
        return Action.NONE
