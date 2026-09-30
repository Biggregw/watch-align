"""Polite HTTP: one honest User-Agent, robots.txt respected for page and image fetches, a minimum
interval per host, and no retries that try to get around a refusal. A 401/403/429 or a robots
disallow is recorded as a reason code; the source is retried on a later run, never forced."""
from __future__ import annotations

import time
import urllib.robotparser
from dataclasses import dataclass
from urllib.parse import urlsplit

import requests

from .config import LIMITS, USER_AGENT


class FetchError(Exception):
    def __init__(self, reason: str, detail: str = "", retryable: bool = True):
        super().__init__(f"{reason}: {detail}" if detail else reason)
        self.reason, self.detail, self.retryable = reason, detail, retryable


@dataclass
class Response:
    url: str
    status: int
    content: bytes
    content_type: str

    @property
    def text(self) -> str:
        return self.content.decode("utf-8", errors="replace")


class Http:
    """Session wrapper. `offline=True` refuses every network call (used by --dry-run and tests)."""

    def __init__(self, offline: bool = False, interval_s: float = LIMITS.per_host_interval_s, robots: bool = True):
        self.offline = offline
        self.interval = interval_s
        self.check_robots = robots
        self.session = requests.Session()
        self.session.headers["User-Agent"] = USER_AGENT
        self._last: dict[str, float] = {}
        self._robots: dict[str, urllib.robotparser.RobotFileParser | None] = {}

    def _wait(self, host: str) -> None:
        last = self._last.get(host)
        if last is not None:
            gap = time.monotonic() - last
            if gap < self.interval:
                time.sleep(self.interval - gap)
        self._last[host] = time.monotonic()

    def allowed(self, url: str) -> bool:
        if not self.check_robots:
            return True
        parts = urlsplit(url)
        base = f"{parts.scheme}://{parts.netloc}"
        if base not in self._robots:
            rp = urllib.robotparser.RobotFileParser()
            try:
                self._wait(parts.netloc)
                r = self.session.get(base + "/robots.txt", timeout=LIMITS.request_timeout_s)
                if r.status_code in (401, 403):
                    rp.disallow_all = True
                elif r.status_code >= 400:
                    rp.allow_all = True
                else:
                    rp.parse(r.text.splitlines())
            except requests.RequestException:
                rp = None   # robots unreachable: treated as unknown, fetch attempted normally
            self._robots[base] = rp
        rp = self._robots[base]
        return True if rp is None else rp.can_fetch(USER_AGENT, url)

    def get(self, url: str, headers: dict | None = None, api: bool = False, max_bytes: int = LIMITS.max_download_bytes) -> Response:
        """GET a URL. `api=True` skips robots.txt (documented APIs define their own access terms)."""
        if self.offline:
            raise FetchError("offline", url, retryable=True)
        if not api and not self.allowed(url):
            raise FetchError("blocked_robots", url, retryable=False)
        self._wait(urlsplit(url).netloc)
        try:
            r = self.session.get(url, headers=headers or {}, timeout=LIMITS.request_timeout_s, stream=True, allow_redirects=True)
        except requests.RequestException as exc:
            raise FetchError("network_error", f"{type(exc).__name__}", retryable=True) from exc
        if r.status_code in (401, 403):
            raise FetchError(f"blocked_http_{r.status_code}", url, retryable=False)
        if r.status_code == 429:
            raise FetchError("rate_limited", url, retryable=True)
        if r.status_code == 404 or r.status_code == 410:
            raise FetchError("gone", f"http {r.status_code}", retryable=False)
        if r.status_code >= 400:
            raise FetchError("http_error", f"http {r.status_code}", retryable=True)
        data = bytearray()
        for chunk in r.iter_content(65536):
            data += chunk
            if len(data) > max_bytes:
                raise FetchError("too_large", url, retryable=False)
        return Response(r.url, r.status_code, bytes(data), (r.headers.get("content-type") or "").lower())

    def post(self, url: str, data: dict, headers: dict | None = None, auth=None) -> Response:
        if self.offline:
            raise FetchError("offline", url)
        self._wait(urlsplit(url).netloc)
        try:
            r = self.session.post(url, data=data, headers=headers or {}, auth=auth, timeout=LIMITS.request_timeout_s)
        except requests.RequestException as exc:
            raise FetchError("network_error", type(exc).__name__) from exc
        if r.status_code >= 400:
            raise FetchError(f"http_{r.status_code}", url, retryable=r.status_code >= 500 or r.status_code == 429)
        return Response(r.url, r.status_code, r.content, (r.headers.get("content-type") or "").lower())
