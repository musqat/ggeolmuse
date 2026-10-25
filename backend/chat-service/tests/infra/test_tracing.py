import httpx
import respx
from fastapi import FastAPI
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter
from opentelemetry.trace import SpanKind

from app.infra.tracing import instrument_app, instrument_client, provider_from_env

MARKET = "http://market-data-service:8083"
TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
INCOMING = f"00-{TRACE_ID}-00f067aa0ba902b7-01"


def _traced_app():
    exporter = InMemorySpanExporter()
    provider = TracerProvider()
    provider.add_span_processor(SimpleSpanProcessor(exporter))
    http = httpx.AsyncClient(base_url=MARKET)
    instrument_client(http, provider)
    app = FastAPI()

    @app.get("/api/ai-chat/ping")
    async def ping():
        await http.get("/api/internal/market/price/AAPL")
        return {"ok": True}

    @app.get("/health")
    def health():
        return {"status": "UP"}

    instrument_app(app, provider)
    return app, exporter


async def _get(app, path, headers=None):
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://chat") as client:
        return await client.get(path, headers=headers)


@respx.mock
async def test_request_and_market_call_continue_incoming_trace():
    market = respx.get(f"{MARKET}/api/internal/market/price/AAPL").mock(return_value=httpx.Response(200, json={}))
    app, exporter = _traced_app()

    res = await _get(app, "/api/ai-chat/ping", {"traceparent": INCOMING})

    assert res.status_code == 200
    spans = exporter.get_finished_spans()
    assert sorted(s.kind.name for s in spans) == [SpanKind.CLIENT.name, SpanKind.SERVER.name]
    assert {format(s.context.trace_id, "032x") for s in spans} == {TRACE_ID}
    assert market.calls.last.request.headers["traceparent"].split("-")[1] == TRACE_ID


async def test_health_is_not_traced():
    app, exporter = _traced_app()

    await _get(app, "/health")

    assert exporter.get_finished_spans() == ()


def test_no_otlp_endpoint_means_no_provider(monkeypatch):
    monkeypatch.delenv("OTEL_EXPORTER_OTLP_ENDPOINT", raising=False)

    assert provider_from_env() is None
