import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createElement, type ReactNode } from 'react'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { stockApi } from '../../../services/api'
import { estimateRoundCost, fetchStartQuote, parseShares, useAmountUnit, useRoundCost } from './amountUnit'
import type { BacktestOptions } from './backtestOptions'

vi.mock('../../../services/api', () => ({
  stockApi: { getOHLCData: vi.fn(), getExchangeRatesBulk: vi.fn() },
}))

const getOHLCData = vi.mocked(stockApi.getOHLCData)
const getExchangeRatesBulk = vi.mocked(stockApi.getExchangeRatesBulk)

const candle = (symbol: string, date: string, closePrice: number) => ({ symbol, date, closePrice, adjustedClose: closePrice })

beforeEach(() => {
  getOHLCData.mockReset()
  getExchangeRatesBulk.mockReset()
})

describe('useAmountUnit', () => {
  it('금액 단위 · 주식 수 1 에서 시작하고 고른 단위 · 주식 수를 남긴다', () => {
    const { result } = renderHook(() => useAmountUnit())
    expect(result.current).toMatchObject({ unit: 'AMOUNT', shares: '1' })

    act(() => {
      result.current.setUnit('SHARES')
      result.current.setShares('3')
    })

    expect(result.current).toMatchObject({ unit: 'SHARES', shares: '3' })
  })
})

describe('parseShares', () => {
  it('1 ~ 1,000,000 정수를 주식 수로 읽는다', () => {
    expect(parseShares('1')).toBe(1)
    expect(parseShares('12')).toBe(12)
    expect(parseShares('1000000')).toBe(1000000)
  })

  it.each(['0', '-1', '1.5', '', ' ', 'abc', '1000001', '99999999999999999999'])('%o 는 주식 수가 아니다', (shares) => {
    expect(parseShares(shares)).toBeUndefined()
  })
})

describe('fetchStartQuote', () => {
  it('시작일부터 2주 안의 첫 거래일 종가와 그 날짜 환율을 받는다', async () => {
    getOHLCData.mockResolvedValue({
      data: [candle('MSFT', '2023-01-03', 240), candle('AAPL', '2023-01-04', 126), candle('AAPL', '2023-01-03', 125)],
    } as never)
    getExchangeRatesBulk.mockResolvedValue({ data: { '2023-01-03': 1270 } } as never)

    await expect(fetchStartQuote('AAPL', '2023-01-01')).resolves.toEqual({ date: '2023-01-03', close: 125, fxRate: 1270, defaultFx: false })
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2023-01-01', '2023-01-15')
    expect(getExchangeRatesBulk).toHaveBeenCalledWith(['2023-01-03'])
  })

  it.each([
    ['조회가 실패하면', () => getExchangeRatesBulk.mockRejectedValue(new Error('down'))],
    ['그 날짜 값이 없으면', () => getExchangeRatesBulk.mockResolvedValue({ data: {} } as never)],
    ['값이 0 이면', () => getExchangeRatesBulk.mockResolvedValue({ data: { '2023-01-03': 0 } } as never)],
  ])('환율 %s 기본 환율로 두고 기본 환율이라고 표시한다', async (_case, arrange) => {
    getOHLCData.mockResolvedValue({ data: [candle('AAPL', '2023-01-03', 125)] } as never)
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    arrange()

    await expect(fetchStartQuote('AAPL', '2023-01-01')).resolves.toEqual({ date: '2023-01-03', close: 125, fxRate: 1350, defaultFx: true })
    warn.mockRestore()
  })

  it('2주 안에 시세가 없으면 null 이고 환율은 받지 않는다', async () => {
    getOHLCData.mockResolvedValue({ data: [] } as never)

    await expect(fetchStartQuote('AAPL', '2023-01-01')).resolves.toBeNull()
    expect(getExchangeRatesBulk).not.toHaveBeenCalled()
  })
})

describe('estimateRoundCost', () => {
  const quote = { date: '2023-01-03', close: 125, fxRate: 1270, defaultFx: false }

  it('종가 × 주식 수 × (1 + 수수료)를 그 날짜 환율로 원화로 바꿔 반올림한다', () => {
    expect(estimateRoundCost(quote, 2, 0.25)).toBe(318294)
  })

  it('환율을 주면 그 환율로 바꾼다', () => {
    expect(estimateRoundCost(quote, 2, 0, 1300)).toBe(325000)
  })
})

describe('useRoundCost', () => {
  const options: BacktestOptions = {
    periodEnd: 'HOLD', reinvestDividends: false, tradingFeeRate: '0', dividendTax: false, capitalGainsTax: false, fxMode: 'auto',
    manualPurchaseFxRate: '1300', manualCurrentFxRate: '1350',
  }
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }, children)
  const input = (patch = {}) => ({ symbol: 'AAPL', startDate: '2023-01-01', unit: 'SHARES' as const, shares: '2', amount: '', options, ...patch })

  beforeEach(() => {
    getOHLCData.mockResolvedValue({ data: [candle('AAPL', '2023-01-03', 125)] } as never)
    getExchangeRatesBulk.mockResolvedValue({ data: { '2023-01-03': 1270 } } as never)
  })

  it('금액 단위면 시세를 받지 않고 회당 금액 그대로', () => {
    const { result } = renderHook(() => useRoundCost(input({ unit: 'AMOUNT', amount: '300000' })), { wrapper })

    expect(result.current).toEqual({ perRound: 300000, defaultFx: false })
    expect(getOHLCData).not.toHaveBeenCalled()
  })

  it('주식 수 단위면 시작일 종가 × 주식 수 × (1 + 수수료)를 그 날짜 환율로 추정', async () => {
    const { result } = renderHook(() => useRoundCost(input({ options: { ...options, tradingFeeRate: '0.25' } })), { wrapper })

    await waitFor(() => expect(result.current).toEqual({ perRound: 318294, defaultFx: false }))
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2023-01-01', '2023-01-15')
  })

  it('수동 환율이면 매수 환율로, 주식 수가 1 ~ 1,000,000 정수가 아니면 모름', async () => {
    const manual = renderHook(() => useRoundCost(input({ options: { ...options, fxMode: 'manual' } })), { wrapper })
    await waitFor(() => expect(manual.result.current).toEqual({ perRound: 325000, defaultFx: false }))

    const broken = renderHook(() => useRoundCost(input({ shares: '1.5' })), { wrapper })
    expect(broken.result.current.perRound).toBeUndefined()
  })

  it('시작일 환율을 못 받으면 기본 환율로 추정하고 기본 환율이라고 표시, 직접 입력한 매수 환율이 있으면 그 환율', async () => {
    getExchangeRatesBulk.mockResolvedValue({ data: {} } as never)

    const auto = renderHook(() => useRoundCost(input()), { wrapper })
    await waitFor(() => expect(auto.result.current).toEqual({ perRound: 337500, defaultFx: true }))

    const manual = renderHook(() => useRoundCost(input({ options: { ...options, fxMode: 'manual' } })), { wrapper })
    await waitFor(() => expect(manual.result.current).toEqual({ perRound: 325000, defaultFx: false }))
  })
})
