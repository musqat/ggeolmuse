import { describe, it, expect, vi, beforeEach } from 'vitest'
import { cloneElement, type ReactElement } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import RequireAuth from './RequireAuth'

const openLogin = vi.fn()

vi.mock('../hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('../hooks/useAuthModal', () => ({ useAuthModal: () => ({ openLogin, openSignup: vi.fn() }) }))

function renderGate(auth: object, gate: ReactElement, path = '/account') {
  vi.mocked(useAuth).mockReturnValue(auth as never)
  const tree = () => (
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/" element={<p>홈</p>} />
        <Route path="/unauthorized" element={<p>권한 없음</p>} />
        <Route path={path} element={cloneElement(gate)} />
      </Routes>
    </MemoryRouter>
  )
  const { rerender } = render(tree())
  // 로그인 상태를 바꿔 다시 그림
  return (next: object) => {
    vi.mocked(useAuth).mockReturnValue(next as never)
    rerender(tree())
  }
}

const accountGate = (
  <RequireAuth loginPrompt="계좌 관리 기능을 이용하시려면 먼저 로그인해주세요">
    <p>계좌 화면</p>
  </RequireAuth>
)

beforeEach(() => vi.clearAllMocks())

describe('RequireAuth', () => {
  it('로그인 확인 중이면 불러오는 중만 보이고 안내도 화면도 그리지 않는다', () => {
    renderGate({ isLoading: true, isAuthenticated: false, user: null }, accountGate)

    expect(screen.getByRole('status', { name: '불러오는 중' })).toBeInTheDocument()
    expect(screen.queryByText('로그인이 필요한 서비스입니다')).toBeNull()
    expect(screen.queryByText('계좌 화면')).toBeNull()
  })

  it('로그인 안 했으면 그 자리에 안내를 보이고 로그인하기는 로그인 창을 연다', async () => {
    renderGate({ isLoading: false, isAuthenticated: false, user: null }, accountGate)

    expect(screen.getByText('계좌 관리 기능을 이용하시려면 먼저 로그인해주세요')).toBeInTheDocument()
    expect(screen.queryByText('계좌 화면')).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: /로그인하기/ }))

    expect(openLogin).toHaveBeenCalledOnce()
  })

  it('안내를 보인 뒤 로그인 요청 중(확인 중)에도 안내를 그대로 둔다', () => {
    const update = renderGate({ isLoading: false, isAuthenticated: false, user: null }, accountGate)
    const loginButton = screen.getByRole('button', { name: /로그인하기/ })

    update({ isLoading: true, isAuthenticated: false, user: null })

    expect(screen.getByRole('button', { name: /로그인하기/ })).toBe(loginButton)
    expect(screen.queryByRole('status', { name: '불러오는 중' })).toBeNull()
  })

  it('안내 문구가 없는 경로는 로그인 안 했으면 홈으로 보낸다', () => {
    renderGate({ isLoading: false, isAuthenticated: false, user: null }, <RequireAuth><p>마이페이지</p></RequireAuth>, '/mypage')

    expect(screen.getByText('홈')).toBeInTheDocument()
  })

  it('로그인했으면 화면을 그린다', () => {
    renderGate({ isLoading: false, isAuthenticated: true, user: { role: 'USER' } }, accountGate)

    expect(screen.getByText('계좌 화면')).toBeInTheDocument()
  })

  it('관리자 경로는 관리자가 아니면 권한 없음으로 보낸다', () => {
    renderGate({ isLoading: false, isAuthenticated: true, user: { role: 'USER' } }, <RequireAuth admin><p>관리자 화면</p></RequireAuth>, '/admin')

    expect(screen.getByText('권한 없음')).toBeInTheDocument()
  })

  it('관리자 경로는 관리자면 화면을 그린다', () => {
    renderGate({ isLoading: false, isAuthenticated: true, user: { role: 'ADMIN' } }, <RequireAuth admin><p>관리자 화면</p></RequireAuth>, '/admin')

    expect(screen.getByText('관리자 화면')).toBeInTheDocument()
  })
})
