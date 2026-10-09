import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { CompareStrategiesChart } from './CompareStrategiesChart'
import { stockApi } from '../../../services/api'
import { readCharts } from '../../../test/readCharts'

vi.mock('recharts', () => import('../../../test/rechartsProbe'))
vi.mock('../../../services/api', () => ({
  stockApi: { getOHLCData: vi.fn(), getExchangeRatesBulk: vi.fn() },
}))

const getOHLCData = vi.mocked(stockApi.getOHLCData)

const candle = (date: string, close: number) => ({ symbol: 'AAPL', date, closePrice: close, adjustedClose: close })

// 단순 매수는 일요일(03-01)에 사서 다음 거래일 03-02 에 마커가 찍힌다
const strategies = [
  {
    name: 'SIMPLE',
    totalInvested: 280000,
    additionalData: { symbol: 'AAPL', purchaseDate: '2026-03-01', endDate: '2026-03-04', shares: 2, investmentAmount: 280000, purchaseFxRate: 1400 },
  },
  {
    name: 'DCA',
    totalInvested: 281000,
    additionalData: {
      symbol: 'AAPL',
      startDate: '2026-03-01',
      endDate: '2026-03-04',
      transactions: [
        { date: '2026-03-02', price: 100, shares: 1, amount: 140000, fxRate: 1400 },
        { date: '2026-03-04', price: 110, shares: 1, amount: 141000, fxRate: 1410 },
      ],
    },
  },
]

// 지금 동작을 그대로 적어 둔다. 공통 부분을 뺄 때 이 결과가 바뀌면 안 된다
describe('CompareStrategiesChart 지금 동작', () => {
  beforeEach(() => {
    getOHLCData.mockReset()
    getOHLCData.mockResolvedValue({ data: [candle('2026-03-02', 100), candle('2026-03-03', 90), candle('2026-03-04', 110)] } as never)
  })

  it('첫 전략 기간으로 받고, 전략별 평가 · 투자금과 매수 마커를 낸다', async () => {
    render(<CompareStrategiesChart strategies={strategies} strategyNames={{ SIMPLE: '단순 매수', DCA: '적립식' }} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(2))
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2026-03-01', '2026-03-04')
    expect(readCharts()).toMatchInlineSnapshot(`
      [
        {
          "dots": [
            {
              "fill": "#3b82f6",
              "x": "2026-03-02",
              "y": 100,
            },
            {
              "fill": "#ef4444",
              "x": "2026-03-02",
              "y": 100,
            },
            {
              "fill": "#ef4444",
              "x": "2026-03-04",
              "y": 110,
            },
          ],
          "lines": [
            "stockPrice:주가",
          ],
          "points": [
            {
              "date": "2026-03-02",
              "stockPrice": 100,
            },
            {
              "date": "2026-03-03",
              "stockPrice": 90,
            },
            {
              "date": "2026-03-04",
              "stockPrice": 110,
            },
          ],
          "yDomains": [
            null,
          ],
        },
        {
          "dots": [
            {
              "fill": "#3b82f6",
              "x": "2026-03-02",
              "y": 280000,
            },
            {
              "fill": "#ef4444",
              "x": "2026-03-02",
              "y": 140500,
            },
            {
              "fill": "#ef4444",
              "x": "2026-03-04",
              "y": 309100,
            },
          ],
          "lines": [
            "SIMPLE_invested:SIMPLE_invested",
            "DCA_invested:DCA_invested",
            "SIMPLE_portfolio:SIMPLE_portfolio",
            "DCA_portfolio:DCA_portfolio",
          ],
          "points": [
            {
              "DCA_invested": 140000,
              "DCA_portfolio": 140500,
              "SIMPLE_invested": 280000,
              "SIMPLE_portfolio": 280000,
              "date": "2026-03-02",
            },
            {
              "DCA_invested": 140000,
              "DCA_portfolio": 126450,
              "SIMPLE_invested": 280000,
              "SIMPLE_portfolio": 252000,
              "date": "2026-03-03",
            },
            {
              "DCA_invested": 281000,
              "DCA_portfolio": 309100,
              "SIMPLE_invested": 280000,
              "SIMPLE_portfolio": 308000,
              "date": "2026-03-04",
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
