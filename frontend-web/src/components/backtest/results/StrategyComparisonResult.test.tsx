import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StrategyComparisonResult } from './StrategyComparisonResult'

vi.mock('@components/charts/backtest/CompareStrategiesChart', () => ({
  CompareStrategiesChart: () => null,
}))

const result = {
  mode: 'compare-strategies',
  bestPerformer: { name: 'SIMPLE', code: 'AAPL', totalReturnPercent: 10 },
  items: [
    { name: 'SIMPLE', code: 'AAPL', totalInvested: 1000000, currentValueKrw: 1100000, totalReturnKrw: 100000, totalReturnPercent: 10 },
    { name: 'DCA', code: 'AAPL', totalInvested: 1000000, currentValueKrw: 995000, totalReturnKrw: -5000, totalReturnPercent: -0.5 },
  ],
}

describe('StrategyComparisonResult', () => {
  it('전략 이름을 한글로 보여준다', () => {
    render(<StrategyComparisonResult result={result} />)

    expect(screen.getAllByText('단순 매수').length).toBeGreaterThan(0)
    expect(screen.getAllByText('적립식').length).toBeGreaterThan(0)
  })

  it('최종 가치는 배당 현금까지 넣은 총자산이라 투자금 + 총 수익과 맞는다', () => {
    const withCash = {
      ...result,
      items: [{ ...result.items[0], totalReturnKrw: 130000, additionalData: { totalAssetKrw: 1130000 } }],
    }
    render(<StrategyComparisonResult result={withCash} />)

    expect(screen.getByText('₩1,130,000')).toBeInTheDocument()
    expect(screen.getByText('+₩130,000')).toBeInTheDocument()
  })

  it('손실은 부호를 통화 앞에 둔다', () => {
    render(<StrategyComparisonResult result={result} />)

    expect(screen.getByText('-₩5,000')).toBeInTheDocument()
  })
})

describe('StrategyComparisonResult 값이 없을 때', () => {
  it('수익이 비어 있으면 회색 - 로 보여준다', () => {
    const missing = {
      ...result,
      items: [{ name: 'CONDITIONAL_PURCHASE', code: 'AAPL', totalInvested: 1000000, currentValueKrw: 1000000, totalReturnPercent: 0 }],
    }
    render(<StrategyComparisonResult result={missing} />)

    const dashes = screen.getAllByText('-')
    expect(dashes.length).toBeGreaterThan(0)
    expect(dashes[0]).toHaveClass('text-tx-3')
  })
})
