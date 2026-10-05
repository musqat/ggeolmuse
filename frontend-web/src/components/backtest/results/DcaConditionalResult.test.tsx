import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DcaConditionalResult } from './DcaConditionalResult'

vi.mock('@components/charts/backtest/DCAChart', () => ({ DCAChart: () => <div>DCA 차트</div> }))
vi.mock('@components/charts/backtest/ConditionalChart', () => ({
  ConditionalChart: () => <div>조건부 차트</div>,
}))

const result = {
  symbol: 'AAPL',
  startDate: '2023-01-01',
  endDate: '2024-01-01',
  totalInvested: 1200000,
  currentValueKrw: 1350000,
  totalAssetKrw: 1350000,
  remainingCashKrw: 0,
  totalReturnKrw: 150000,
  totalReturnPercent: 12.5,
  fxReturn: 20000,
  fxReturnPercent: 1.5,
  totalShares: 8,
  averagePrice: 150,
  averageFxRate: 1320,
  currentPrice: 170,
  currentFxRate: 1350,
  totalTransactions: 12,
  transactions: [{ date: '2023-01-15', shares: 1, price: 140, amount: 100000, fxRate: 1300 }],
}

const props = {
  symbol: 'AAPL',
  dcaStartDate: '2023-01-01',
  conditionalStartDate: '2023-01-01',
}

describe('DcaConditionalResult', () => {
  it('적립식이면 적립식 차트를 그린다', () => {
    render(<DcaConditionalResult result={result} mode="dca" {...props} />)

    expect(screen.getByText('DCA 차트')).toBeInTheDocument()
  })

  it('조건부면 조건부 차트를 그린다', () => {
    render(<DcaConditionalResult result={result} mode="conditional" {...props} />)

    expect(screen.getByText('조건부 차트')).toBeInTheDocument()
  })
})
