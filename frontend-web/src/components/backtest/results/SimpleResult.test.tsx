import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SimpleResult } from './SimpleResult'
import { SimpleChart } from '@components/charts/backtest/SimpleChart'
import { PurchaseDayChart } from './PurchaseDayChart'

vi.mock('@components/charts/backtest/SimpleChart', () => ({ SimpleChart: vi.fn(() => null) }))
vi.mock('./PurchaseDayChart', () => ({ PurchaseDayChart: vi.fn(() => null) }))
vi.mock('recharts', () => import('../../../test/rechartsProbe'))

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

beforeEach(() => vi.clearAllMocks())
const priceTab = () => userEvent.click(screen.getByRole('tab', { name: '주가 추이' }))

// 매수일별 수익률(원화 50 · 60 · 40, 달러 40 · 30 · 20)
const timing = [
  { date: '2023-01-03', returnPercent: 50, returnPercentUsd: 40 },
  { date: '2023-01-04', returnPercent: 60, returnPercentUsd: 30 },
  { date: '2023-01-05', returnPercent: 40, returnPercentUsd: 20 },
]

describe('SimpleResult', () => {
  it('카드 넷과 자산 추이 · 주가 추이 · 매수일별 수익률 탭을 위에 두고 상세는 닫아 두며, 차트는 탭을 고를 때 그린다', () => {
    render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)

    for (const label of ['총 수익률', '연평균 수익률', '최대 낙폭']) expect(screen.getByText(label)).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '자산 추이' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: '주가 추이' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '매수일별 수익률' })).toBeInTheDocument()
    expect(screen.getByText('상세').closest('details')).not.toHaveAttribute('open')
    expect(vi.mocked(SimpleChart)).not.toHaveBeenCalled()
    expect(vi.mocked(PurchaseDayChart)).not.toHaveBeenCalled()
  })

  it('요약 카드에 투자금과 수익률 기준을 보여준다', () => {
    render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)

    expect(screen.getByText('총 수익률').parentElement).toHaveTextContent('총 수익률 · 투자금 기준')
    expect(screen.getByText('총 수익률').parentElement).toHaveTextContent('초기 투자금 ₩300,000 대비 +₩150,000')
  })

  it('E2E 가 읽는 수익률 칸을 그대로 둔다', () => {
    render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)

    expect(screen.getByTestId('backtest-return-rate')).toBeInTheDocument()
  })

  it('주가 추이 탭을 고르면 차트를 평가일까지 그리게 넘긴다', async () => {
    render(<SimpleResult result={{ ...result, currentDate: '2024-06-14' }} symbol="AAPL" purchaseDate="2023-01-03" />)

    await priceTab()

    expect(vi.mocked(SimpleChart).mock.lastCall?.[0]).toMatchObject({ endDate: '2024-06-14' })
  })

  it('매수일별 수익률 탭을 고르면 결과의 매수일별 수익률을 고른 통화 · 매수일 · 평가일과 넘긴다', async () => {
    render(
      <SimpleResult
        result={{ ...result, currentDate: '2024-06-14', purchaseDayReturns: timing, metricsKrw: {}, metricsUsd: {} }}
        symbol="AAPL"
        purchaseDate="2023-01-03"
      />,
    )

    await userEvent.click(screen.getByRole('tab', { name: '매수일별 수익률' }))
    expect(vi.mocked(PurchaseDayChart).mock.lastCall?.[0]).toEqual({
      returns: timing,
      currency: 'KRW',
      purchaseDate: '2023-01-03',
      valuationDate: '2024-06-14',
    })

    await userEvent.click(screen.getByRole('button', { name: '달러 기준' }))
    expect(vi.mocked(PurchaseDayChart).mock.lastCall?.[0]).toMatchObject({ currency: 'USD' })
  })

  it('매수 타이밍 카드에 매수일부터 평가일 전까지 거래일 가운데 이 매수일의 순위를 고른 통화로 보인다', async () => {
    render(
      <SimpleResult
        result={{ ...result, purchaseDayReturns: timing, metricsKrw: {}, metricsUsd: {} }}
        symbol="AAPL"
        purchaseDate="2023-01-03"
      />,
    )

    expect(screen.getByText('매수 타이밍').parentElement).toHaveTextContent('상위 66.67%매수일부터 평가일 전까지 거래일 3일 중 2번째')

    await userEvent.click(screen.getByRole('button', { name: '달러 기준' }))
    expect(screen.getByText('매수 타이밍').parentElement).toHaveTextContent('상위 33.33%')
  })

  it('매수일별 수익률이 없거나 하루뿐이면 매수 타이밍 카드도 없다', () => {
    const { unmount } = render(<SimpleResult result={result} symbol="AAPL" purchaseDate="2023-01-03" />)
    expect(screen.queryByText('매수 타이밍')).toBeNull()
    unmount()

    render(<SimpleResult result={{ ...result, purchaseDayReturns: timing.slice(0, 1) }} symbol="AAPL" purchaseDate="2023-01-03" />)
    expect(screen.queryByText('매수 타이밍')).toBeNull()
  })

  it('주가 차트 툴팁에 보유 수익률과 여기서 샀다면 평가일까지 수익률을 덧붙인다', async () => {
    const dailyValues = [
      { date: '2023-01-03', valueUsd: 230, valueKrw: 300000 },
      { date: '2023-01-04', valueUsd: 250, valueKrw: 330000 },
    ]
    render(
      <SimpleResult result={{ ...result, dailyValues, purchaseDayReturns: timing }} symbol="AAPL" purchaseDate="2023-01-03" />,
    )
    await priceTab()

    const extra = vi.mocked(SimpleChart).mock.lastCall?.[0].tooltipExtra
    const { container } = render(<>{extra?.('2023-01-04')}</>)
    expect(container).toHaveTextContent('보유 수익률: +10.00%')
    expect(container).toHaveTextContent('여기서 샀다면 평가일까지: +60.00%')

    const { container: empty } = render(<>{extra?.('2023-02-01')}</>)
    expect(empty).toBeEmptyDOMElement()
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
