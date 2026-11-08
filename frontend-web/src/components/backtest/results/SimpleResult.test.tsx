import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SimpleResult } from './SimpleResult'
import { SimpleChart } from '@components/charts/backtest/SimpleChart'
import { PurchaseDayChart } from './PurchaseDayChart'

vi.mock('@components/charts/backtest/SimpleChart', () => ({ SimpleChart: vi.fn(() => null) }))
vi.mock('./PurchaseDayChart', () => ({ PurchaseDayChart: vi.fn(() => null) }))

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

const open = (title: string) => userEvent.click(screen.getByText(title))

describe('SimpleResult', () => {
  it('카드 넷과 자산 추이를 위에 두고 상세 · 주가 · 수익률 추이는 닫아 둔다', () => {
    render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)

    for (const label of ['총 수익률', '연평균 수익률', '최대 낙폭']) expect(screen.getByText(label)).toBeInTheDocument()
    expect(screen.getByText('자산 추이')).toBeInTheDocument()
    expect(screen.getByText('상세').closest('details')).not.toHaveAttribute('open')
    expect(screen.getByText('주가 · 수익률 추이').closest('details')).not.toHaveAttribute('open')
    expect(vi.mocked(SimpleChart)).not.toHaveBeenCalled()
  })

  it('요약 카드에 투자금을 보여준다', () => {
    render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)

    expect(screen.getByText('총 수익률').parentElement).toHaveTextContent('초기 투자금 ₩300,000 대비 +₩150,000')
  })

  it('E2E 가 읽는 수익률 칸을 그대로 둔다', () => {
    render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)

    expect(screen.getByTestId('backtest-return-rate')).toBeInTheDocument()
  })

  it('주가 · 수익률 추이를 열면 차트를 평가일까지 그리게 넘긴다', async () => {
    render(<SimpleResult result={{ ...result, currentDate: '2024-06-14' }} symbol="AAPL" purchaseDate="2023-01-03" />)

    await open('주가 · 수익률 추이')

    expect(vi.mocked(SimpleChart).mock.lastCall?.[0]).toMatchObject({ endDate: '2024-06-14' })
  })

  it('매수일별 수익률은 닫아 두고, 열면 결과의 매수일별 수익률을 고른 통화 · 평가일과 넘긴다', async () => {
    const purchaseDayReturns = [{ date: '2023-01-03', returnPercent: 50, returnPercentUsd: 40 }]
    render(
      <SimpleResult
        result={{ ...result, currentDate: '2024-06-14', purchaseDayReturns, metricsKrw: {}, metricsUsd: {} }}
        symbol="AAPL"
        purchaseDate="2023-01-03"
      />,
    )

    expect(screen.getByText('매수일별 수익률').closest('details')).not.toHaveAttribute('open')
    expect(vi.mocked(PurchaseDayChart)).not.toHaveBeenCalled()

    await open('매수일별 수익률')
    expect(vi.mocked(PurchaseDayChart).mock.lastCall?.[0]).toEqual({
      returns: purchaseDayReturns,
      currency: 'KRW',
      purchaseDate: '2023-01-03',
      valuationDate: '2024-06-14',
    })

    await userEvent.click(screen.getByRole('button', { name: '달러 기준' }))
    expect(vi.mocked(PurchaseDayChart).mock.lastCall?.[0]).toMatchObject({ currency: 'USD' })
  })

  it('기본 환율로 계산한 날이 있으면 그 알림을 보인다', () => {
    render(<SimpleResult result={{ ...result, defaultFxDates: ['2013-06-03'] }} symbol="AAPL" purchaseDate="2013-06-03" />)

    expect(screen.getByRole('note')).toHaveTextContent('2013-06-03')
  })

  it('평가일 기준 라벨을 쓴다', async () => {
    render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)
    await open('상세')

    expect(screen.getAllByText('평가 가치').length).toBeGreaterThan(0)
    expect(screen.getByText('평가일 가격')).toBeInTheDocument()
    expect(screen.getByText('평가일 환율')).toBeInTheDocument()
    expect(screen.queryByText('현재 가격')).toBeNull()
  })

  it('평가 가치의 현금은 총자산에서 주식을 뺀 값이라 받은 배당이 들어간다', async () => {
    render(<SimpleResult result={{ ...result, remainingCashKrw: 0 }} symbol="AAPL" purchaseDate="2023-01-03" />)
    await open('상세')

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

  it('보유 주식수는 소수 둘째 자리까지 보여준다', async () => {
    render(<SimpleResult result={{ ...result, shares: 3.141592 }} symbol="AAPL" purchaseDate="2023-01-03" />)
    await open('상세')

    expect(screen.getByText('3.14 주')).toBeInTheDocument()
  })
})

describe('SimpleResult 값이 없을 때', () => {
  it('환율 변동이 비어 있으면 회색 - 로 보여준다', async () => {
    render(<SimpleResult result={{ ...result, fxReturn: undefined }} symbol="AAPL" purchaseDate="2023-01-03" />)
    await open('상세')

    const row = screen.getByText('환율 변동').parentElement
    expect(row).toHaveTextContent('환율 변동-')
    expect(row?.lastElementChild).toHaveClass('text-tx-3')
  })
})
