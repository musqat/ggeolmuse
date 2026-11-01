"""OTel 트레이스(FastAPI 서버 스팬 · market-data 호출 httpx 스팬, OTLP 주소가 없으면 안 냄)"""
from __future__ import annotations

import os

import httpx
from fastapi import FastAPI
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

EXCLUDED_URLS = "/health,/metrics"


def provider_from_env() -> TracerProvider | None:
    """OTEL_EXPORTER_OTLP_ENDPOINT 로 내보내는 공급자(서비스 이름 · 자원 속성 · 샘플링은 OTEL_* 환경변수)"""
    if not os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT") or os.getenv("OTEL_SDK_DISABLED", "").lower() == "true":
        return None
    provider = TracerProvider(resource=Resource.create())
    provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))
    trace.set_tracer_provider(provider)
    return provider


def instrument_app(app: FastAPI, provider: TracerProvider) -> None:
    """요청마다 서버 스팬 하나(헬스 체크 · 지표 수집 · ASGI 송수신 스팬 제외)"""
    FastAPIInstrumentor.instrument_app(app, tracer_provider=provider, excluded_urls=EXCLUDED_URLS,
                                       exclude_spans=["receive", "send"])


def instrument_client(http: httpx.AsyncClient, provider: TracerProvider) -> None:
    """나가는 호출마다 클라이언트 스팬과 traceparent 헤더"""
    HTTPXClientInstrumentor.instrument_client(http, tracer_provider=provider)
