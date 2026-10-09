import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DcaConditionalResult } from './DcaConditionalResult'

// 차트는 시작일만 보인다. 결과에 시작일이 없을 때 모드별 시작일을 넘기는지 본다
vi.mock('@components/charts/backtest/AccumulationChart', () => ({
  AccumulationChart: ({ startDate }: { startDate: string }) => <div>차트 시작 {startDate}</div>,
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
  conditionalStartDate: '2022-06-01',
}

describe('DcaConditionalResult', () => {
  it('결과의 시작일로 차트를 그린다', () => {
    render(<DcaConditionalResult result={result} mode="conditional" {...props} />)

    expect(screen.getByText('차트 시작 2023-01-01')).toBeInTheDocument()
  })

  it('결과에 시작일이 없으면 모드별 시작일을 쓴다', () => {
    const { startDate: _omit, ...withoutStart } = result
    void _omit
    const { unmount } = render(<DcaConditionalResult result={withoutStart} mode="dca" {...props} />)
    expect(screen.getByText('차트 시작 2023-01-01')).toBeInTheDocument()
    unmount()

    render(<DcaConditionalResult result={withoutStart} mode="conditional" {...props} />)
    expect(screen.getByText('차트 시작 2022-06-01')).toBeInTheDocument()
  })

  it('평가일 기준 라벨을 쓴다', () => {
    render(<DcaConditionalResult result={result} mode="dca" {...props} />)

    expect(screen.getAllByText('평가 가치').length).toBeGreaterThan(0)
    expect(screen.getByText('평가일 가격')).toBeInTheDocument()
    expect(screen.getByText('평가일 환율')).toBeInTheDocument()
    expect(screen.queryByText('현재 가격')).toBeNull()
  })
})
