import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createElement, type ReactNode } from 'react'
import { act, renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { tradeApi } from '../services/api'
import { notify } from '@/components/common/notify'
import { useTradeOrder } from './useTradeOrder'

vi.mock('../services/api', () => ({
  tradeApi: { buy: vi.fn(), sell: vi.fn() },
}))

const TRADE = {
  id: 1, accountId: 3, symbol: 'AAPL', tradeType: 'BUY', quantity: 10, price: 100, totalAmount: 1000, fee: 1,
  tradeDate: '2026-11-06', executedAt: '2026-11-06T10:00:00', status: 'COMPLETED',
}

const input = {
  accountId: 3, symbol: 'AAPL', orderType: 'buy' as 'buy' | 'sell', priceType: 'close' as const,
  quantity: '10', limitPrice: '', tradeDate: '2026-11-06', ohlc: { open: 99, high: 102, low: 98, close: 100 },
}

function wrapper({ children }: { children: ReactNode }) {
  return createElement(QueryClientProvider, { client: new QueryClient() }, children)
}

const keyOf = (call: number) => vi.mocked(tradeApi.buy).mock.calls[call][1]

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(notify, 'success').mockImplementation(() => {})
  vi.spyOn(notify, 'error').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

describe('useTradeOrder 중복 요청 키', () => {
  it('실패한 주문을 그대로 다시 보내면 같은 키, 성공한 뒤에는 새 키를 싣는다', async () => {
    vi.mocked(tradeApi.buy)
      .mockRejectedValueOnce(new Error('timeout'))
      .mockResolvedValue({ data: TRADE } as never)
    const { result } = renderHook(() => useTradeOrder(input), { wrapper })

    await act(() => result.current.submit())
    await act(() => result.current.submit())
    await act(() => result.current.submit())

    expect(keyOf(0)).toEqual(expect.any(String))
    expect(keyOf(1)).toBe(keyOf(0))
    expect(keyOf(2)).not.toBe(keyOf(0))
  })

  it('매수가 실패한 뒤 매도로 바꾸면 새 키를 싣는다', async () => {
    vi.mocked(tradeApi.buy).mockRejectedValue(new Error('down'))
    vi.mocked(tradeApi.sell).mockRejectedValue(new Error('down'))
    const { result, rerender } = renderHook((props) => useTradeOrder(props), { wrapper, initialProps: input })

    await act(() => result.current.submit())
    rerender({ ...input, orderType: 'sell' })
    await act(() => result.current.submit())

    expect(vi.mocked(tradeApi.sell).mock.calls[0][1]).not.toBe(keyOf(0))
  })

  it('같은 키로 다시 받은 체결이 그새 취소됐으면 실패로 알리고 다음 주문은 새 키다', async () => {
    vi.mocked(tradeApi.buy)
      .mockResolvedValueOnce({ data: { ...TRADE, status: 'CANCELLED', cancelReason: 'INSUFFICIENT_USD_BALANCE' } } as never)
      .mockResolvedValue({ data: TRADE } as never)
    const { result } = renderHook(() => useTradeOrder(input), { wrapper })

    let placed = true
    await act(async () => { placed = await result.current.submit() })
    await act(() => result.current.submit())

    expect(placed).toBe(false)
    expect(notify.error).toHaveBeenCalledWith('주문 실패: 체결이 취소되었습니다(잔액 부족)')
    expect(notify.success).not.toHaveBeenCalledWith(expect.stringContaining('취소'))
    expect(keyOf(1)).not.toBe(keyOf(0))
  })

  it('수량을 바꾸면 새 키를 싣는다', async () => {
    vi.mocked(tradeApi.buy).mockRejectedValue(new Error('down'))
    const { result, rerender } = renderHook((props) => useTradeOrder(props), { wrapper, initialProps: input })

    await act(() => result.current.submit())
    rerender({ ...input, quantity: '11' })
    await act(() => result.current.submit())

    expect(keyOf(1)).not.toBe(keyOf(0))
  })
})
