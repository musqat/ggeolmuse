import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import type { ReactNode } from 'react'
import { render, screen } from '@testing-library/react'
import { useAuth } from './hooks/useAuth'
import App from './App'

const header = vi.hoisted(() => ({ broken: false }))

vi.mock('./hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('./hooks/useAuthModal', () => ({ useAuthModal: () => ({ openLogin: vi.fn(), openSignup: vi.fn() }) }))
vi.mock('./contexts/AuthContext', () => ({ AuthProvider: ({ children }: { children: ReactNode }) => children }))
vi.mock('./contexts/AuthModalContext', () => ({ AuthModalProvider: ({ children }: { children: ReactNode }) => children }))
vi.mock('./components/common/Header', () => ({
  default: () => {
    if (header.broken) throw new Error('머리말 오류')
    return null
  },
}))
vi.mock('./components/layout/Footer', () => ({ default: () => null }))
vi.mock('./components/chat/AiChatButton', () => ({ default: () => null }))
vi.mock('./components/auth/NicknameRequiredPrompt', () => ({ NicknameRequiredPrompt: () => null }))
vi.mock('./components/layout/AdminLayout', () => ({ default: () => <p>관리자 화면</p> }))
vi.mock('./pages/Home', () => ({ default: () => <p>홈 화면</p> }))
vi.mock('./pages/Stocks', () => ({ default: () => <p>종목 화면</p> }))
vi.mock('./pages/Backtest', () => ({ default: () => <p>백테스트 화면</p> }))
vi.mock('./pages/BacktestHistory', () => ({ default: () => <p>백테스트 기록 화면</p> }))
vi.mock('./pages/Charts', () => ({ default: () => <p>차트 화면</p> }))
vi.mock('./pages/Portfolio', () => ({ default: () => <p>포트폴리오 화면</p> }))
vi.mock('./pages/Trading', () => ({ default: () => <p>거래 화면</p> }))
vi.mock('./pages/TradeHistory', () => ({ default: () => <p>거래내역 화면</p> }))
vi.mock('./pages/Account', () => ({ default: () => <p>계좌 화면</p> }))
vi.mock('./pages/AccountStats', () => ({ default: () => <p>계좌 통계 화면</p> }))
vi.mock('./pages/MyPage', () => ({ default: () => <p>마이페이지 화면</p> }))

function renderAt(path: string, auth: object) {
  vi.mocked(useAuth).mockReturnValue({ isLoading: false, isAuthenticated: false, user: null, ...auth } as never)
  window.history.pushState({}, '', path)
  render(<App />)
}

beforeEach(() => {
  header.broken = false
})
afterEach(() => {
  vi.restoreAllMocks()
  window.history.pushState({}, '', '/')
})

describe('App 로그인 게이트', () => {
  it.each([
    ['/portfolio', '포트폴리오 기능을 이용하시려면 먼저 로그인해주세요', '포트폴리오 화면'],
    ['/trading', '가상 거래 서비스를 이용하시려면 먼저 로그인해주세요', '거래 화면'],
    ['/trade-history', '거래내역을 확인하시려면 먼저 로그인해주세요', '거래내역 화면'],
    ['/account', '계좌 관리 기능을 이용하시려면 먼저 로그인해주세요', '계좌 화면'],
    ['/account/stats', '계좌 통계를 보시려면 먼저 로그인해주세요', '계좌 통계 화면'],
  ])('%s 는 로그인 안 했으면 그 자리에 안내를 보이고 화면을 그리지 않는다', async (path, prompt, page) => {
    renderAt(path, {})

    expect(await screen.findByText(prompt)).toBeInTheDocument()
    expect(screen.queryByText(page)).toBeNull()
    expect(window.location.pathname).toBe(path)
  })

  it('로그인 확인 중이면 안내 없이 불러오는 중만 보인다', async () => {
    renderAt('/account', { isLoading: true })

    expect(await screen.findByRole('status', { name: '불러오는 중' })).toBeInTheDocument()
    expect(screen.queryByText('로그인이 필요한 서비스입니다')).toBeNull()
  })

  it('로그인했으면 그 화면을 그린다', async () => {
    renderAt('/account', { isAuthenticated: true, user: { role: 'USER' } })

    expect(await screen.findByText('계좌 화면')).toBeInTheDocument()
  })

  it.each([
    ['/', '홈 화면'],
    ['/stocks', '종목 화면'],
    ['/backtest', '백테스트 화면'],
    ['/backtest/history', '백테스트 기록 화면'],
    ['/charts', '차트 화면'],
    ['/charts/AAPL', '차트 화면'],
  ])('공개 경로 %s 는 로그인 안 해도 그 화면을 그린다', async (path, page) => {
    renderAt(path, {})

    expect(await screen.findByText(page)).toBeInTheDocument()
    expect(screen.queryByText('로그인이 필요한 서비스입니다')).toBeNull()
    expect(window.location.pathname).toBe(path)
  })

  it.each(['/mypage', '/admin/market'])('%s 는 로그인 안 했으면 홈으로 보낸다', async (path) => {
    renderAt(path, {})

    expect(await screen.findByText('홈 화면')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/')
  })
})

describe('App 오류 경계', () => {
  it('화면 바깥(머리말)에서 렌더 오류가 나도 빈 화면 대신 안내와 다시 시도를 보인다', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    header.broken = true
    renderAt('/', {})

    expect(await screen.findByRole('alert')).toHaveTextContent('문제가 생겼습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})
