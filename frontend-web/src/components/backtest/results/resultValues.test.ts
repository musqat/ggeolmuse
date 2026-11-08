import { describe, it, expect } from 'vitest'
import { zeroOffset } from './resultValues'

describe('zeroOffset', () => {
  it('0 이 범위 안이면 위쪽 비율, 모두 이익이면 1, 모두 손실이면 0', () => {
    expect(zeroOffset(-10, 30)).toBe(0.75)
    expect(zeroOffset(5, 30)).toBe(1)
    expect(zeroOffset(-30, -5)).toBe(0)
  })
})
