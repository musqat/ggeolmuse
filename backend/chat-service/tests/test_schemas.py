from app.schemas import ChatRequest, ChatResponse


def test_chat_request_parses_message():
    req = ChatRequest(message="AAPL 어때?")
    assert req.message == "AAPL 어때?"


def test_chat_response_serializes_fields():
    res = ChatResponse(answer="상승 경향", remaining=4, symbol="AAPL")
    dumped = res.model_dump()
    assert dumped == {"answer": "상승 경향", "remaining": 4, "symbol": "AAPL"}
