import { describe, it, expect, vi, beforeEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { stockApi } from '../../../services/api'
import { estimateRoundCost, fetchStartQuote, parseShares, useAmountUnit } from './amountUnit'

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
  it('1 이상 정수를 주식 수로 읽는다', () => {
    expect(parseShares('1')).toBe(1)
    expect(parseShares('12')).toBe(12)
  })

  it.each(['0', '-1', '1.5', '', ' ', 'abc'])('%o 는 주식 수가 아니다', (shares) => {
    expect(parseShares(shares)).toBeUndefined()
  })
})

describe('fetchStartQuote', () => {
  it('시작일부터 2주 안의 첫 거래일 종가와 그 날짜 환율을 받는다', async () => {
    getOHLCData.mockResolvedValue({
      data: [candle('MSFT', '2023-01-03', 240), candle('AAPL', '2023-01-04', 126), candle('AAPL', '2023-01-03', 125)],
    } as never)
    getExchangeRatesBulk.mockResolvedValue({ data: { '2023-01-03': 1270 } } as never)

    await expect(fetchStartQuote('AAPL', '2023-01-01')).resolves.toEqual({ date: '2023-01-03', close: 125, fxRate: 1270 })
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2023-01-01', '2023-01-15')
    expect(getExchangeRatesBulk).toHaveBeenCalledWith(['2023-01-03'])
  })

  it('2주 안에 시세가 없으면 null 이고 환율은 받지 않는다', async () => {
    getOHLCData.mockResolvedValue({ data: [] } as never)

    await expect(fetchStartQuote('AAPL', '2023-01-01')).resolves.toBeNull()
    expect(getExchangeRatesBulk).not.toHaveBeenCalled()
  })
})

describe('estimateRoundCost', () => {
  const quote = { date: '2023-01-03', close: 125, fxRate: 1270 }

  it('종가 × 주식 수 × (1 + 수수료)를 그 날짜 환율로 원화로 바꿔 반올림한다', () => {
    expect(estimateRoundCost(quote, 2, 0.25)).toBe(318294)
  })

  it('환율을 주면 그 환율로 바꾼다', () => {
    expect(estimateRoundCost(quote, 2, 0, 1300)).toBe(325000)
  })
})
