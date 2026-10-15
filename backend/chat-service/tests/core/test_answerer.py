from app.core.answerer import Answerer, strip_markdown
from app.core.messages import ANSWER_RULES
from app.core.models import Conversation, Evidence, Turn
from tests.core.fakes import TODAY, FakeModel, StubSkill

EVIDENCE = Evidence(lines=("RSI(14): 44.2 (중립)", "MA20 110.29"), as_of=TODAY)


def _conversation(turns=()):
    return Conversation(id="c1", user_id="u1", symbol="AAPL", name="Apple Inc.", turns=turns)


async def test_messages_have_rules_history_and_evidence():
    model = FakeModel()
    turns = tuple(Turn(question=f"질문{i}", answer=f"답{i}", skill="indicators") for i in range(4))
    await Answerer(model, history_turns=3).answer(_conversation(turns), StubSkill(), EVIDENCE, "더 자세히")

    messages = model.answered[0]
    assert messages[0]["role"] == "system"
    assert ANSWER_RULES in messages[0]["content"]
    assert "indicators 지침" in messages[0]["content"]
    assert [m["content"] for m in messages[1:7]] == ["질문1", "답1", "질문2", "답2", "질문3", "답3"]
    last = messages[-1]["content"]
    assert "종목: AAPL (Apple Inc.)" in last
    assert "기준일: 2026-10-16" in last
    assert "- RSI(14): 44.2 (중립)\n- MA20 110.29" in last
    assert last.endswith("질문: 더 자세히")


async def test_answer_strips_markdown_but_keeps_bold():
    model = FakeModel(answer="# 제목\n*기울임* **굵게**\n- 항목")
    completion = await Answerer(model, history_turns=3).answer(_conversation(), StubSkill(), EVIDENCE, "어때?")
    assert completion.text == "제목\n기울임 **굵게**\n항목"
    assert completion.tokens == 100


def test_strip_markdown_keeps_plain_text():
    assert strip_markdown("RSI 는 **중립**입니다") == "RSI 는 **중립**입니다"
