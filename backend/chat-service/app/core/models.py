"""chat-service 안쪽에서 쓰는 데이터와 도메인 예외"""
from __future__ import annotations


class MarketUnavailable(Exception):
    """시세 API 5xx · 시간 초과 · 연결 실패"""


class AiUnavailable(Exception):
    """OpenAI 오류 · 시간 초과 · 속도 제한"""


class AuthUnavailable(Exception):
    """인증 서버에서 공개키를 못 받음"""
