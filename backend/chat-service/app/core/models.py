"""chat-service 안쪽에서 쓰는 데이터와 도메인 예외"""
from __future__ import annotations


class AuthUnavailable(Exception):
    """인증 서버에서 공개키를 못 받음"""
