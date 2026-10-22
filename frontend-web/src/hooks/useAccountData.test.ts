import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { accountsApi, portfolioApi } from '../services/api'
import { useAccountData } from './useAccountData'

vi.mock('../services/api', () => ({
  accountsApi: { getAccounts: vi.fn(), getCurrentExchangeRate: vi.fn(), getAccountBalance: vi.fn() },
  portfolioApi: { getPortfolio: vi.fn(), getPortfolioSummary: vi.fn() },
}))

// 테스트마다 새 클라이언트로 감쌈
const wrapper = ({ children }: { children: ReactNode }) =>
  createElement(QueryClientProvider, { client: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }, children)

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(accountsApi.getAccounts).mockResolvedValue({ data: [{ accountId: 1 }, { accountId: 2 }] } as never)
  vi.mocked(accountsApi.getCurrentExchangeRate).mockResolvedValue({ data: 1350 } as never)
  vi.mocked(accountsApi.getAccountBalance)
    .mockResolvedValueOnce({ data: { balanceKrw: 1_000_000, balanceUsd: 100 } } as never)
    .mockResolvedValueOnce({ data: { balanceKrw: 500_000, balanceUsd: 0 } } as never)
  vi.mocked(portfolioApi.getPortfolio).mockResolvedValue({ data: [{ symbol: 'AAPL', currentPrice: 200 }] } as never)
  vi.mocked(portfolioApi.getPortfolioSummary).mockResolvedValue({ data: { totalCurrentValue: 200 } } as never)
})

describe('useAccountData 총자산', () => {
  it('계좌마다 원화와 달러 × 환율을 더한 현금에 주식 평가액 × 환율을 더한다', async () => {
    const { result } = renderHook(() => useAccountData(true), { wrapper })

    // 1,000,000 + 100 × 1,350 + 500,000 + 200 × 1,350
    await waitFor(() => expect(result.current.totalAssets).toBe(1_905_000))
  })

  it('포트폴리오를 못 받으면 현금만 더한다', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    vi.mocked(portfolioApi.getPortfolio).mockRejectedValue(new Error('down'))

    const { result } = renderHook(() => useAccountData(true), { wrapper })

    await waitFor(() => expect(result.current.totalAssets).toBe(1_635_000))
  })

  it('로그인 전이면 계좌를 묻지 않고 총자산은 0 이다', () => {
    const { result } = renderHook(() => useAccountData(false), { wrapper })

    expect(accountsApi.getAccounts).not.toHaveBeenCalled()
    expect(result.current.totalAssets).toBe(0)
  })
})
