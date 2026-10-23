import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { CompareSymbolsChart } from '@components/charts/backtest/CompareSymbolsChart'
import { CompareSymbolsChartMemoized } from './CompareSymbolsChartMemoized'

vi.mock('@components/charts/backtest/CompareSymbolsChart', () => ({
  CompareSymbolsChart: vi.fn(() => null),
}))

describe('CompareSymbolsChartMemoized', () => {
  it('차트에 항목 이름이 아니라 종목 코드로 종목을, 항목의 최적 매수 · 매도일과 함께 넘긴다', () => {
    render(
      <CompareSymbolsChartMemoized
        items={[{ code: 'AAPL', name: '애플', totalReturnPercent: 12, optimalBuyDate: '2023-01-05', optimalSellDate: '2026-07-28' }]}
        comparePurchaseDate="2023-01-03"
        compareSaleDate=""
      />
    )

    const props = vi.mocked(CompareSymbolsChart).mock.calls[0][0]
    expect(props.symbols[0]).toMatchObject({ symbol: 'AAPL', optimalBuyDate: '2023-01-05', optimalSellDate: '2026-07-28' })
  })
})
