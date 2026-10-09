import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { SimpleResult } from './SimpleResult'

vi.mock('@components/charts/backtest/SimpleChart', () => ({ SimpleChart: () => null }))

const result = {
  mode: 'simple',
  symbol: 'AAPL',
  purchaseDate: '2023-01-03',
  investmentAmount: 300000,
  totalAssetKrw: 450000,
  currentValueKrw: 440000,
  remainingCashKrw: 10000,
  totalReturnKrw: 150000,
  totalReturnPercent: 50,
}

describe('SimpleResult', () => {
  it('요약 카드에 투자금을 보여준다', () => {
    render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)

    expect(screen.getAllByText('초기 투자금').length).toBeGreaterThan(0)
    expect(screen.getAllByText('₩300,000').length).toBeGreaterThan(0)
  })

  it('E2E 가 읽는 수익률 칸을 그대로 둔다', () => {
    render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)

    expect(screen.getByTestId('backtest-return-rate')).toBeInTheDocument()
  })

  it('평가일 기준 라벨을 쓴다', () => {
    render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)

    expect(screen.getAllByText('평가 가치').length).toBeGreaterThan(0)
    expect(screen.getByText('평가일 가격')).toBeInTheDocument()
    expect(screen.getByText('평가일 환율')).toBeInTheDocument()
    expect(screen.queryByText('현재 가격')).toBeNull()
  })
})

describe('SimpleResult 값이 없을 때', () => {
  it('환율 변동이 비어 있으면 회색 - 로 보여준다', () => {
    render(<SimpleResult result={{ ...result, fxReturn: undefined }} symbol="AAPL" purchaseDate="2023-01-03" />)

    const row = screen.getByText('환율 변동').parentElement
    expect(row).toHaveTextContent('환율 변동-')
    expect(row?.lastElementChild).toHaveClass('text-tx-3')
  })
})
