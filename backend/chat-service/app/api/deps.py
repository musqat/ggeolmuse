from fastapi import Request

from app.core.chat_service import ChatService


def get_chat_service(request: Request) -> ChatService:
    return request.app.state.chat_service
