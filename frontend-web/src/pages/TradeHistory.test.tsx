import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { accountsApi, tradeApi } from '../services/api'
import { useAuth } from '../hooks/useAuth'
import TradeHistory from './TradeHistory'

vi.mock('../services/api', () => ({
  tradeApi: { history: vi.fn() },
  accountsApi: { getAccounts: vi.fn() },
}))
vi.mock('../hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('../hooks/useAuthModal', () => ({ useAuthModal: () => ({ openLogin, openSignup: vi.fn() }) }))
vi.mock('../components/trading/history/TradeHistoryList', () => ({
  TradeHistoryList: ({ transactions, accounts }: { transactions: unknown[]; accounts: unknown[] }) => (
    <p>내역 {transactions.length}건 · 계좌 {accounts.length}개</p>
  ),
}))

const openLogin = vi.fn()

function renderPage({ isAuthenticated = true, isLoading = false } = {}) {
  vi.mocked(useAuth).mockReturnValue({ isAuthenticated, isLoading } as never)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <TradeHistory />
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(tradeApi.history).mockResolvedValue({ data: [{ tradeId: 1 }, { tradeId: 2 }] } as never)
  vi.mocked(accountsApi.getAccounts).mockResolvedValue({ data: [{ accountId: 3, accountName: '연습' }] } as never)
})

describe('TradeHistory', () => {
  it('로그인 확인 중이면 내용을 그리지 않는다', () => {
    renderPage({ isAuthenticated: false, isLoading: true })

    expect(screen.queryByText('로그인이 필요한 서비스입니다')).toBeNull()
    expect(screen.queryByRole('heading', { name: '거래내역' })).toBeNull()
  })

  it('로그인 안 했으면 안내를 보이고 로그인하기는 로그인 창을 연다', async () => {
    const user = userEvent.setup()
    renderPage({ isAuthenticated: false })

    expect(screen.getByText('거래내역을 확인하시려면 먼저 로그인해주세요')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /로그인하기/ }))

    expect(openLogin).toHaveBeenCalledTimes(1)
    expect(tradeApi.history).not.toHaveBeenCalled()
  })

  it('거래 내역과 계좌 목록을 받아 목록에 넘긴다', async () => {
    renderPage()

    expect(await screen.findByText('내역 2건 · 계좌 1개')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '거래내역' })).toBeInTheDocument()
  })

  it('내역을 못 받으면 실패 문구를 보인다', async () => {
    vi.mocked(tradeApi.history).mockRejectedValue(new Error('down'))
    renderPage()

    expect(await screen.findByText('거래내역을 불러오는데 실패했습니다.')).toBeInTheDocument()
  })
})
