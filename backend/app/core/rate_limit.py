"""
Attempt limits for the sign-in and registration endpoints.

A sliding window per key, held in this process's memory. It throttles
password guessing and bulk account creation against a single API process.
Running several API processes (or hosts) gives each its own counters; put a
shared limiter (reverse proxy, API gateway or Redis) in front for that
deployment. Counters are lost on restart.
"""

from __future__ import annotations

import math
import threading
import time
from collections import defaultdict, deque


class RateLimiter:
    def __init__(self, clock=time.monotonic):
        self._clock = clock
        self._hits: dict[str, deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def hit(self, key: str, limit: int, window_seconds: int) -> int | None:
        """Records an attempt. Returns None when allowed, else seconds until the next attempt is allowed."""
        now = self._clock()
        with self._lock:
            hits = self._hits[key]
            while hits and hits[0] <= now - window_seconds:
                hits.popleft()
            if len(hits) >= limit:
                return max(1, math.ceil(hits[0] + window_seconds - now))
            hits.append(now)
            return None

    def clear(self, key: str) -> None:
        with self._lock:
            self._hits.pop(key, None)

    def reset(self) -> None:
        with self._lock:
            self._hits.clear()


limiter = RateLimiter()
