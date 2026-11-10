import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import { CompareSymbolsChart } from './CompareSymbolsChart'
import { stockApi } from '../../../services/api'
import { readCharts, readTooltips, readXAxes } from '../../../test/readCharts'

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

const PRICES: Record<string, ReturnType<typeof candle>[]> = {
  AAPL: [candle('AAPL', '2026-03-02', 100), candle('AAPL', '2026-03-03', 90), candle('AAPL', '2026-03-04', 110)],
  MSFT: [candle('MSFT', '2026-03-02', 400), candle('MSFT', '2026-03-03', 410), candle('MSFT', '2026-03-04', 380)],
}

const symbols = [
  { symbol: 'AAPL', purchaseDate: '2026-03-02', color: '#3b82f6', optimalBuyDate: '2026-03-03', optimalSellDate: '2026-03-04' },
  // 최적 시점이 없으면 금색 점 없음
  { symbol: 'MSFT', purchaseDate: '2026-03-03', color: '#ef4444', optimalBuyDate: null, optimalSellDate: null },
]

// 종목마다 같은 기간 가격으로 그린 주가 · 매수 · 최적 시점
describe('CompareSymbolsChart 차트', () => {
  beforeEach(() => {
    getOHLCData.mockReset()
    getExchangeRatesBulk.mockReset()
    getOHLCData.mockImplementation(async (symbol: string) => ({ data: PRICES[symbol] }) as never)
  })

  it('종목마다 같은 기간으로 받아 한 차트에 종가로 그리고, 최적 매수 · 매도 점은 그 종목 주가 위에 찍는다', async () => {
    renderWithQuery(<CompareSymbolsChart symbols={symbols} startDate="2026-03-02" endDate="2026-03-04" />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2026-03-02', '2026-03-04')
    expect(getOHLCData).toHaveBeenCalledWith('MSFT', '2026-03-02', '2026-03-04')
    expect(getExchangeRatesBulk).not.toHaveBeenCalled()
    const [price] = readCharts()
    expect(price.lines).toEqual(['AAPL_price:AAPL 주가', 'MSFT_price:MSFT 주가'])
    expect(price.points).toEqual([
      { date: '2026-03-02', AAPL_price: 100, MSFT_price: 400 },
      { date: '2026-03-03', AAPL_price: 90, MSFT_price: 410 },
      { date: '2026-03-04', AAPL_price: 110, MSFT_price: 380 },
    ])
    expect(price.dots).toEqual([
      { x: '2026-03-02', y: 100, fill: '#8b5cf6' },
      { x: '2026-03-03', y: 410, fill: '#8b5cf6' },
      { x: '2026-03-03', y: 90, fill: '#fbbf24' },
      { x: '2026-03-04', y: 110, fill: '#fbbf24' },
    ])
    expect(price.yDomains).toEqual([[74, 426]])
  })

  it('툴팁은 주가를 천 단위 쉼표 · 달러 소수 둘째 자리로 보여준다', async () => {
    renderWithQuery(<CompareSymbolsChart symbols={symbols} startDate="2026-03-02" endDate="2026-03-04" />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    expect(readTooltips()).toEqual(['$1,234,567.89'])
  })

  it('기간이 1년을 넘으면 날짜 축을 달마다 연.월 눈금으로 바꾼다', async () => {
    getOHLCData.mockResolvedValue({
      data: [candle('AAPL', '2023-01-03', 125), candle('AAPL', '2023-01-31', 144), candle('AAPL', '2024-03-04', 175)],
    } as never)
    renderWithQuery(<CompareSymbolsChart symbols={[symbols[0]]} startDate="2023-01-03" endDate="2024-03-04" />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    expect(readXAxes()).toEqual([{ ticks: ['2023-01-03', '2024-03-04'], first: '23.1' }])
  })
})
