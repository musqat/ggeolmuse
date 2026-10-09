import { describe, it, expect } from 'vitest'
import { applyFilters, buyDates, groupByDay, summarize } from './tradeHistory'
import type { TransactionHistoryItem } from '../../../services/api'

const buy = (tradeId: number, date: string, executedAt: string, extra: Partial<TransactionHistoryItem> = {}) =>
  ({ type: 'BUY', tradeId, accountId: 1, symbol: 'AAPL', quantity: 2, price: 100, totalAmount: 200.4, fee: 0.4, date, executedAt, status: 'COMPLETED', ...extra }) as TransactionHistoryItem
const sell = (tradeId: number, date: string, executedAt: string, extra: Partial<TransactionHistoryItem> = {}) =>
  ({ type: 'SELL', tradeId, accountId: 1, symbol: 'NVDA', quantity: 1, price: 150, totalAmount: 149.7, fee: 0.3, date, executedAt, status: 'COMPLETED', ...extra }) as TransactionHistoryItem
const dividend = (tradeId: number, date: string) =>
  ({ type: 'DIVIDEND', tradeId, symbol: 'AAPL', totalAmount: 0.44, grossAmount: 0.52, taxAmount: 0.08, dividendPerShare: 0.26, shares: 2, date, executedAt: `${date}T00:00:00` }) as TransactionHistoryItem

describe('groupByDay', () => {
  it('매수 · 매도 · 배당을 거래일 최신순으로 섞어 묶고, 같은 날은 주문 시각 최신순이다', () => {
    const days = groupByDay([
      buy(1, '2025-01-02', '2026-10-01T10:00:00'),
      dividend(1, '2026-08-14'),
      sell(2, '2026-10-08', '2026-10-09T15:12:00'),
      buy(3, '2026-10-08', '2026-10-09T14:58:00'),
    ])

    expect(days.map((d) => d.date)).toEqual(['2026-10-08', '2026-08-14', '2025-01-02'])
    expect(days[0].items.map((t) => t.tradeId)).toEqual([2, 3])
    expect(days[1].items[0].type).toBe('DIVIDEND')
  })
})

describe('summarize', () => {
  it('유형별 합계와 건수를 내고, 취소된 거래는 빼고 따로 센다', () => {
    const summary = summarize([
      buy(1, '2026-10-01', '2026-10-01T10:00:00'),
      buy(2, '2026-10-02', '2026-10-02T10:00:00', { status: 'CANCELLED', totalAmount: 999, fee: 9 }),
      sell(3, '2026-10-03', '2026-10-03T10:00:00'),
      dividend(1, '2026-10-04'),
      dividend(1, '2026-10-05'),
    ])

    expect(summary.buy).toEqual({ total: 200.4, count: 1 })
    expect(summary.sell).toEqual({ total: 149.7, count: 1 })
    expect(summary.dividend.count).toBe(2)
    expect(summary.dividend.total).toBeCloseTo(0.88)
    expect(summary.fee).toBeCloseTo(0.7)
    expect(summary.cancelled).toBe(1)
  })
})

describe('applyFilters', () => {
  const items = [
    buy(1, '2026-10-01', '2026-10-01T10:00:00', { accountId: 1 }),
    buy(2, '2026-10-02', '2026-10-02T10:00:00', { accountId: 2 }),
    sell(3, '2026-10-03', '2026-10-03T10:00:00', { accountId: 1 }),
    dividend(1, '2026-10-04'),
    dividend(2, '2026-10-05'),
  ]

  it('유형으로 거른다', () => {
    expect(applyFilters(items, 'SELL', 'ALL').map((t) => t.tradeId)).toEqual([3])
    expect(applyFilters(items, 'DIVIDEND', 'ALL')).toHaveLength(2)
  })

  it('계좌로 거르면 배당은 연결된 매수의 계좌를 따른다', () => {
    const result = applyFilters(items, 'ALL', 1)

    expect(result.map((t) => `${t.type}-${t.tradeId}`)).toEqual(['BUY-1', 'SELL-3', 'DIVIDEND-1'])
  })
})

describe('buyDates', () => {
  it('매수 tradeId 로 그 거래일을 찾는다', () => {
    const dates = buyDates([buy(1, '2025-01-02', '2025-01-02T10:00:00'), sell(2, '2026-01-01', '2026-01-01T10:00:00')])

    expect(dates.get(1)).toBe('2025-01-02')
    expect(dates.has(2)).toBe(false)
  })
})
