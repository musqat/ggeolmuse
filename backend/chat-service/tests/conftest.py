import asyncio
import inspect

import fakeredis.aioredis
import pytest


class InterleavingRedis:
    """명령 응답을 받을 때마다 이벤트 루프에 한 번 양보하는 Redis.
    fakeredis 는 명령 사이에 양보하지 않아 동시 요청이 차례로 끝난다. 이걸 쓰면 한 요청의 읽기와 쓰기 사이에
    다른 요청이 끼어들어, '읽고 고쳐 쓰기' 로 바뀐 코드가 동시성 테스트에서 실패한다"""

    def __init__(self, redis):
        self._redis = redis

    def __getattr__(self, name):
        attr = getattr(self._redis, name)
        if not callable(attr):
            return attr

        def call(*args, **kwargs):
            result = attr(*args, **kwargs)
            # 명령은 코루틴을 돌려준다. pipeline() 처럼 객체를 돌려주는 것은 그대로 둔다
            return _then_yield(result) if inspect.iscoroutine(result) else result

        return call


async def _then_yield(command):
    result = await command
    await asyncio.sleep(0)
    return result


@pytest.fixture
def fake_redis():
    return fakeredis.aioredis.FakeRedis(decode_responses=True)


@pytest.fixture
def interleaving_redis(fake_redis):
    return InterleavingRedis(fake_redis)
