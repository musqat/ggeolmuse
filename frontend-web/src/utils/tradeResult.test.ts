import { describe, it, expect } from 'vitest'
import { formatTradeResult } from './tradeResult'
import type { TradeResult } from '../services/api'

const buy: TradeResult = {
  id: 1,
  accountId: 3,
  symbol: 'TSLL',
  tradeType: 'BUY',
  quantity: 10,
  price: 8.98,
  totalAmount: 90.02,
  fee: 0.22,
  tradeDate: '2026-10-05',
  executedAt: '2026-10-06T07:12:00',
}

describe('formatTradeResult', () => {
  it('매수는 응답의 체결가·수수료·총액을 보여준다', () => {
    expect(formatTradeResult(buy)).toBe(
      [
        '매수 주문이 체결되었습니다.',
        '',
        '종목: TSLL',
        '수량: 10주',
        '체결가: $8.98',
        '수수료: $0.22',
        '총액: $90.02',
        '거래일: 2026-10-05',
      ].join('\n')
    )
  })

  it('매도는 수수료를 뺀 받는 금액을 보여준다', () => {
    const sell: TradeResult = { ...buy, tradeType: 'SELL', price: 8.96, totalAmount: 89.38 }

    const message = formatTradeResult(sell)

    expect(message).toContain('매도 주문이 체결되었습니다.')
    expect(message).toContain('체결가: $8.96')
    expect(message).toContain('받는 금액: $89.38')
    expect(message).not.toContain('총액')
  })
})
