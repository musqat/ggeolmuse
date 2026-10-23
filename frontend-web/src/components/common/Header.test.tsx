import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import Header from './Header'
import { accountsApi, portfolioApi, stockApi } from '../../services/api'
import { useAuth } from '../../hooks/useAuth'
import { useAccountData } from '../../hooks/useAccountData'

vi.mock('../../services/api', () => ({
  accountsApi: { getAccounts: vi.fn(), getCurrentExchangeRate: vi.fn(), getAccountBalance: vi.fn() },
  portfolioApi: { getPortfolio: vi.fn(), getPortfolioSummary: vi.fn() },
  stockApi: { getAllSymbols: vi.fn() },
}))
vi.mock('../../hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('../../hooks/useAuthModal', () => ({ useAuthModal: () => ({ openLogin: vi.fn(), openSignup: vi.fn() }) }))
vi.mock('./SearchModal', () => ({ default: () => null }))
vi.mock('./ThemeToggle', () => ({ default: () => null }))

// 같은 훅으로 계좌 데이터를 쓰는 화면
function AccountScreen() {
  const { accounts } = useAccountData(true)
  return <p>계좌 {accounts.length}개</p>
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(useAuth).mockReturnValue({ isAuthenticated: true, user: { nickname: '머스캣' }, logout: vi.fn() } as never)
  vi.mocked(stockApi.getAllSymbols).mockResolvedValue({ data: [] } as never)
  vi.mocked(accountsApi.getAccounts).mockResolvedValue({ data: [{ accountId: 1 }] } as never)
  vi.mocked(accountsApi.getCurrentExchangeRate).mockResolvedValue({ data: 1350 } as never)
  vi.mocked(accountsApi.getAccountBalance).mockResolvedValue({ data: { balanceKrw: 1_000_000, balanceUsd: 100 } } as never)
  vi.mocked(portfolioApi.getPortfolio).mockResolvedValue({ data: [{ symbol: 'AAPL', currentPrice: 200 }] } as never)
  vi.mocked(portfolioApi.getPortfolioSummary).mockResolvedValue({ data: { totalCurrentValue: 200 } } as never)
})

describe('Header 총자산', () => {
  it('계좌 화면과 같은 계좌 데이터를 한 번만 불러 현금과 주식 평가액을 더한 총자산을 보여 준다', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <Header />
          <AccountScreen />
        </MemoryRouter>
      </QueryClientProvider>
    )

    // 1,000,000 + 100 × 1,350 + 200 × 1,350
    expect(await screen.findByText('₩1,405,000')).toBeInTheDocument()
    expect(await screen.findByText('계좌 1개')).toBeInTheDocument()
    expect(accountsApi.getAccounts).toHaveBeenCalledTimes(1)
    expect(portfolioApi.getPortfolioSummary).toHaveBeenCalledTimes(1)
  })
})
