import { describe, it, expect } from 'vitest'
import { applyFilters, groupByDay, historyRefetchInterval, summarize } from './tradeHistory'
import type { TransactionHistoryItem } from '../../../services/api'

const buy = (tradeId: number, date: string, executedAt: string, extra: Partial<TransactionHistoryItem> = {}) =>
  ({ type: 'BUY', tradeId, accountId: 1, symbol: 'AAPL', quantity: 2, price: 100, totalAmount: 200.4, fee: 0.4, date, executedAt, status: 'COMPLETED', ...extra }) as TransactionHistoryItem
const sell = (tradeId: number, date: string, executedAt: string, extra: Partial<TransactionHistoryItem> = {}) =>
  ({ type: 'SELL', tradeId, accountId: 1, symbol: 'NVDA', quantity: 1, price: 150, totalAmount: 149.7, fee: 0.3, date, executedAt, status: 'COMPLETED', ...extra }) as TransactionHistoryItem
const dividend = (accountId: number, date: string) =>
  ({ type: 'DIVIDEND', accountId, symbol: 'AAPL', totalAmount: 0.44, grossAmount: 0.52, taxAmount: 0.08, dividendPerShare: 0.26, shares: 2, date, executedAt: `${date}T00:00:00` }) as TransactionHistoryItem

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

  it('정산 전 매수는 합계에서 빼고 따로 센다', () => {
    const summary = summarize([
      buy(1, '2026-10-01', '2026-10-01T10:00:00'),
      buy(2, '2026-10-02', '2026-10-02T10:00:00', { status: 'PENDING', totalAmount: 500, fee: 5 }),
    ])

    expect(summary.buy).toEqual({ total: 200.4, count: 1 })
    expect(summary.fee).toBeCloseTo(0.4)
    expect(summary.pending).toBe(1)
    expect(summary.cancelled).toBe(0)
  })
})

describe('historyRefetchInterval', () => {
  const now = Date.parse('2026-10-19T10:00:30')

  it('주문한 지 1분 안의 정산 전 매수가 있으면 2초마다 다시 불러온다', () => {
    expect(historyRefetchInterval([buy(1, '2026-10-19', '2026-10-19T10:00:00', { status: 'PENDING' })], now)).toBe(2000)
  })

  it('1분 넘은 정산 전 매수만 있거나, 정산 전 매수가 없거나, 내역이 비면 멈춘다', () => {
    expect(historyRefetchInterval([buy(1, '2026-10-19', '2026-10-19T09:58:00', { status: 'PENDING' })], now)).toBe(false)
    expect(historyRefetchInterval([buy(1, '2026-10-19', '2026-10-19T10:00:00')], now)).toBe(false)
    expect(historyRefetchInterval([], now)).toBe(false)
    expect(historyRefetchInterval(undefined, now)).toBe(false)
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

  it('계좌로 거르면 배당도 자기 계좌로 걸러진다', () => {
    const result = applyFilters(items, 'ALL', 1)

    expect(result.map((t) => `${t.type}-${t.tradeId ?? t.date}`)).toEqual(['BUY-1', 'SELL-3', 'DIVIDEND-2026-10-04'])
  })
})
