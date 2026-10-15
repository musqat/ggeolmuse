import asyncio

from app.core.models import Turn
from app.infra.conversations import RedisConversationStore


def _store(redis, ttl=18_000, max_turns=3, answer_chars=800):
    return RedisConversationStore(redis, ttl_seconds=ttl, max_turns=max_turns, answer_chars=answer_chars)


async def test_create_then_load(fake_redis):
    store = _store(fake_redis)
    created = await store.create("u1", "AAPL", "Apple Inc.")
    loaded = await store.load("u1", created.id)

    assert (loaded.id, loaded.symbol, loaded.name, loaded.turns) == (created.id, "AAPL", "Apple Inc.", ())
    assert 0 < await fake_redis.ttl(f"aichat:conv:u1:{created.id}") <= 18_000


async def test_name_none_round_trips(fake_redis):
    store = _store(fake_redis)
    created = await store.create("u1", "NEW", None)
    assert (await store.load("u1", created.id)).name is None


async def test_other_user_or_bad_id_is_none(fake_redis):
    store = _store(fake_redis)
    created = await store.create("u1", "AAPL", None)
    assert await store.load("u2", created.id) is None
    assert await store.load("u1", "not-a-uuid") is None
    assert await store.load("u1", "00000000-0000-0000-0000-000000000000") is None


async def test_append_keeps_last_turns_and_refreshes_ttl(fake_redis):
    store = _store(fake_redis, ttl=100, max_turns=3)
    conversation = await store.create("u1", "AAPL", None)
    await fake_redis.expire(f"aichat:conv:u1:{conversation.id}", 5)
    for i in range(5):
        await store.append(conversation, Turn(question=f"q{i}", answer=f"a{i}", skill="trend"))

    loaded = await store.load("u1", conversation.id)
    assert [t.question for t in loaded.turns] == ["q2", "q3", "q4"]
    assert loaded.turns[-1] == Turn(question="q4", answer="a4", skill="trend")
    assert 5 < await fake_redis.ttl(f"aichat:conv:u1:{conversation.id}") <= 100
    assert 0 < await fake_redis.ttl(f"aichat:conv:u1:{conversation.id}:turns") <= 100


async def test_concurrent_appends_keep_both(interleaving_redis):
    store = _store(interleaving_redis)
    conversation = await store.create("u1", "AAPL", None)
    await asyncio.gather(*(store.append(conversation, Turn(question=f"q{i}", answer="a", skill="trend"))
                           for i in range(2)))
    assert sorted(t.question for t in (await store.load("u1", conversation.id)).turns) == ["q0", "q1"]


async def test_append_cuts_long_answers(fake_redis):
    store = _store(fake_redis, answer_chars=10)
    conversation = await store.create("u1", "AAPL", None)
    await store.append(conversation, Turn(question="q", answer="가" * 30, skill="indicators"))
    assert (await store.load("u1", conversation.id)).turns[0].answer == "가" * 10
