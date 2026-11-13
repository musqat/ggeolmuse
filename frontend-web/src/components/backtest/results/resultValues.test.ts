import { describe, it, expect } from 'vitest'
import { isYearEndHarvest, purchaseCount, sellCount } from './resultValues'
import { zeroOffset } from './resultValues'

describe('매수 · 매도 수', () => {
  it('연말 정리 매도 · 재매수는 매수 · 매도 수에 세지 않는다', () => {
    const transactions = [
      { trigger: '월정액', shares: 10 },
      { trigger: '공제 채우기 매도', shares: -4 },
      { trigger: '공제 채우기 재매수', shares: 3.99 },
      { trigger: '손실 상계 매도', shares: -1 },
      { trigger: '손실 상계 재매수', shares: 0.99 },
      { trigger: '데드 크로스', shares: -9.98 },
    ]

    expect(purchaseCount(transactions)).toBe(1)
    expect(sellCount(transactions)).toBe(1)
    expect(transactions.filter(isYearEndHarvest)).toHaveLength(4)
  })
})

describe('zeroOffset', () => {
  it('0 이 범위 안이면 위쪽 비율, 모두 이익이면 1, 모두 손실이면 0', () => {
    expect(zeroOffset(-10, 30)).toBe(0.75)
    expect(zeroOffset(5, 30)).toBe(1)
    expect(zeroOffset(-30, -5)).toBe(0)
  })
})
