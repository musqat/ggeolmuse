import { describe, it, expect } from 'vitest'
import { createIdempotencyKeys } from './idempotencyKey'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

describe('중복 요청 키', () => {
  it('같은 입력이면 같은 키를 준다', () => {
    const keys = createIdempotencyKeys()

    const first = keys.keyFor({ accountId: 3, amount: 1000 })

    expect(first).toMatch(UUID)
    expect(keys.keyFor({ accountId: 3, amount: 1000 })).toBe(first)
  })

  it('입력이 바뀌면 새 키를 준다', () => {
    const keys = createIdempotencyKeys()
    const first = keys.keyFor({ accountId: 3, amount: 1000 })

    expect(keys.keyFor({ accountId: 3, amount: 2000 })).not.toBe(first)
  })

  it('보안 컨텍스트가 아니어서 randomUUID 가 없어도 UUID v4 키를 준다', () => {
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true })
    try {
      const keys = createIdempotencyKeys()

      const key = keys.keyFor({ accountId: 3, amount: 1000 })

      expect(key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
      expect(keys.keyFor({ accountId: 3, amount: 2000 })).not.toBe(key)
    } finally {
      Reflect.deleteProperty(crypto, 'randomUUID')
    }
  })

  it('성공을 알리면 같은 입력이라도 다음에는 새 키를 준다', () => {
    const keys = createIdempotencyKeys()
    const first = keys.keyFor({ accountId: 3, amount: 1000 })

    keys.reset()

    expect(keys.keyFor({ accountId: 3, amount: 1000 })).not.toBe(first)
  })
})
