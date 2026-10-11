import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AxiosError, type AxiosResponse } from 'axios'
import { authApi, type User } from '../services/api'
import { useAuth } from '../hooks/useAuth'
import MyPage from './MyPage'

vi.mock('../services/api', () => ({ authApi: { getCurrentUser: vi.fn(), deleteAccount: vi.fn() } }))
vi.mock('../hooks/useAuth', () => ({ useAuth: vi.fn() }))

const BASE_USER: User = {
  id: 1, email: 'u@example.local', nickname: '유저', role: 'USER', provider: 'LOCAL',
  emailVerified: true, createdAt: '2026-01-05T12:00:00', hasPassword: true,
}
const jwt = (payload: object) => `h.${btoa(JSON.stringify(payload))}.s`
const minutesAgo = (m: number) => Math.floor(Date.now() / 1000) - m * 60
const forbidden = new AxiosError('fail', 'ERR', undefined, undefined, {
  status: 403, data: { detail: '보안을 위해 다시 로그인한 뒤 탈퇴할 수 있습니다.' }, statusText: '', headers: {}, config: {},
} as AxiosResponse)

let logout: ReturnType<typeof vi.fn>
let loginWithGoogle: ReturnType<typeof vi.fn>

function renderPage(user: Partial<User>, { authTime = minutesAgo(1), path = '/mypage' } = {}) {
  localStorage.setItem('accessToken', jwt({ exp: minutesAgo(-60), auth_time: authTime }))
  vi.mocked(authApi.getCurrentUser).mockResolvedValue({ data: { ...BASE_USER, ...user } } as never)
  vi.mocked(useAuth).mockReturnValue({ isAuthenticated: true, logout, loginWithGoogle } as never)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <MyPage />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

// 탈퇴 모달을 열고 확인 문구를 넣는다
async function openDeleteModal(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByText('회원 탈퇴', { selector: 'p' }))
  await user.type(screen.getByPlaceholderText('회원탈퇴'), '회원탈퇴')
}
const deleteButton = () => screen.getByRole('button', { name: '탈퇴하기' })

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  logout = vi.fn()
  loginWithGoogle = vi.fn()
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  vi.spyOn(window, 'alert').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

describe('MyPage 회원 탈퇴', () => {
  it('비밀번호 사용자는 비밀번호를 넣어야 누를 수 있고, 그 비밀번호를 실어 보낸다', async () => {
    const user = userEvent.setup()
    vi.mocked(authApi.deleteAccount).mockResolvedValue({} as never)
    renderPage({ hasPassword: true })

    await openDeleteModal(user)
    expect(deleteButton()).toBeDisabled()
    await user.type(screen.getByLabelText('비밀번호'), 'typed-secret')
    await user.click(deleteButton())

    await waitFor(() => expect(authApi.deleteAccount).toHaveBeenCalledWith('typed-secret'))
    expect(logout).toHaveBeenCalled()
  })

  it('구글 사용자는 방금 로그인했으면 비밀번호 없이 보낸다', async () => {
    const user = userEvent.setup()
    vi.mocked(authApi.deleteAccount).mockResolvedValue({} as never)
    renderPage({ hasPassword: false }, { authTime: minutesAgo(1) })

    await openDeleteModal(user)
    expect(screen.queryByLabelText('비밀번호')).toBeNull()
    await user.click(deleteButton())

    await waitFor(() => expect(authApi.deleteAccount).toHaveBeenCalledWith(undefined))
  })

  it('구글 사용자가 로그인한 지 오래됐으면 탈퇴 대신 다시 로그인시키고 마이페이지로 돌아오게 한다', async () => {
    const user = userEvent.setup()
    renderPage({ hasPassword: false }, { authTime: minutesAgo(30) })

    await openDeleteModal(user)
    expect(deleteButton()).toBeDisabled()
    await user.click(screen.getByRole('button', { name: '구글로 다시 로그인' }))

    expect(loginWithGoogle).toHaveBeenCalledWith({ reauth: true, returnTo: '/mypage?delete=1' })
    expect(authApi.deleteAccount).not.toHaveBeenCalled()
  })

  it('서버가 403(다시 로그인)으로 막으면 재로그인 안내로 바꾼다', async () => {
    const user = userEvent.setup()
    vi.mocked(authApi.deleteAccount).mockRejectedValue(forbidden)
    renderPage({ hasPassword: false }, { authTime: minutesAgo(1) })

    await openDeleteModal(user)
    await user.click(deleteButton())

    expect(await screen.findByRole('button', { name: '구글로 다시 로그인' })).toBeInTheDocument()
    expect(window.alert).not.toHaveBeenCalled()
    expect(logout).not.toHaveBeenCalled()
  })

  it('다시 로그인하고 돌아오면(?delete=1) 모달이 열려 있다', async () => {
    renderPage({ hasPassword: false }, { path: '/mypage?delete=1' })

    expect(await screen.findByPlaceholderText('회원탈퇴')).toBeInTheDocument()
  })

  it('비밀번호 변경은 비밀번호 있는 사용자에게만 보인다', async () => {
    renderPage({ hasPassword: false, provider: 'LOCAL' })
    await screen.findByText('닉네임 변경')
    expect(screen.queryByText('비밀번호 변경')).toBeNull()
  })
})
