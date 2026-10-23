import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { SimpleResult } from './SimpleResult'
import { SimpleChart } from '@components/charts/backtest/SimpleChart'

vi.mock('@components/charts/backtest/SimpleChart', () => ({ SimpleChart: vi.fn(() => null) }))

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

  it('차트를 평가일까지 그리게 넘긴다', () => {
    render(<SimpleResult result={{ ...result, currentDate: '2024-06-14' }} symbol="AAPL" purchaseDate="2023-01-03" />)

    expect(vi.mocked(SimpleChart).mock.lastCall?.[0]).toMatchObject({ endDate: '2024-06-14' })
  })

  it('평가일 기준 라벨을 쓴다', () => {
    render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)

    expect(screen.getAllByText('평가 가치').length).toBeGreaterThan(0)
    expect(screen.getByText('평가일 가격')).toBeInTheDocument()
    expect(screen.getByText('평가일 환율')).toBeInTheDocument()
    expect(screen.queryByText('현재 가격')).toBeNull()
  })

  it('평가 가치의 현금은 총자산에서 주식을 뺀 값이라 받은 배당이 들어간다', () => {
    render(<SimpleResult result={{ ...result, remainingCashKrw: 0 }} symbol="AAPL" purchaseDate="2023-01-03" />)

    expect(screen.getAllByText('주식: ₩440,000, 현금: ₩10,000')).toHaveLength(2)
  })

  it('고른 매수일이 휴장일이라 다른 날 샀으면 알린다', () => {
    render(<SimpleResult result={{ ...result, purchaseDate: '2023-01-03' }} symbol="AAPL" purchaseDate="2023-01-01" />)

    expect(screen.getByText('선택한 매수일(2023-01-01)은 휴장일이라 다음 거래일(2023-01-03) 종가로 샀습니다.')).toBeInTheDocument()
  })

  it('고른 날에 샀으면 알리지 않는다', () => {
    render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)

    expect(screen.queryByText(/휴장일이라/)).toBeNull()
  })

  it('보유 주식수는 소수 둘째 자리까지 보여준다', () => {
    render(<SimpleResult result={{ ...result, shares: 3.141592 }} symbol="AAPL" purchaseDate="2023-01-03" />)

    expect(screen.getByText('3.14 주')).toBeInTheDocument()
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
