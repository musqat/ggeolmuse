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
vi.mock('../components/trading/history/TradeHistoryList', () => ({
  TradeHistoryList: ({ transactions, accounts }: { transactions: unknown[]; accounts: unknown[] }) => (
    <p>내역 {transactions.length}건 · 계좌 {accounts.length}개</p>
  ),
}))

function renderPage() {
  vi.mocked(useAuth).mockReturnValue({ isAuthenticated: true, isLoading: false } as never)
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
  it('거래 내역과 계좌 목록을 받아 목록에 넘긴다', async () => {
    renderPage()

    expect(await screen.findByText('내역 2건 · 계좌 1개')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '거래내역' })).toBeInTheDocument()
  })

  it('내역을 못 받으면 빈 목록 대신 실패와 다시 시도를 보이고 다시 시도하면 다시 받는다', async () => {
    vi.mocked(tradeApi.history).mockRejectedValue(new Error('down'))
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('거래내역을 불러오지 못했습니다')
    expect(screen.queryByText(/내역 0건/)).toBeNull()

    vi.mocked(tradeApi.history).mockResolvedValue({ data: [{ tradeId: 1 }] } as never)
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByText('내역 1건 · 계좌 1개')).toBeInTheDocument()
  })
})
