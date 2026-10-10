import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SimpleChart } from './SimpleChart'
import { stockApi } from '../../../services/api'
import { readCharts } from '../../../test/readCharts'

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
        shares={1}
        investmentAmount={300000}
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

// 매수는 보라, 배당 재투자는 녹색, 최적 시점은 금색 점
describe('SimpleChart 차트', () => {
  it('가격 · 평가금액 데이터와 매수 · 최적 · 배당 재투자 마커', async () => {
    // 날짜가 배열로 오는 줄과 다른 종목 줄이 섞여 온다. 03-04 환율은 빠져 대체값을 쓴다
    getOHLCData.mockResolvedValue({
      data: [
        { symbol: 'AAPL', date: '2026-03-02', closePrice: 100, adjustedClose: 100 },
        { symbol: 'AAPL', date: [2026, 3, 3], closePrice: 102, adjustedClose: 102 },
        { symbol: 'AAPL', date: '2026-03-04', closePrice: 101, adjustedClose: 101 },
        { symbol: 'AAPL', date: '2026-03-05', closePrice: 105, adjustedClose: 105 },
        { symbol: 'MSFT', date: '2026-03-02', closePrice: 400, adjustedClose: 400 },
      ],
    } as never)
    vi.mocked(stockApi.getExchangeRatesBulk).mockResolvedValue({
      data: { '2026-03-02': 1400, '2026-03-03': 1410, '2026-03-05': 1420 },
    } as never)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <SimpleChart
          symbol="AAPL"
          purchaseDate="2026-03-02"
          shares={2}
          investmentAmount={280000}
          optimalBuyDate="2026-03-04"
          optimalSellDate="2026-03-05"
          dividendReinvestDates={['2026-03-01', '2026-03-04']}
          endDate="2026-03-05"
        />
      </QueryClientProvider>
    )

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(2))
    expect(vi.mocked(stockApi.getExchangeRatesBulk)).toHaveBeenCalledWith(['2026-03-02', '2026-03-03', '2026-03-04', '2026-03-05'])
    expect(readCharts()).toMatchInlineSnapshot(`
      [
        {
          "dots": [
            {
              "fill": "#8b5cf6",
              "x": "2026-03-02",
              "y": 100,
            },
            {
              "fill": "#fbbf24",
              "x": "2026-03-04",
              "y": 101,
            },
            {
              "fill": "#10b981",
              "x": "2026-03-02",
              "y": 100,
            },
            {
              "fill": "#10b981",
              "x": "2026-03-04",
              "y": 101,
            },
          ],
          "lines": [
            "price:AAPL 주가",
          ],
          "points": [
            {
              "date": "2026-03-02",
              "price": 100,
              "투자금": 280000,
              "평가금액": 280000,
            },
            {
              "date": "2026-03-03",
              "price": 102,
              "투자금": 280000,
              "평가금액": 287640,
            },
            {
              "date": "2026-03-04",
              "price": 101,
              "투자금": 280000,
              "평가금액": 262600,
            },
            {
              "date": "2026-03-05",
              "price": 105,
              "투자금": 280000,
              "평가금액": 298200,
            },
          ],
          "yDomains": [
            [
              50,
              155,
            ],
          ],
        },
        {
          "dots": [
            {
              "fill": "#8b5cf6",
              "x": "2026-03-02",
              "y": 280000,
            },
            {
              "fill": "#fbbf24",
              "x": "2026-03-05",
              "y": 298200,
            },
            {
              "fill": "#10b981",
              "x": "2026-03-02",
              "y": 280000,
            },
            {
              "fill": "#10b981",
              "x": "2026-03-04",
              "y": 262600,
            },
          ],
          "lines": [
            "투자금:투자금",
            "평가금액:평가금액",
          ],
          "points": [
            {
              "date": "2026-03-02",
              "price": 100,
              "투자금": 280000,
              "평가금액": 280000,
            },
            {
              "date": "2026-03-03",
              "price": 102,
              "투자금": 280000,
              "평가금액": 287640,
            },
            {
              "date": "2026-03-04",
              "price": 101,
              "투자금": 280000,
              "평가금액": 262600,
            },
            {
              "date": "2026-03-05",
              "price": 105,
              "투자금": 280000,
              "평가금액": 298200,
            },
          ],
          "yDomains": [
            null,
          ],
        },
      ]
    `)
  })
})
