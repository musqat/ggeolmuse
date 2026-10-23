import { describe, it, expect } from 'vitest'
import type { AccountBalance, HoldingResponse } from '../services/api'
import { allocationItems, totalAssetsUsd } from './portfolioAllocation'

const holding = (symbol: string, currentValue: number) =>
  ({ symbol, currentValue, totalInvestedAmount: currentValue, totalQuantity: 1, currentPrice: currentValue }) as HoldingResponse
const balance = (balanceUsd: number, balanceKrw: number) => ({ balanceUsd, balanceKrw }) as AccountBalance

describe('portfolioAllocation', () => {
  it('자산은 금액 큰 순으로 색을 받고 여섯째부터 회색', () => {
    const holdings = [holding('A', 10), holding('B', 60), holding('C', 30), holding('D', 50), holding('E', 20)]

    const items = allocationItems(holdings, balance(40, 1_350), 1_350)

    expect(items.map((item) => `${item.symbol}:${item.color}`)).toEqual([
      'B:var(--alloc-1)',
      'D:var(--alloc-2)',
      'USD 현금:var(--alloc-3)',
      'C:var(--alloc-4)',
      'E:var(--alloc-5)',
      'A:var(--alloc-etc)',
      'KRW 현금:var(--alloc-etc)',
    ])
  })

  it('총자산은 주식 + 달러 + 원화 ÷ 환율', () => {
    expect(totalAssetsUsd({ totalCurrentValue: 200 } as never, balance(100, 135_000), 1_350)).toBe(400)
  })
})
