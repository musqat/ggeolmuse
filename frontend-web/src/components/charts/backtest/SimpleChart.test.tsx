import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SimpleChart } from './SimpleChart'
import { stockApi } from '../../../services/api'
import { readCharts, readXAxes } from '../../../test/readCharts'

vi.mock('recharts', () => import('../../../test/rechartsProbe'))
vi.mock('../../../services/api', () => ({
  stockApi: { getOHLCData: vi.fn(), getExchangeRatesBulk: vi.fn() },
}))

const getOHLCData = vi.mocked(stockApi.getOHLCData)

function renderChart(endDate?: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <SimpleChart
        symbol="AAPL"
        purchaseDate="2024-01-15"
        endDate={endDate}
      />
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-09T12:00:00+09:00'))
  getOHLCData.mockReset()
  getOHLCData.mockResolvedValue({ data: [] } as never)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('SimpleChart 가격 구간', () => {
  it('평가일이 있으면 그날까지 가격을 받는다', async () => {
    renderChart('2024-06-14')

    await waitFor(() => expect(getOHLCData).toHaveBeenCalled())
    expect(getOHLCData.mock.calls[0][2]).toBe('2024-06-14')
  })

  it('평가일이 없으면 오늘까지 받는다', async () => {
    renderChart()

    await waitFor(() => expect(getOHLCData).toHaveBeenCalled())
    expect(getOHLCData.mock.calls[0][2]).toBe('2026-10-09')
  })
})

// 매수는 보라, 배당 재투자는 녹색, 최적 시점은 금색 점(모두 주가 위)
describe('SimpleChart 차트', () => {
  it('종가로 그린 주가 하나에 매수 · 배당 재투자 · 최적 매수 · 매도 점을 찍고 환율은 묻지 않는다', async () => {
    // 날짜가 배열로 오는 줄과 다른 종목 줄이 섞임, 수정 종가는 배당만큼 낮게, 그리는 값은 종가
    getOHLCData.mockResolvedValue({
      data: [
        { symbol: 'AAPL', date: '2026-03-02', closePrice: 100, adjustedClose: 99 },
        { symbol: 'AAPL', date: [2026, 3, 3], closePrice: 102, adjustedClose: 101 },
        { symbol: 'AAPL', date: '2026-03-04', closePrice: 101, adjustedClose: 100 },
        { symbol: 'AAPL', date: '2026-03-05', closePrice: 105, adjustedClose: 104 },
        { symbol: 'MSFT', date: '2026-03-02', closePrice: 400, adjustedClose: 399 },
      ],
    } as never)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <SimpleChart
          symbol="AAPL"
          purchaseDate="2026-03-02"
          optimalBuyDate="2026-03-04"
          optimalSellDate="2026-03-05"
          dividendReinvestDates={['2026-03-03', '2026-03-04']}
          endDate="2026-03-05"
        />
      </QueryClientProvider>
    )

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    const [price] = readCharts()
    expect(price.lines).toEqual(['price:AAPL 주가'])
    expect(price.points).toEqual([
      { date: '2026-03-02', price: 100 },
      { date: '2026-03-03', price: 102 },
      { date: '2026-03-04', price: 101 },
      { date: '2026-03-05', price: 105 },
    ])
    expect(price.dots).toEqual([
      { x: '2026-03-02', y: 100, fill: '#8b5cf6' },
      { x: '2026-03-04', y: 101, fill: '#fbbf24' },
      { x: '2026-03-05', y: 105, fill: '#fbbf24' },
      { x: '2026-03-03', y: 102, fill: '#10b981' },
      { x: '2026-03-04', y: 101, fill: '#10b981' },
    ])
    expect(price.yDomains).toEqual([[50, 155]])
    expect(stockApi.getExchangeRatesBulk).not.toHaveBeenCalled()
  })
})

describe('SimpleChart 범위 밖 점', () => {
  it('1년을 고르면 그보다 앞선 매수 · 배당 재투자는 첫날에 찍지 않는다', async () => {
    getOHLCData.mockResolvedValue({
      data: [
        { symbol: 'AAPL', date: '2025-10-10', closePrice: 100, adjustedClose: 99 },
        { symbol: 'AAPL', date: '2026-03-03', closePrice: 102, adjustedClose: 101 },
      ],
    } as never)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <SimpleChart symbol="AAPL" purchaseDate="2024-01-02" dividendReinvestDates={['2024-06-01', '2026-03-03']} />
      </QueryClientProvider>
    )
    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))

    await userEvent.click(screen.getByRole('button', { name: '1년' }))

    await waitFor(() => expect(getOHLCData).toHaveBeenLastCalledWith('AAPL', '2025-10-09', '2026-10-09'))
    expect(readCharts()[0].dots).toEqual([{ x: '2026-03-03', y: 102, fill: '#10b981' }])
  })
})

describe('SimpleChart 날짜 축', () => {
  it('기간이 1년을 넘으면 달마다 연.월 눈금', async () => {
    getOHLCData.mockResolvedValue({
      data: [
        { symbol: 'AAPL', date: '2024-01-16', closePrice: 180, adjustedClose: 179 },
        { symbol: 'AAPL', date: '2025-03-03', closePrice: 240, adjustedClose: 239 },
      ],
    } as never)
    renderChart('2025-03-03')

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    expect(readXAxes()).toEqual([{ ticks: ['2024-01-16', '2025-03-03'], first: '24.1' }])
  })
})
