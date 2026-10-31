"""Prometheus 요청 지표(Micrometer 와 같은 이름 · 레이블)"""
from __future__ import annotations

import time
from collections.abc import Callable, Iterator

from prometheus_client import CONTENT_TYPE_LATEST, CollectorRegistry, Histogram, generate_latest
from prometheus_client.core import GaugeMetricFamily
from starlette.responses import Response
from starlette.types import ASGIApp, Message, Receive, Scope, Send

LABELS = ("method", "uri", "status")
SKIPPED_PATHS = ("/health", "/metrics")
WINDOW_SECONDS = 120.0
# 응답 시간 구간(자바 서비스와 같음)
BUCKETS = (0.1, 0.3, 1.0, 2.0, 5.0)


class RequestMetrics:
    """요청 수 · 처리 시간 합계 · 구간과 최근 2분 최대 처리 시간"""

    def __init__(self, registry: CollectorRegistry, clock: Callable[[], float] = time.monotonic):
        self._registry = registry
        self._clock = clock
        self._seconds = Histogram(
            "http_server_requests_seconds", "HTTP 요청 처리 시간", LABELS, registry=registry, buckets=BUCKETS
        )
        self._max: dict[tuple[str, str, str], tuple[float, float]] = {}
        registry.register(self)

    def observe(self, labels: tuple[str, str, str], seconds: float) -> None:
        self._seconds.labels(*labels).observe(seconds)
        now = self._clock()
        current, at = self._max.get(labels, (0.0, now))
        if seconds > current or now - at > WINDOW_SECONDS:
            self._max[labels] = (seconds, now)

    def collect(self) -> Iterator[GaugeMetricFamily]:
        family = GaugeMetricFamily("http_server_requests_seconds_max", "최근 2분 최대 처리 시간", labels=LABELS)
        now = self._clock()
        for labels, (seconds, at) in self._max.items():
            family.add_metric(labels, seconds if now - at <= WINDOW_SECONDS else 0.0)
        yield family

    def response(self) -> Response:
        return Response(generate_latest(self._registry), media_type=CONTENT_TYPE_LATEST)


class MetricsMiddleware:
    """요청마다 경로 틀 · 메서드 · 상태로 기록(헬스 체크 · 지표 수집 제외, 경로 없는 요청은 UNKNOWN)"""

    def __init__(self, app: ASGIApp, metrics: RequestMetrics):
        self.app = app
        self.metrics = metrics

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http" or scope["path"] in SKIPPED_PATHS:
            await self.app(scope, receive, send)
            return
        status = 500

        async def send_with_status(message: Message) -> None:
            nonlocal status
            if message["type"] == "http.response.start":
                status = message["status"]
            await send(message)

        started = time.perf_counter()
        try:
            await self.app(scope, receive, send_with_status)
        finally:
            route = getattr(scope.get("route"), "path", None) or "UNKNOWN"
            self.metrics.observe((scope["method"], route, str(status)), time.perf_counter() - started)
