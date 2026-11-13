import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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
  vi.mocked(accountsApi.getAccountBalance).mockResolvedValue({ data: { balanceKrw: 1_000_000, balanceUsd: 100, currentExchangeRate: 1350 } } as never)
  vi.mocked(portfolioApi.getPortfolio).mockResolvedValue({ data: [{ symbol: 'AAPL', currentPrice: 200 }] } as never)
  vi.mocked(portfolioApi.getPortfolioSummary).mockResolvedValue({ data: { totalCurrentValue: 200 } } as never)
})

describe('Header 현금', () => {
  it('계좌 화면과 같은 계좌 데이터를 한 번만 불러 헤더에 현금을 보여 준다', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <Header />
          <AccountScreen />
        </MemoryRouter>
      </QueryClientProvider>
    )

    // 1,000,000 + 100 × 1,350
    expect(await screen.findByText('현금 ₩1,135,000')).toBeInTheDocument()
    expect(await screen.findByText('계좌 1개')).toBeInTheDocument()
    expect(accountsApi.getAccounts).toHaveBeenCalledTimes(1)
    expect(portfolioApi.getPortfolioSummary).toHaveBeenCalledTimes(1)
  })
})

describe('Header 메뉴', () => {
  it('메뉴는 링크이고 계좌 아래 화면에서도 계좌 메뉴를 지금 화면으로 표시한다', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/account/stats']}>
          <Header />
        </MemoryRouter>
      </QueryClientProvider>
    )

    expect(screen.getByRole('link', { name: '계좌' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: '계좌' })).toHaveAttribute('href', '/account')
    expect(screen.getByRole('link', { name: '거래' })).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('link', { name: '마이페이지' })).toHaveAttribute('href', '/mypage')
    expect(await screen.findByText('현금 ₩1,135,000')).toBeInTheDocument()
  })

  it('모바일 메뉴 단추는 열림 상태와 연 메뉴 상자를 알린다', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <Header />
        </MemoryRouter>
      </QueryClientProvider>
    )
    const menuButton = screen.getByRole('button', { name: '메뉴' })

    expect(menuButton).toHaveAttribute('aria-expanded', 'false')
    expect(menuButton).not.toHaveAttribute('aria-controls')
    await userEvent.click(menuButton)

    expect(menuButton).toHaveAttribute('aria-expanded', 'true')
    const menu = document.getElementById(menuButton.getAttribute('aria-controls') ?? '')
    expect(menu).not.toBeNull()
    expect(within(menu!).getByRole('button', { name: '로그아웃' })).toBeInTheDocument()
  })
})
