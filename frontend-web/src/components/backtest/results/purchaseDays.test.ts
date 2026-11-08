import { describe, it, expect } from 'vitest'
import { purchaseDaySeries, rankOf, targetOffset, targetSummary } from './purchaseDays'

const returns = [
  { date: '2024-01-02', returnPercent: 25, returnPercentUsd: 20 },
  { date: '2024-01-03', returnPercent: 5, returnPercentUsd: 8 },
  { date: '2024-01-04', returnPercent: null, returnPercentUsd: 12 },
  { date: '2024-01-05', returnPercent: 25, returnPercentUsd: -3 },
  { date: '2024-01-08', returnPercent: 10, returnPercentUsd: 9 },
]

describe('purchaseDaySeries', () => {
  it('고른 통화의 수익률만 날짜순 그대로 두고 값이 없는 날은 뺀다', () => {
    expect(purchaseDaySeries(returns, 'KRW')).toEqual([
      { date: '2024-01-02', returnPct: 25 },
      { date: '2024-01-03', returnPct: 5 },
      { date: '2024-01-05', returnPct: 25 },
      { date: '2024-01-08', returnPct: 10 },
    ])
    expect(purchaseDaySeries(returns, 'USD').map((p) => p.returnPct)).toEqual([20, 8, 12, -3, 9])
  })

  it('매수일별 수익률이 없는 결과는 빈 목록', () => {
    expect(purchaseDaySeries(undefined, 'KRW')).toEqual([])
  })
})

describe('targetSummary', () => {
  it('목표와 같거나 높은 날을 세고, 가장 좋은 날은 같은 값이면 앞선 날', () => {
    expect(targetSummary(purchaseDaySeries(returns, 'KRW'), 10)).toEqual({
      total: 4,
      hits: 3,
      share: 75,
      best: { date: '2024-01-02', returnPct: 25 },
    })
  })

  it('날이 없으면 비율 0 · 가장 좋은 날 없음', () => {
    expect(targetSummary([], 10)).toEqual({ total: 0, hits: 0, share: 0, best: null })
  })
})

describe('targetOffset', () => {
  it('목표가 범위 안이면 위쪽 비율, 모두 목표 위면 1, 모두 아래면 0', () => {
    expect(targetOffset(-10, 30, 10)).toBe(0.5)
    expect(targetOffset(15, 30, 10)).toBe(1)
    expect(targetOffset(-5, 5, 10)).toBe(0)
  })
})

describe('rankOf', () => {
  const krw = purchaseDaySeries(returns, 'KRW')

  it('그 날짜보다 수익률이 높은 날 수 + 1 이 순위, 같은 값은 같은 순위', () => {
    expect(rankOf(krw, '2024-01-02')).toEqual({ rank: 1, total: 4 })
    expect(rankOf(krw, '2024-01-05')).toEqual({ rank: 1, total: 4 })
    expect(rankOf(krw, '2024-01-08')).toEqual({ rank: 3, total: 4 })
  })

  it('그 날짜의 값이 없으면 null', () => {
    expect(rankOf(krw, '2024-01-04')).toBeNull()
    expect(rankOf(krw, undefined)).toBeNull()
  })
})
