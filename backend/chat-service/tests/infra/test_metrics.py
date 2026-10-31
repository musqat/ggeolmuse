from fastapi import FastAPI
from fastapi.testclient import TestClient
from prometheus_client import CollectorRegistry

from app.infra.metrics import MetricsMiddleware, RequestMetrics

COUNT = "http_server_requests_seconds_count"
MAX = "http_server_requests_seconds_max"
BUCKET = "http_server_requests_seconds_bucket"


def _app(clock=None):
    registry = CollectorRegistry()
    metrics = RequestMetrics(registry, clock=clock) if clock else RequestMetrics(registry)
    app = FastAPI()
    app.add_middleware(MetricsMiddleware, metrics=metrics)

    @app.get("/api/chat/items/{item_id}")
    def item(item_id: str):
        return {"id": item_id}

    @app.get("/api/chat/boom")
    def boom():
        raise RuntimeError("boom")

    @app.get("/health")
    def health():
        return {"status": "UP"}

    @app.get("/metrics")
    def scrape():
        return metrics.response()

    return app, registry, metrics


def _count(registry, uri, status, method="GET"):
    return registry.get_sample_value(COUNT, {"method": method, "uri": uri, "status": status})


def test_counts_requests_by_route_template():
    app, registry, _ = _app()
    client = TestClient(app)

    client.get("/api/chat/items/1")
    client.get("/api/chat/items/2")

    assert _count(registry, "/api/chat/items/{item_id}", "200") == 2


def test_unmatched_paths_share_one_label():
    # 경로마다 레이블이 생기면 시계열이 끝없이 는다
    app, registry, _ = _app()
    client = TestClient(app)

    client.get("/nope/1")
    client.get("/nope/2")

    assert _count(registry, "UNKNOWN", "404") == 2


def test_unhandled_error_counts_as_500():
    app, registry, _ = _app()

    TestClient(app, raise_server_exceptions=False).get("/api/chat/boom")

    assert _count(registry, "/api/chat/boom", "500") == 1


def test_health_and_scrape_are_not_counted():
    app, registry, _ = _app()
    client = TestClient(app)

    client.get("/health")
    client.get("/metrics")

    assert _count(registry, "/health", "200") is None
    assert _count(registry, "/metrics", "200") is None


def test_max_drops_to_zero_after_window():
    now = [0.0]
    _, registry, metrics = _app(clock=lambda: now[0])
    labels = {"method": "POST", "uri": "/api/chat", "status": "200"}

    metrics.observe(("POST", "/api/chat", "200"), 3.0)
    assert registry.get_sample_value(MAX, labels) == 3.0

    now[0] = 121.0
    assert registry.get_sample_value(MAX, labels) == 0.0


def test_scrape_returns_prometheus_text():
    app, _, _ = _app()
    client = TestClient(app)
    client.get("/api/chat/items/1")

    res = client.get("/metrics")

    assert res.headers["content-type"].startswith("text/plain")
    assert 'http_server_requests_seconds_count{method="GET",status="200",uri="/api/chat/items/{item_id}"}' in res.text


def test_latency_buckets_match_java_services():
    _, registry, metrics = _app()
    labels = {"method": "POST", "uri": "/api/chat", "status": "200"}

    metrics.observe(("POST", "/api/chat", "200"), 0.5)
    metrics.observe(("POST", "/api/chat", "200"), 3.0)

    counts = [registry.get_sample_value(BUCKET, {**labels, "le": le}) for le in ("0.1", "0.3", "1.0", "2.0", "5.0", "+Inf")]
    assert counts == [0, 0, 1, 1, 2, 2]
