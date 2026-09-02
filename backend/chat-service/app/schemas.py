from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=500)
    # 종목이 확정된 경우(차트에서 분석 요청) 직접 전달 → mini 추출 생략
    symbol: str | None = None


class ChatResponse(BaseModel):
    answer: str
    remaining: int
    symbol: str | None = None
