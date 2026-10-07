import { describe, it, expect } from 'vitest'
import { addAmount } from './amount'

describe('addAmount', () => {
  it('누를 때마다 더한다', () => {
    expect(addAmount('', 10000000)).toBe('10000000')
    expect(addAmount('10000000', 10000000)).toBe('20000000')
    expect(addAmount('20000000', 1000000)).toBe('21000000')
  })

  it('숫자가 아니면 0 에서 시작한다', () => {
    expect(addAmount('abc', 1000)).toBe('1000')
  })

  it('USD 소수는 센트까지 맞춘다', () => {
    expect(addAmount('68.11', 1000)).toBe('1068.11')
    expect(addAmount('0.1', 0.2)).toBe('0.3')
  })
})
