import { describe, it, expect, vi, beforeEach } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { stockApi } from '../services/api'
import { useTradingChart } from './useTradingChart'

vi.mock('../services/api', () => ({
  stockApi: { getOHLCData: vi.fn() },
}))

const bar = (date: string, close: number) => ({
  symbol: 'AAPL', date, openPrice: close, highPrice: close, lowPrice: close, closePrice: close, adjustedClose: close, volume: 1,
})
// 차트 기간 10-01 ~ 10-12 의 거래일(10-07 은 봉 없는 평일, 10-10 · 10-11 은 주말)
const CHART = [bar('2026-10-06', 100), bar('2026-10-08', 105), bar('2026-10-09', 108)]

const wrapper = ({ children }: { children: ReactNode }) =>
  createElement(QueryClientProvider, { client: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }, children)

const renderChart = () =>
  renderHook(() => useTradingChart({ symbol: 'AAPL', timeframe: '직접설정', customStartDate: '2026-10-01', customEndDate: '2026-10-12' }), { wrapper })

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(stockApi.getOHLCData).mockImplementation(async (_symbol: string, start?: string, end?: string) => {
    if (start === '2026-06-01' && end === '2026-06-01') return { data: [bar('2026-06-01', 50)] } as never
    if (start === end) return { data: [] } as never
    return { data: CHART } as never
  })
})

describe('useTradingChart 거래일', () => {
  it('차트 기간 밖 날짜를 고르면 그날 봉을 따로 받아 그날 시세로 보인다', async () => {
    const { result } = renderChart()
    await waitFor(() => expect(result.current.tradeDate).toBe('2026-10-09'))

    await act(async () => { await result.current.selectTradeDate('2026-06-01') })

    expect(stockApi.getOHLCData).toHaveBeenCalledWith('AAPL', '2026-06-01', '2026-06-01')
    expect(result.current.tradeDate).toBe('2026-06-01')
    expect(result.current.selectedDateOHLC?.close).toBe(50)
    expect(result.current.tradeDateNotice).toBeNull()
  })

  it.each([
    ['주말', '2026-10-10'],
    ['차트 기간 안의 봉 없는 평일', '2026-10-07'],
    ['차트 기간 밖의 봉 없는 날', '2026-06-02'],
  ])('%s: 고르지 않고 휴장일이라고 알린다', async (_case, date) => {
    const { result } = renderChart()
    await waitFor(() => expect(result.current.tradeDate).toBe('2026-10-09'))

    await act(async () => { await result.current.selectTradeDate(date) })

    expect(result.current.tradeDate).toBe('2026-10-09')
    expect(result.current.selectedDateOHLC?.close).toBe(108)
    expect(result.current.tradeDateNotice).toBe(`${date} 은 시세가 없는 날(휴장일)이라 고를 수 없습니다`)
  })

  it('거래일을 다시 고르면 알림을 지운다', async () => {
    const { result } = renderChart()
    await waitFor(() => expect(result.current.tradeDate).toBe('2026-10-09'))
    await act(async () => { await result.current.selectTradeDate('2026-10-10') })

    await act(async () => { await result.current.selectTradeDate('2026-10-08') })

    expect(result.current.tradeDate).toBe('2026-10-08')
    expect(result.current.selectedDateOHLC?.close).toBe(105)
    expect(result.current.tradeDateNotice).toBeNull()
  })
})
