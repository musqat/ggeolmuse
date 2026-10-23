import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DcaConditionalResult } from './DcaConditionalResult'

// 차트는 기간만 보임(결과에 시작일이 없으면 넘겨받은 시작일)
vi.mock('@components/charts/backtest/AccumulationChart', () => ({
  AccumulationChart: ({ startDate, endDate }: { startDate: string; endDate?: string }) => (
    <div>
      <div>차트 시작 {startDate}</div>
      <div>차트 끝 {endDate ?? '오늘'}</div>
    </div>
  ),
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
  startDate: '2022-06-01',
}

describe('DcaConditionalResult', () => {
  it('결과의 시작일부터 평가일까지 차트를 그린다', () => {
    render(<DcaConditionalResult result={result} {...props} />)

    expect(screen.getByText('차트 시작 2023-01-01')).toBeInTheDocument()
    expect(screen.getByText('차트 끝 2024-01-01')).toBeInTheDocument()
  })

  it('결과에 시작일이 없으면 넘겨받은 시작일부터 그린다', () => {
    const { startDate: _omit, ...withoutStart } = result
    void _omit
    render(<DcaConditionalResult result={withoutStart} {...props} />)

    expect(screen.getByText('차트 시작 2022-06-01')).toBeInTheDocument()
  })

  it('평가일 기준 라벨을 쓴다', () => {
    render(<DcaConditionalResult result={result} {...props} />)

    expect(screen.getAllByText('평가 가치').length).toBeGreaterThan(0)
    expect(screen.getByText('평가일 가격')).toBeInTheDocument()
    expect(screen.getByText('평가일 환율')).toBeInTheDocument()
    expect(screen.queryByText('현재 가격')).toBeNull()
  })

  it('보유 주식수 · 달러 주식 평가액은 소수 둘째 자리까지 보여준다', () => {
    render(<DcaConditionalResult result={{ ...result, totalShares: 8.123456, currentValue: 1234.5678 }} {...props} />)

    expect(screen.getByText('8.12 주')).toBeInTheDocument()
    expect(screen.getByText('$1,234.57')).toBeInTheDocument()
  })

  it('위 카드 평가 가치는 배당 현금까지 넣은 총자산, 투자 정보는 주식 평가액만 보여준다', () => {
    render(<DcaConditionalResult result={{ ...result, currentValueKrw: 1300000 }} {...props} />)

    expect(screen.getByText('주식: ₩1,300,000, 현금: ₩50,000')).toBeInTheDocument()
    expect(screen.getByText('주식 평가액').nextElementSibling).toHaveTextContent('₩1,300,000')
  })

  it('투자일이 휴장일이라 다른 날 산 번수를 알린다', () => {
    const transactions = [
      { date: '2023-01-15', actualDate: '2023-01-17', shares: 1, price: 140, amount: 100000, fxRate: 1300 },
      { date: '2023-02-15', actualDate: '2023-02-15', shares: 1, price: 145, amount: 100000, fxRate: 1300 },
    ]
    render(<DcaConditionalResult result={{ ...result, transactions }} {...props} />)

    expect(screen.getByText('투자일이 휴장일이면 다음 거래일 종가로 삽니다(이번 결과 1번).')).toBeInTheDocument()
  })

  it('환율은 소수 둘째 자리까지, 하락은 부호를 통화 앞에 둔다', () => {
    render(<DcaConditionalResult result={{ ...result, currentFxRate: 1325.5, fxReturn: -30.02 }} {...props} />)

    expect(screen.getByText('₩1,325.50')).toBeInTheDocument()
    expect(screen.getByText('-₩30.02')).toBeInTheDocument()
  })
})
