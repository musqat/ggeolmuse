import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StrategyComparisonResult } from './StrategyComparisonResult'

vi.mock('@components/charts/backtest/CompareStrategiesChart', () => ({
  CompareStrategiesChart: () => null,
}))

const result = {
  mode: 'compare-strategies',
  bestPerformer: { name: 'SIMPLE', totalReturnPercent: 10 },
  items: [
    { name: 'SIMPLE', totalInvested: 1000000, currentValueKrw: 1100000, totalReturnKrw: 100000, totalReturnPercent: 10 },
    { name: 'DCA', totalInvested: 1000000, currentValueKrw: 995000, totalReturnKrw: -5000, totalReturnPercent: -0.5 },
  ],
}

describe('StrategyComparisonResult', () => {
  it('전략 이름을 한글로 보여준다', () => {
    render(<StrategyComparisonResult result={result} />)

    expect(screen.getAllByText('단순 매수').length).toBeGreaterThan(0)
    expect(screen.getAllByText('적립식').length).toBeGreaterThan(0)
  })
})
