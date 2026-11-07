import { describe, it, expect } from 'vitest'
import { assetSeries, depositPoints, simpleDeposits, strategyDeposits } from './assetSeries'

const values = [
  { date: '2024-01-02', valueUsd: 0, valueKrw: 0 },
  { date: '2024-01-03', valueUsd: 100, valueKrw: 130000 },
  { date: '2024-01-08', valueUsd: 210, valueKrw: 280000 },
  { date: '2024-01-09', valueUsd: 190, valueKrw: 250000 },
]

const deposits = [
  { date: '2024-01-03', krw: 130000, usd: 100 },
  { date: '2024-01-06', krw: 130000, usd: 98 },
]

describe('assetSeries', () => {
  it('원화는 그날까지 넣은 돈을 더하고 평가 가치 ÷ 넣은 돈 - 1 을 수익률로 둔다', () => {
    expect(assetSeries(values, deposits, 'KRW')).toEqual([
      { date: '2024-01-02', value: 0, invested: null, returnPct: null },
      { date: '2024-01-03', value: 130000, invested: 130000, returnPct: 0 },
      { date: '2024-01-08', value: 280000, invested: 260000, returnPct: (280000 / 260000 - 1) * 100 },
      { date: '2024-01-09', value: 250000, invested: 260000, returnPct: (250000 / 260000 - 1) * 100 },
    ])
  })

  it('달러는 매수마다 달러로 바꾼 금액을 더한다', () => {
    const usd = assetSeries(values, deposits, 'USD')
    expect(usd[2]).toEqual({ date: '2024-01-08', value: 210, invested: 198, returnPct: (210 / 198 - 1) * 100 })
  })

  it('달러 금액을 모르는 매수가 있으면 그날부터 달러 넣은 돈을 비운다', () => {
    const usd = assetSeries(values, [deposits[0], { ...deposits[1], usd: null }], 'USD')
    expect(usd.map((p) => p.invested)).toEqual([null, 100, null, null])
  })
})

describe('depositPoints', () => {
  it('매수한 날의 점을 찍고, 일별 값이 없는 날이면 다음 날에 하나만 찍는다', () => {
    const points = assetSeries(values, [...deposits, { date: '2024-01-07', krw: 1, usd: 1 }], 'KRW')
    expect(depositPoints(points, [...deposits, { date: '2024-01-07', krw: 1, usd: 1 }]).map((p) => p.date))
      .toEqual(['2024-01-03', '2024-01-08'])
  })
})

describe('넣은 돈 목록', () => {
  it('단순 매수는 투자금 하나, 달러는 매수 환율로 나눈다', () => {
    expect(simpleDeposits('2024-01-03', 1300000, 1300)).toEqual([{ date: '2024-01-03', krw: 1300000, usd: 1000 }])
    expect(simpleDeposits('2024-01-03', 1300000, null)).toEqual([{ date: '2024-01-03', krw: 1300000, usd: null }])
    expect(simpleDeposits(null, 1300000, 1300)).toEqual([])
  })

  it('적립식 · 조건부는 실제 거래일에 금액을 더하고 배당 재투자(금액 0)는 뺀다', () => {
    const transactions = [
      { date: '2024-01-06', actualDate: '2024-01-08', price: 10, shares: 1, amount: 130000, fxRate: 1300, fee: 0 },
      { date: '2024-02-10', price: 10, shares: 0.1, amount: 0, fxRate: 1300, fee: 0, trigger: '배당 재투자' },
      { date: '2024-03-04', price: 10, shares: 1, amount: 130000, fxRate: 0, fee: 0 },
    ]
    expect(strategyDeposits(transactions)).toEqual([
      { date: '2024-01-08', krw: 130000, usd: 100 },
      { date: '2024-03-04', krw: 130000, usd: null },
    ])
  })
})
