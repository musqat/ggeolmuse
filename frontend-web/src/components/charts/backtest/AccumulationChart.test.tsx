import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import { AccumulationChart } from './AccumulationChart'
import { stockApi } from '../../../services/api'
import { readCharts } from '../../../test/readCharts'

// 테스트마다 새 react-query 클라이언트로 감쌈(차트가 react-query 로 받음)
const renderWithQuery = (ui: ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

vi.mock('recharts', () => import('../../../test/rechartsProbe'))
vi.mock('../../../services/api', () => ({
  stockApi: { getOHLCData: vi.fn(), getExchangeRatesBulk: vi.fn() },
}))

const getOHLCData = vi.mocked(stockApi.getOHLCData)
const getExchangeRatesBulk = vi.mocked(stockApi.getExchangeRatesBulk)

// 수정 종가는 배당만큼 낮게, 차트가 그리는 값은 종가
const candle = (symbol: string, date: string, close: number) => ({ symbol, date, closePrice: close, adjustedClose: close - 1 })

// 2026-03-01 은 일요일(매수 마커는 다음 거래일 03-02)
const transactions = [
  { date: '2026-03-01', actualDate: '2026-03-01', price: 100, shares: 1, amount: 140000, fxRate: 1400 },
  { date: '2026-03-04', price: 101, shares: 1, amount: 141000, fxRate: 1396 },
  { date: '2026-03-05', price: 105, shares: 0.1, amount: 14900, fxRate: 1420, trigger: '배당 재투자' },
]

const props = { symbol: 'AAPL', transactions, startDate: '2026-03-01' }

// 일반 매수는 보라, 배당 재투자는 녹색 점(한 거래일에 한 번씩만)
describe('AccumulationChart', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-03-10T12:00:00+09:00'))
    getOHLCData.mockReset()
    getExchangeRatesBulk.mockReset()
    getOHLCData.mockResolvedValue({
      data: [
        candle('AAPL', '2026-03-02', 100),
        candle('AAPL', '2026-03-03', 102),
        candle('AAPL', '2026-03-04', 101),
        candle('AAPL', '2026-03-05', 105),
        candle('AAPL', '2026-03-06', 104),
        candle('MSFT', '2026-03-02', 400),
      ],
    } as never)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('첫 매수일부터 오늘까지 그 종목 주가만 받고 환율은 묻지 않는다', async () => {
    renderWithQuery(<AccumulationChart {...props} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2026-03-01', '2026-03-10')
    expect(getExchangeRatesBulk).not.toHaveBeenCalled()
  })

  it('주가 차트 하나에 종가와 매수 · 배당 재투자 점을 그린다', async () => {
    renderWithQuery(<AccumulationChart {...props} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    const [price] = readCharts()
    expect(price.lines).toEqual(['stockPrice:주가'])
    expect(price.points.map((p: { date: string; stockPrice: number }) => [p.date, p.stockPrice])).toEqual([
      ['2026-03-02', 100],
      ['2026-03-03', 102],
      ['2026-03-04', 101],
      ['2026-03-05', 105],
      ['2026-03-06', 104],
    ])
    expect(price.dots).toEqual([
      { fill: '#8b5cf6', x: '2026-03-02', y: 100 },
      { fill: '#8b5cf6', x: '2026-03-04', y: 101 },
      { fill: '#10b981', x: '2026-03-05', y: 105 },
    ])
    expect(price.yDomains).toEqual([[50, 155]])
    expect(screen.getByText(/보라 점 = 매수/)).toHaveTextContent('파란 선 = 주가 | 보라 점 = 매수(2개) | 녹색 점 = 배당 재투자(1개)')
  })

  it('매도는 매수 점으로 세지 않고 빨간 점으로 찍는다', async () => {
    const withSale = [
      { date: '2026-03-01', actualDate: '2026-03-01', price: 100, shares: 1, amount: 140000, fxRate: 1400 },
      { date: '2026-03-04', price: 101, shares: -1, amount: 0, fxRate: 1396, trigger: '10.0%익절' },
    ]
    renderWithQuery(<AccumulationChart {...props} transactions={withSale} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    expect(readCharts()[0].dots).toEqual([
      { fill: '#8b5cf6', x: '2026-03-02', y: 100 },
      { fill: '#ef4444', x: '2026-03-04', y: 101 },
    ])
    expect(screen.getByText('1회 매수 • 1회 매도 • 총 0.00주 보유')).toBeInTheDocument()
    expect(screen.getByText(/보라 점 = 매수/)).toHaveTextContent('파란 선 = 주가 | 보라 점 = 매수(1개) | 빨간 점 = 매도(1개)')
  })

  it('전량 매도로 끝나면 부동소수 합이 음수로 남아도 보유를 0.00주로 보인다', async () => {
    const soldOut = [
      { date: '2026-03-02', price: 100, shares: 3.19845656, amount: 140000, fxRate: 1400 },
      { date: '2026-03-03', price: 100, shares: 3.17819174, amount: 140000, fxRate: 1400 },
      { date: '2026-03-05', price: 105, shares: -6.3766483, amount: 0, fxRate: 1400, trigger: '5.0%익절' },
    ]
    renderWithQuery(<AccumulationChart {...props} transactions={soldOut} />)

    expect(await screen.findByText('2회 매수 • 1회 매도 • 총 0.00주 보유')).toBeInTheDocument()
  })

  it('평가일이 있으면 그 날짜까지 받는다', async () => {
    renderWithQuery(<AccumulationChart {...props} endDate="2026-03-06" />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2026-03-01', '2026-03-06')
  })

  // 03-07 은 토요일(배당 재투자 점은 다음 거래일 03-09)
  it('주말 배당 재투자도 다음 거래일에 녹색 점으로 찍는다', async () => {
    getOHLCData.mockResolvedValue({ data: [candle('AAPL', '2026-03-06', 100), candle('AAPL', '2026-03-09', 110)] } as never)
    const weekendDividend = [
      { date: '2026-03-06', price: 100, shares: 1, amount: 140000, fxRate: 1400 },
      { date: '2026-03-07', price: 100, shares: 0.1, amount: 0, fxRate: 1400, trigger: '배당 재투자' },
    ]
    renderWithQuery(<AccumulationChart {...props} transactions={weekendDividend} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    expect(readCharts()[0].dots).toEqual([
      { x: '2026-03-06', y: 100, fill: '#8b5cf6' },
      { x: '2026-03-09', y: 110, fill: '#10b981' },
    ])
  })

  it('제목 없이 배당 재투자를 뺀 매수 횟수와 보유 주식 합(소수 둘째 자리)을 보인다', async () => {
    renderWithQuery(<AccumulationChart {...props} />)

    expect(await screen.findByText('2회 매수 • 총 2.10주 보유')).toBeInTheDocument()
    expect(screen.queryByRole('heading')).toBeNull()
  })

  it('1년을 고르면 그보다 앞선 거래는 첫날에 찍지 않는다', async () => {
    const older = [
      { date: '2024-05-01', price: 90, shares: 1, amount: 140000, fxRate: 1400 },
      { date: '2026-03-04', price: 101, shares: 1, amount: 141000, fxRate: 1396 },
    ]
    renderWithQuery(<AccumulationChart {...props} transactions={older} startDate="2024-05-01" />)
    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))

    await userEvent.click(screen.getByRole('button', { name: '1년' }))

    await waitFor(() => expect(getOHLCData).toHaveBeenLastCalledWith('AAPL', '2025-03-10', '2026-03-10'))
    expect(readCharts()[0].dots).toEqual([{ x: '2026-03-04', y: 101, fill: '#8b5cf6' }])
  })

  it('매수가 없어도 시작일부터 주가를 받아 0회 매수로 그린다', async () => {
    renderWithQuery(<AccumulationChart {...props} transactions={[]} />)

    expect(await screen.findByText('0회 매수 • 총 0.00주 보유')).toBeInTheDocument()
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2026-03-01', '2026-03-10')
    expect(readCharts()[0].dots).toEqual([])
  })

  it('고른 기간에 시세가 없으면 카드 테두리 없이 안내를 보인다', async () => {
    getOHLCData.mockResolvedValue({ data: [] } as never)
    renderWithQuery(<AccumulationChart {...props} />)

    expect((await screen.findByText('차트 데이터가 없습니다.')).closest('.bg-surface')).toBeNull()
  })
})
