import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { accountsApi, portfolioApi } from '../services/api'
import { queryKeys } from '../services/queryKeys'
import { usePortfolioData } from './usePortfolioData'

vi.mock('../services/api', () => ({
  accountsApi: { getAccountBalance: vi.fn() },
  portfolioApi: { getAccountPortfolio: vi.fn() },
}))

const HOLDING = { symbol: 'AAPL', quantity: 2 }
const BALANCE = { balanceKrw: 1000, currentExchangeRate: 1350 }

let client: QueryClient

function wrapper() {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

beforeEach(() => {
  vi.mocked(portfolioApi.getAccountPortfolio).mockResolvedValue({ data: [HOLDING] } as never)
  vi.mocked(accountsApi.getAccountBalance).mockResolvedValue({ data: BALANCE } as never)
})

describe('usePortfolioData', () => {
  it('다시 받기가 실패해도 받아 둔 보유 · 잔액이 있으면 실패로 가리지 않는다', async () => {
    const { result } = renderHook(() => usePortfolioData('1', true), { wrapper: wrapper() })
    await waitFor(() => expect(result.current.holdings).toHaveLength(1))

    vi.mocked(portfolioApi.getAccountPortfolio).mockRejectedValue(new Error('trade down'))
    vi.mocked(accountsApi.getAccountBalance).mockRejectedValue(new Error('user down'))
    act(() => result.current.retry())
    await waitFor(() => expect(client.getQueryState(queryKeys.portfolio.holdings('1'))?.status).toBe('error'))
    await waitFor(() => expect(client.getQueryState(queryKeys.account.balance('1'))?.status).toBe('error'))

    expect(result.current.failed).toBe(false)
    expect(result.current.holdings).toEqual([HOLDING])
    expect(result.current.balance).toEqual(BALANCE)
  })

  it('처음부터 보유를 못 받으면 실패로 알린다', async () => {
    vi.mocked(portfolioApi.getAccountPortfolio).mockRejectedValue(new Error('trade down'))

    const { result } = renderHook(() => usePortfolioData('1', true), { wrapper: wrapper() })

    await waitFor(() => expect(result.current.failed).toBe(true))
    expect(result.current.holdings).toEqual([])
  })
})
