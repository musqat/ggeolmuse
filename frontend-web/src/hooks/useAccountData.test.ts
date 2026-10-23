import { describe, it, expect, vi, beforeEach } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { accountsApi, portfolioApi } from '../services/api'
import { useAccountData } from './useAccountData'
import { useAccounts } from './useAccounts'

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

// 앱과 같은 신선 기간(5분)
const appClient = () => new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 5 * 60 * 1000 } } })
const withClient = (client: QueryClient) => ({ children }: { children: ReactNode }) =>
  createElement(QueryClientProvider, { client }, children)
const useHeaderFirst = () => { const data = useAccountData(true); const list = useAccounts(true); return { data, list } }
const useScreenFirst = () => { const list = useAccounts(true); const data = useAccountData(true); return { data, list } }
const zeroBalance = () =>
  vi.mocked(accountsApi.getAccountBalance).mockResolvedValue({ data: { balanceKrw: 0, balanceUsd: 0 } } as never)

describe('useAccountData 계좌 목록', () => {
  it.each([['헤더', useHeaderFirst], ['화면', useScreenFirst]])('%s 쪽이 먼저 불러도 계좌 목록은 한 번만 부른다', async (_, hooks) => {
    const { result } = renderHook(hooks, { wrapper: withClient(appClient()) })

    await waitFor(() => expect(result.current.data.accounts).toHaveLength(2))
    expect(result.current.list.accounts).toHaveLength(2)
    expect(accountsApi.getAccounts).toHaveBeenCalledTimes(1)
  })

  it('다시 불러오면 계좌 목록도 새로 받는다', async () => {
    const { result } = renderHook(() => useAccountData(true), { wrapper: withClient(appClient()) })
    await waitFor(() => expect(result.current.accounts).toHaveLength(2))
    vi.mocked(accountsApi.getAccounts).mockResolvedValue({ data: [{ accountId: 1 }, { accountId: 2 }, { accountId: 3 }] } as never)
    zeroBalance()

    await act(() => result.current.refetch())

    await waitFor(() => expect(result.current.accounts).toHaveLength(3))
  })

  it('거래 뒤 계좌 쿼리 둘을 같이 무효화해도 계좌 목록은 한 번만 다시 부른다', async () => {
    const client = appClient()
    const { result } = renderHook(useHeaderFirst, { wrapper: withClient(client) })
    await waitFor(() => expect(result.current.data.accounts).toHaveLength(2))
    zeroBalance()
    vi.mocked(accountsApi.getAccounts).mockClear()

    await act(() => Promise.all([
      client.invalidateQueries({ queryKey: ['account'] }),
      client.invalidateQueries({ queryKey: ['accounts'] }, { cancelRefetch: false }),
    ]))

    expect(accountsApi.getAccounts).toHaveBeenCalledTimes(1)
  })
})
