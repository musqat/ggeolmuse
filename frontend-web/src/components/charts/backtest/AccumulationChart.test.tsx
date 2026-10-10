import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { AccumulationChart } from './AccumulationChart'
import { stockApi } from '../../../services/api'
import { readCharts } from '../../../test/readCharts'

vi.mock('recharts', () => import('../../../test/rechartsProbe'))
vi.mock('../../../services/api', () => ({
  stockApi: { getOHLCData: vi.fn(), getExchangeRatesBulk: vi.fn() },
}))

const getOHLCData = vi.mocked(stockApi.getOHLCData)
const getExchangeRatesBulk = vi.mocked(stockApi.getExchangeRatesBulk)

const candle = (symbol: string, date: string, close: number) => ({ symbol, date, closePrice: close, adjustedClose: close })

// 2026-03-01 은 일요일. 매수 마커는 다음 거래일 03-02 에 찍힌다
const transactions = [
  { date: '2026-03-01', actualDate: '2026-03-01', price: 100, shares: 1, amount: 140000, fxRate: 1400 },
  { date: '2026-03-04', price: 101, shares: 1, amount: 141000, fxRate: 1396 },
  { date: '2026-03-05', price: 105, shares: 0.1, amount: 14900, fxRate: 1420, trigger: '배당 재투자' },
]

const props = { symbol: 'AAPL', transactions, currentValueKrw: 300000, totalInvested: 295900, startDate: '2026-03-01' }

// 일반 매수는 보라, 배당 재투자는 녹색 점. 한 거래일에 한 번씩만 찍는다
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
    // 03-04 · 03-06 은 빠져 대체값을 쓴다
    getExchangeRatesBulk.mockResolvedValue({ data: { '2026-03-02': 1400, '2026-03-03': 1410, '2026-03-05': 1420 } } as never)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('첫 매수일부터 오늘까지 받고, 그 종목 날짜로만 환율을 묻는다', async () => {
    render(<AccumulationChart {...props} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(2))
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2026-03-01', '2026-03-10')
    expect(getExchangeRatesBulk).toHaveBeenCalledWith(['2026-03-02', '2026-03-03', '2026-03-04', '2026-03-05', '2026-03-06'])
  })

  it('주가 · 포트폴리오 차트의 데이터와 마커', async () => {
    render(<AccumulationChart {...props} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(2))
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
              "x": "2026-03-04",
              "y": 101,
            },
            {
              "fill": "#10b981",
              "x": "2026-03-05",
              "y": 105,
            },
          ],
          "lines": [
            "stockPrice:주가",
          ],
          "points": [
            {
              "date": "2026-03-02",
              "investedAmount": 140000,
              "isDividendReinvest": false,
              "isPurchase": true,
              "portfolioValue": 140000,
              "stockPrice": 100,
            },
            {
              "date": "2026-03-03",
              "investedAmount": 140000,
              "isDividendReinvest": false,
              "isPurchase": false,
              "portfolioValue": 143820,
              "stockPrice": 102,
            },
            {
              "date": "2026-03-04",
              "investedAmount": 281000,
              "isDividendReinvest": false,
              "isPurchase": true,
              "portfolioValue": 262600,
              "stockPrice": 101,
            },
            {
              "date": "2026-03-05",
              "investedAmount": 295900,
              "isDividendReinvest": true,
              "isPurchase": false,
              "portfolioValue": 313110,
              "stockPrice": 105,
            },
            {
              "date": "2026-03-06",
              "investedAmount": 295900,
              "isDividendReinvest": false,
              "isPurchase": false,
              "portfolioValue": 283920,
              "stockPrice": 104,
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
              "y": 140000,
            },
            {
              "fill": "#8b5cf6",
              "x": "2026-03-04",
              "y": 262600,
            },
            {
              "fill": "#10b981",
              "x": "2026-03-05",
              "y": 313110,
            },
          ],
          "lines": [
            "investedAmount:누적 투자금",
            "portfolioValue:포트폴리오 가치",
          ],
          "points": [
            {
              "date": "2026-03-02",
              "investedAmount": 140000,
              "isDividendReinvest": false,
              "isPurchase": true,
              "portfolioValue": 140000,
              "stockPrice": 100,
            },
            {
              "date": "2026-03-03",
              "investedAmount": 140000,
              "isDividendReinvest": false,
              "isPurchase": false,
              "portfolioValue": 143820,
              "stockPrice": 102,
            },
            {
              "date": "2026-03-04",
              "investedAmount": 281000,
              "isDividendReinvest": false,
              "isPurchase": true,
              "portfolioValue": 262600,
              "stockPrice": 101,
            },
            {
              "date": "2026-03-05",
              "investedAmount": 295900,
              "isDividendReinvest": true,
              "isPurchase": false,
              "portfolioValue": 313110,
              "stockPrice": 105,
            },
            {
              "date": "2026-03-06",
              "investedAmount": 295900,
              "isDividendReinvest": false,
              "isPurchase": false,
              "portfolioValue": 283920,
              "stockPrice": 104,
            },
          ],
          "yDomains": [
            [
              122689,
              330421,
            ],
          ],
        },
      ]
    `)
    expect(screen.getByText(/보라 점: 매수/)).toHaveTextContent('보라 점: 매수 (2개)')
    expect(screen.getByText(/녹색 점: 배당 재투자/)).toHaveTextContent('녹색 점: 배당 재투자 (1개)')
  })

  it('평가일이 있으면 그날까지 받는다', async () => {
    render(<AccumulationChart {...props} endDate="2026-03-06" />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(2))
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2026-03-01', '2026-03-06')
  })

  // 03-07 은 토요일. 배당 재투자 점은 다음 거래일 03-09 에 찍힌다
  it('주말 배당 재투자도 다음 거래일에 녹색 점으로 찍는다', async () => {
    getOHLCData.mockResolvedValue({ data: [candle('AAPL', '2026-03-06', 100), candle('AAPL', '2026-03-09', 110)] } as never)
    const weekendDividend = [
      { date: '2026-03-06', price: 100, shares: 1, amount: 140000, fxRate: 1400 },
      { date: '2026-03-07', price: 100, shares: 0.1, amount: 14000, fxRate: 1400, trigger: '배당 재투자' },
    ]
    render(<AccumulationChart {...props} transactions={weekendDividend} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(2))
    const [price, portfolio] = readCharts()
    expect(price.dots).toEqual([
      { x: '2026-03-06', y: 100, fill: '#8b5cf6' },
      { x: '2026-03-09', y: 110, fill: '#10b981' },
    ])
    expect(portfolio.dots.map((dot: { fill: string }) => dot.fill)).toEqual(['#8b5cf6', '#10b981'])
  })
})
