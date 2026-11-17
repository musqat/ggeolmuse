import { describe, it, expect } from 'vitest'
import { summarizeHoldings } from './accountSummary'
import type { HoldingResponse } from '../services/api'

const holding = (symbol: string, quantity: number, invested: number, currentPrice?: number) =>
  ({ symbol, totalQuantity: quantity, totalInvestedAmount: invested, currentPrice }) as HoldingResponse

describe('summarizeHoldings', () => {
  it('손익 · 수익률은 현재가 있는 종목의 투자금 기준, 투자금 합계는 모든 종목', () => {
    const summary = summarizeHoldings([holding('AAPL', 2, 180, 100), holding('MSFT', 5, 500)])

    expect(summary).toEqual({
      totalInvestedAmount: 680, totalCurrentValue: 200, totalUnrealizedPnL: 20, totalReturnRate: 11.11,
      unpricedSymbols: ['MSFT'],
    })
  })

  it('현재가가 하나도 없으면 손익 · 수익률 0 이고 같은 종목은 한 번만 알린다', () => {
    const summary = summarizeHoldings([holding('MSFT', 5, 500), holding('MSFT', 1, 100, 0)])

    expect(summary.totalUnrealizedPnL).toBe(0)
    expect(summary.totalReturnRate).toBe(0)
    expect(summary.unpricedSymbols).toEqual(['MSFT'])
  })
})
