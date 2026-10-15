from app.core.models import Conversation, Turn
from app.core.router import Router
from tests.core.fakes import FakeModel, StubSkill

CONVERSATION = Conversation(id="c1", user_id="u1", symbol="AAPL", name="Apple Inc.")


def _router(text):
    model = FakeModel(choose=text)
    skills = [StubSkill("indicators"), StubSkill("trend"), StubSkill("dividend")]
    return Router(model, skills), model


async def test_picks_named_skill():
    router, _ = _router('{"skill": "dividend"}')
    decision = await router.decide(CONVERSATION, "배당은?")
    assert decision.skill.name == "dividend"
    assert decision.reason is None
    assert decision.tokens == 10


async def test_skill_wins_over_reason():
    router, _ = _router('{"skill": "trend", "reason": "off_topic"}')
    assert (await router.decide(CONVERSATION, "1년이면?")).skill.name == "trend"


async def test_other_symbol_reason_passes_through():
    router, _ = _router('{"skill": null, "reason": "other_symbol"}')
    decision = await router.decide(CONVERSATION, "MSFT 는?")
    assert decision.skill is None
    assert decision.reason == "other_symbol"


async def test_unknown_skill_or_garbage_is_off_topic():
    for text in ('{"skill": "news"}', "쓰레기", "[]", '{"skill": null, "reason": "weather"}', ""):
        router, _ = _router(text)
        decision = await router.decide(CONVERSATION, "날씨 어때?")
        assert (decision.skill, decision.reason) == (None, "off_topic")


async def test_prompt_lists_skills_symbol_and_previous_turn():
    router, model = _router('{"skill": "trend"}')
    conversation = Conversation(id="c1", user_id="u1", symbol="AAPL", name="Apple Inc.",
                                turns=(Turn(question="RSI 어때?", answer="중립", skill="indicators"),))
    await router.decide(conversation, "왜?")

    system, user = model.chosen[0]
    assert "AAPL (Apple Inc.)" in system["content"]
    for name in ("indicators", "trend", "dividend"):
        assert f"- {name}: {name} 설명" in system["content"]
    assert "직전 스킬: indicators" in system["content"]
    assert "직전 질문: RSI 어때?" in system["content"]
    assert user == {"role": "user", "content": "왜?"}
