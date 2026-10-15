from pydantic import BaseModel, Field


class StartRequest(BaseModel):
    symbol: str = Field(min_length=1, max_length=32)


class StartResponse(BaseModel):
    conversation_id: str
    symbol: str
    name: str | None
    remaining: int


class AskRequest(BaseModel):
    conversation_id: str = Field(min_length=1, max_length=64)
    message: str = Field(min_length=1, max_length=500)


class QuotaResponse(BaseModel):
    remaining: int


class AskResponse(BaseModel):
    answer: str
    remaining: int
    conversation_id: str
    symbol: str
    rejected: str | None  # off_topic · other_symbol · insufficient_data
