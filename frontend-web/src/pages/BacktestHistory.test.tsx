import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import BacktestHistory from './BacktestHistory'
import { backtestApi } from '../services/api'
import { getLocalBacktestHistory } from '../utils/localBacktestHistory'

vi.mock('../services/api', () => ({
  backtestApi: { getHistory: vi.fn() },
}))

const auth = vi.hoisted(() => ({
  isAuthenticated: true,
  user: { email: 'tester@example.com' } as { email: string } | null,
}))
vi.mock('../hooks/useAuth', () => ({ useAuth: () => auth }))

vi.mock('../utils/localBacktestHistory', () => ({
  getLocalBacktestHistory: vi.fn(() => []),
}))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <BacktestHistory />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  auth.isAuthenticated = true
  auth.user = { email: 'tester@example.com' }
  vi.mocked(backtestApi.getHistory).mockResolvedValue({ data: { content: [], totalPages: 0 } } as never)
})

describe('지난 백테스트 기록 페이지', () => {
  it('로그인했으면 페이지 번호와 크기만 보내 이력을 조회하고 백테스트로 돌아가는 링크를 둔다', async () => {
    renderPage()

    expect(screen.getByRole('heading', { name: '지난 백테스트 기록' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '← 백테스트' })).toHaveAttribute('href', '/backtest')
    await vi.waitFor(() => expect(backtestApi.getHistory).toHaveBeenCalledWith(0, 20))
    expect(await screen.findByText('백테스트를 실행하면 여기에 기록됩니다.')).toBeInTheDocument()
  })

  it('로그인하지 않았으면 서버에 묻지 않고 이 기기의 기록을 보인다', () => {
    auth.isAuthenticated = false
    auth.user = null
    renderPage()

    expect(getLocalBacktestHistory).toHaveBeenCalled()
    expect(backtestApi.getHistory).not.toHaveBeenCalled()
    expect(screen.getByText(/이 기기에 저장됩니다/)).toBeInTheDocument()
  })
})
