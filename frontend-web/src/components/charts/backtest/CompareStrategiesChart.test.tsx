import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import { CompareStrategiesChart } from './CompareStrategiesChart'
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
const candle = (date: string, close: number) => ({ symbol: 'AAPL', date, closePrice: close, adjustedClose: close - 1 })

// 단순 매수는 일요일 03-01 매수(마커는 다음 거래일 03-02)
const strategies = [
  {
    name: 'SIMPLE',
    totalInvested: 280000,
    additionalData: { symbol: 'AAPL', purchaseDate: '2026-03-01', currentDate: '2026-03-04', shares: 2, investmentAmount: 280000, purchaseFxRate: 1400 },
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

// 주가 하나에 전략마다 색을 달리한 매수 점
describe('CompareStrategiesChart', () => {
  beforeEach(() => {
    getOHLCData.mockReset()
    getOHLCData.mockResolvedValue({ data: [candle('2026-03-02', 100), candle('2026-03-03', 90), candle('2026-03-04', 110)] } as never)
    getExchangeRatesBulk.mockReset()
  })

  it('첫 전략 기간으로 주가만 받고 전략별 매수 점을 그 전략 색으로 찍는다', async () => {
    renderWithQuery(<CompareStrategiesChart strategies={strategies} strategyNames={{ SIMPLE: '단순 매수', DCA: '적립식' }} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2026-03-01', '2026-03-04')
    expect(getExchangeRatesBulk).not.toHaveBeenCalled()
    const [price] = readCharts()
    expect(price.lines).toEqual(['stockPrice:주가'])
    expect(price.dots).toEqual([
      { x: '2026-03-02', y: 100, fill: '#3b82f6' },
      { x: '2026-03-02', y: 100, fill: '#ef4444' },
      { x: '2026-03-04', y: 110, fill: '#ef4444' },
    ])
    expect(screen.getByText('단순 매수').previousElementSibling).toHaveStyle({ background: '#3b82f6' })
    expect(screen.getByText('적립식').previousElementSibling).toHaveStyle({ background: '#ef4444' })
  })

  it('배당 재투자 거래에는 점을 찍지 않는다', async () => {
    const withDividend = [
      strategies[0],
      {
        ...strategies[1],
        additionalData: {
          ...strategies[1].additionalData,
          transactions: [
            ...(strategies[1].additionalData.transactions ?? []),
            { date: '2026-03-03', price: 90, shares: 0.1, amount: 0, fxRate: 1410, trigger: '배당 재투자' },
          ],
        },
      },
    ]
    renderWithQuery(<CompareStrategiesChart strategies={withDividend} strategyNames={{}} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    expect(readCharts()[0].dots.map((d: { x: string }) => d.x)).toEqual(['2026-03-02', '2026-03-02', '2026-03-04'])
  })

  it('매도 거래에는 매수 점을 찍지 않는다', async () => {
    const withSale = [
      strategies[0],
      {
        ...strategies[1],
        additionalData: {
          ...strategies[1].additionalData,
          transactions: [
            ...(strategies[1].additionalData.transactions ?? []),
            { date: '2026-03-03', price: 90, shares: -1, amount: 0, fxRate: 1410, trigger: '10.0%익절' },
          ],
        },
      },
    ]
    renderWithQuery(<CompareStrategiesChart strategies={withSale} strategyNames={{}} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    expect(readCharts()[0].dots.map((d: { x: string }) => d.x)).toEqual(['2026-03-02', '2026-03-02', '2026-03-04'])
  })

  it('양도세 납부 거래에는 매수 점을 찍지 않는다', async () => {
    const withPayment = [
      strategies[0],
      {
        ...strategies[1],
        additionalData: {
          ...strategies[1].additionalData,
          transactions: [
            ...(strategies[1].additionalData.transactions ?? []),
            { date: '2026-03-03', price: 0, shares: 0, amount: 0, fxRate: 1410, trigger: '양도세 납부', taxPaid: 12.5 },
          ],
        },
      },
    ]
    renderWithQuery(<CompareStrategiesChart strategies={withPayment} strategyNames={{}} />)

    await waitFor(() => expect(screen.getAllByTestId('line-chart')).toHaveLength(1))
    expect(readCharts()[0].dots.map((d: { x: string }) => d.x)).toEqual(['2026-03-02', '2026-03-02', '2026-03-04'])
  })
})
