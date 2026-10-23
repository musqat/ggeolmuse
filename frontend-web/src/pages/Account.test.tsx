import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { accountsApi, portfolioApi } from '../services/api'
import { useAuth } from '../hooks/useAuth'
import { notify } from '@/components/common/notify'
import Account from './Account'

vi.mock('../services/api', () => ({
  accountsApi: {
    getAccounts: vi.fn(), getCurrentExchangeRate: vi.fn(), getAccountBalance: vi.fn(), getExchangeRateRange: vi.fn(),
    depositKrw: vi.fn(), createAccount: vi.fn(), exchangeCurrency: vi.fn(), deleteAccount: vi.fn(),
  },
  portfolioApi: { getPortfolio: vi.fn(), getPortfolioSummary: vi.fn() },
}))
vi.mock('../hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('../hooks/useAuthModal', () => ({ useAuthModal: () => ({ openLogin: vi.fn(), openSignup: vi.fn() }) }))

const ACCOUNT = { accountId: 3, accountName: '연습', commissionRate: 0.0025, usdBalance: 100, krwBalance: 1_000_000 }

function renderPage(isAuthenticated = true) {
  vi.mocked(useAuth).mockReturnValue({ isAuthenticated } as never)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Account />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(notify, 'success').mockImplementation(() => {})
  vi.spyOn(notify, 'error').mockImplementation(() => {})
  vi.mocked(accountsApi.getAccounts).mockResolvedValue({ data: [ACCOUNT] } as never)
  vi.mocked(accountsApi.getCurrentExchangeRate).mockResolvedValue({ data: 1350 } as never)
  vi.mocked(accountsApi.getAccountBalance).mockResolvedValue({
    data: { balanceKrw: 1_000_000, balanceUsd: 100, slippageRate: 0.001 },
  } as never)
  vi.mocked(portfolioApi.getPortfolio).mockResolvedValue({ data: [] } as never)
})
afterEach(() => vi.restoreAllMocks())

describe('Account', () => {
  it('로그인 안 했으면 로그인 안내를 보인다', () => {
    renderPage(false)

    expect(screen.getByText('계좌 관리 기능을 이용하시려면 먼저 로그인해주세요')).toBeInTheDocument()
    expect(accountsApi.getAccounts).not.toHaveBeenCalled()
  })

  it('계좌 카드마다 잔액을 보이고 숨기기를 누르면 가린다', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('₩1,000,000')).toBeInTheDocument()
    expect(screen.getByText('$100.00')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '잔액 숨기기' }))

    expect(screen.queryByText('₩1,000,000')).toBeNull()
    expect(screen.getAllByText('₩***,***').length).toBeGreaterThan(0)
    expect(screen.getByText('$***.**')).toBeInTheDocument()
  })

  it('계좌가 없으면 만들기 안내를 보인다', async () => {
    vi.mocked(accountsApi.getAccounts).mockResolvedValue({ data: [] } as never)
    renderPage()

    expect(await screen.findByText('계좌가 없습니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '계좌 생성하기' })).toBeInTheDocument()
  })

  it('카드의 입금은 그 계좌로 입금 창을 열고 보낸다', async () => {
    const user = userEvent.setup()
    vi.mocked(accountsApi.depositKrw).mockResolvedValue({} as never)
    renderPage()

    await user.click(await screen.findByRole('button', { name: '입금' }))
    await user.type(screen.getByPlaceholderText('입금할 금액을 입력하세요'), '100000')
    await user.click(screen.getByRole('button', { name: '입금하기' }))

    expect(accountsApi.depositKrw).toHaveBeenCalledWith(3, { krwAmount: 100000 })
    expect(notify.success).toHaveBeenCalledWith('입금이 완료되었습니다.')
  })
})
