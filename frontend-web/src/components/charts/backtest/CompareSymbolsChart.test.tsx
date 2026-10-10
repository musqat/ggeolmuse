import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { CompareSymbolsChart } from './CompareSymbolsChart'
import { stockApi } from '../../../services/api'
import { readCharts } from '../../../test/readCharts'

vi.mock('recharts', () => import('../../../test/rechartsProbe'))
vi.mock('../../../services/api', () => ({
  stockApi: { getOHLCData: vi.fn(), getExchangeRatesBulk: vi.fn() },
}))

const getOHLCData = vi.mocked(stockApi.getOHLCData)
const getExchangeRatesBulk = vi.mocked(stockApi.getExchangeRatesBulk)

const candle = (symbol: string, date: string, close: number) => ({ symbol, date, closePrice: close, adjustedClose: close })

const PRICES: Record<string, ReturnType<typeof candle>[]> = {
  AAPL: [candle('AAPL', '2026-03-02', 100), candle('AAPL', '2026-03-03', 90), candle('AAPL', '2026-03-04', 110)],
  // MSFT 는 03-03 에 샀다. 그 전 날은 평가 0
  MSFT: [candle('MSFT', '2026-03-02', 400), candle('MSFT', '2026-03-03', 410), candle('MSFT', '2026-03-04', 380)],
}

const symbols = [
  { symbol: 'AAPL', purchaseDate: '2026-03-02', purchasePrice: 100, shares: 2, investmentAmount: 280000, currentPrice: 110, currentValueKrw: 0, color: '#3b82f6' },
  { symbol: 'MSFT', purchaseDate: '2026-03-03', purchasePrice: 410, shares: 0.5, investmentAmount: 280000, currentPrice: 380, currentValueKrw: 0, color: '#ef4444' },
]

// 종목마다 같은 기간 가격을 받아 주가 · 평가금액 · 매수 · 최적 시점을 그린다
describe('CompareSymbolsChart 차트', () => {
  beforeEach(() => {
    getOHLCData.mockReset()
    getExchangeRatesBulk.mockReset()
    getOHLCData.mockImplementation(async (symbol: string) => ({ data: PRICES[symbol] }) as never)
    // 03-04 환율은 빠져 대체값을 쓴다
    getExchangeRatesBulk.mockResolvedValue({ data: { '2026-03-02': 1400, '2026-03-03': 1410 } } as never)
  })

  it('종목마다 같은 기간으로 받고, 데이터 · 마커 · 범위 · 최적 시점을 낸다', async () => {
    const onOptimal = vi.fn()
    render(<CompareSymbolsChart symbols={symbols} startDate="2026-03-02" endDate="2026-03-04" onOptimalPointsCalculated={onOptimal} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(2))
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2026-03-02', '2026-03-04')
    expect(getOHLCData).toHaveBeenCalledWith('MSFT', '2026-03-02', '2026-03-04')
    expect(onOptimal.mock.calls.at(-1)![0]).toMatchInlineSnapshot(`
      {
        "AAPL": {
          "buyDate": "2026-03-03",
          "maxValue": 286000,
          "minPrice": 90,
          "sellDate": "2026-03-04",
        },
        "MSFT": {
          "buyDate": "2026-03-02",
          "maxValue": 289050,
          "minPrice": 400,
          "sellDate": "2026-03-03",
        },
      }
    `)
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
              "fill": "#8b5cf6",
              "x": "2026-03-03",
              "y": 410,
            },
            {
              "fill": "#fbbf24",
              "x": "2026-03-03",
              "y": 90,
            },
            {
              "fill": "#fbbf24",
              "x": "2026-03-02",
              "y": 400,
            },
          ],
          "lines": [
            "AAPL_price:AAPL 주가",
            "MSFT_price:MSFT 주가",
          ],
          "points": [
            {
              "AAPL_portfolio": 280000,
              "AAPL_price": 100,
              "AAPL_purchasePrice": 100,
              "MSFT_portfolio": 0,
              "MSFT_price": 400,
              "date": "2026-03-02",
              "totalInvestment": 280000,
              "totalPortfolio": 280000,
            },
            {
              "AAPL_portfolio": 253800,
              "AAPL_price": 90,
              "MSFT_portfolio": 289050,
              "MSFT_price": 410,
              "MSFT_purchasePrice": 410,
              "date": "2026-03-03",
              "totalInvestment": 280000,
              "totalPortfolio": 542850,
            },
            {
              "AAPL_portfolio": 286000,
              "AAPL_price": 110,
              "MSFT_portfolio": 247000,
              "MSFT_price": 380,
              "date": "2026-03-04",
              "totalInvestment": 280000,
              "totalPortfolio": 533000,
            },
          ],
          "yDomains": [
            [
              74,
              426,
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
              "fill": "#8b5cf6",
              "x": "2026-03-03",
              "y": 289050,
            },
            {
              "fill": "#fbbf24",
              "x": "2026-03-04",
              "y": 286000,
            },
            {
              "fill": "#fbbf24",
              "x": "2026-03-03",
              "y": 289050,
            },
          ],
          "lines": [
            "totalInvestment:투자금",
            "AAPL_portfolio:AAPL 평가금",
            "MSFT_portfolio:MSFT 평가금",
          ],
          "points": [
            {
              "AAPL_portfolio": 280000,
              "AAPL_price": 100,
              "AAPL_purchasePrice": 100,
              "MSFT_portfolio": 0,
              "MSFT_price": 400,
              "date": "2026-03-02",
              "totalInvestment": 280000,
              "totalPortfolio": 280000,
            },
            {
              "AAPL_portfolio": 253800,
              "AAPL_price": 90,
              "MSFT_portfolio": 289050,
              "MSFT_price": 410,
              "MSFT_purchasePrice": 410,
              "date": "2026-03-03",
              "totalInvestment": 280000,
              "totalPortfolio": 542850,
            },
            {
              "AAPL_portfolio": 286000,
              "AAPL_price": 110,
              "MSFT_portfolio": 247000,
              "MSFT_price": 380,
              "date": "2026-03-04",
              "totalInvestment": 280000,
              "totalPortfolio": 533000,
            },
          ],
          "yDomains": [
            [
              244897,
              291153,
            ],
          ],
        },
      ]
    `)
  })
})
