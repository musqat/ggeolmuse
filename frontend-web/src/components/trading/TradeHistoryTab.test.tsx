import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import TradeHistoryTab from './TradeHistoryTab'
import { accountsApi, tradeApi } from '../../services/api'

vi.mock('../../services/api', () => ({
  accountsApi: { getAccounts: vi.fn() },
  tradeApi: { history: vi.fn() },
}))

describe('TradeHistoryTab', () => {
  it('거래 내역을 받는 동안 불러오는 중 표시를 보인다', () => {
    vi.mocked(accountsApi.getAccounts).mockResolvedValue({ data: [] } as never)
    vi.mocked(tradeApi.history).mockReturnValue(new Promise(() => {}) as never)
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <TradeHistoryTab />
      </QueryClientProvider>
    )

    expect(screen.getByRole('status', { name: '불러오는 중' })).toHaveClass('h-8')
  })
})
