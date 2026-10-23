import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AxiosError, type AxiosResponse } from 'axios'
import { authApi, type User } from '../services/api'
import { useAuth } from '../hooks/useAuth'
import MyPage from './MyPage'
import { notify } from '@/components/common/notify'

vi.mock('../services/api', () => ({
  authApi: { getCurrentUser: vi.fn(), deleteAccount: vi.fn(), changeNickname: vi.fn(), changePassword: vi.fn() },
}))
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
let refreshUserData: ReturnType<typeof vi.fn>

function renderPage(
  user: Partial<User>,
  { authTime = minutesAgo(1), path = '/mypage', auth = {} }: { authTime?: number; path?: string; auth?: object } = {}
) {
  localStorage.setItem('accessToken', jwt({ exp: minutesAgo(-60), auth_time: authTime }))
  vi.mocked(authApi.getCurrentUser).mockResolvedValue({ data: { ...BASE_USER, ...user } } as never)
  vi.mocked(useAuth).mockReturnValue({
    isAuthenticated: true, isLoading: false, user: { ...BASE_USER, ...user }, refreshUserData, logout, loginWithGoogle, ...auth,
  } as never)
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/" element={<p>홈</p>} />
        <Route path="/mypage" element={<MyPage />} />
      </Routes>
    </MemoryRouter>
  )
}

// 닉네임 창에서 칸을 비우고 새 닉네임으로 변경
async function changeNickname(user: ReturnType<typeof userEvent.setup>, name: string) {
  await user.click(await screen.findByText('닉네임 변경', { selector: 'p' }))
  const input = screen.getByPlaceholderText('변경할 닉네임 입력')
  await user.clear(input)
  await user.type(input, name)
  await user.click(screen.getByRole('button', { name: '변경하기' }))
}

// 비밀번호 창을 열고 현재 · 새 · 확인 칸을 차례로 채워 변경
async function changePassword(user: ReturnType<typeof userEvent.setup>, current: string, next: string, confirm = next) {
  await user.click(await screen.findByText('비밀번호 변경', { selector: 'p' }))
  const [currentInput, newInput, confirmInput] = document.querySelectorAll<HTMLInputElement>('input[type="password"]')
  await user.type(currentInput, current)
  await user.type(newInput, next)
  await user.type(confirmInput, confirm)
  await user.click(screen.getByRole('button', { name: '변경하기' }))
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
  refreshUserData = vi.fn().mockResolvedValue(undefined)
  vi.spyOn(notify, 'confirm').mockResolvedValue(true)
  vi.spyOn(notify, 'success').mockImplementation(() => {})
  vi.spyOn(notify, 'error').mockImplementation(() => {})
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
    expect(notify.error).not.toHaveBeenCalled()
    expect(notify.success).not.toHaveBeenCalled()
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

describe('MyPage 비밀번호 변경', () => {
  it('비밀번호를 바꾸면 현재 · 새 비밀번호를 보낸다', async () => {
    const user = userEvent.setup()
    vi.mocked(authApi.changePassword).mockResolvedValue({} as never)
    renderPage({ hasPassword: true })

    await changePassword(user, 'old-pass1', 'new-pass1')

    await waitFor(() => expect(notify.success).toHaveBeenCalledWith('비밀번호가 변경되었습니다.'))
    expect(authApi.changePassword).toHaveBeenCalledWith({ currentPassword: 'old-pass1', newPassword: 'new-pass1' })
    expect(screen.queryByRole('button', { name: '변경하기' })).toBeNull()
  })

  it('새 비밀번호가 다르면 보내지 않는다', async () => {
    const user = userEvent.setup()
    renderPage({ hasPassword: true })

    await changePassword(user, 'old-pass1', 'new-pass1', 'new-pass2')

    expect(notify.error).toHaveBeenCalledWith('새 비밀번호가 일치하지 않습니다.')
    expect(authApi.changePassword).not.toHaveBeenCalled()
  })
})

describe('MyPage 사용자 정보', () => {
  it('로그인 상태의 사용자 정보를 보여 주고 다시 부르지 않는다', async () => {
    renderPage({ nickname: '머스캣' })

    expect(await screen.findByRole('heading', { name: '머스캣' })).toBeInTheDocument()
    expect(authApi.getCurrentUser).not.toHaveBeenCalled()
  })

  it('닉네임을 바꾸면 로그인 상태의 사용자 정보를 새로 받는다', async () => {
    const user = userEvent.setup()
    vi.mocked(authApi.changeNickname).mockResolvedValue({} as never)
    renderPage({})

    await changeNickname(user, '새이름')

    await waitFor(() => expect(refreshUserData).toHaveBeenCalledTimes(1))
    expect(authApi.changeNickname).toHaveBeenCalledWith({ nickname: '새이름' })
  })

  it('닉네임은 바뀌고 다시 받기만 실패하면 실패 알림을 띄우지 않는다', async () => {
    const user = userEvent.setup()
    vi.mocked(authApi.changeNickname).mockResolvedValue({} as never)
    refreshUserData.mockRejectedValue(new Error('down'))
    renderPage({})

    await changeNickname(user, '새이름')

    await waitFor(() => expect(notify.success).toHaveBeenCalledWith('닉네임이 변경되었습니다.'))
    expect(notify.error).not.toHaveBeenCalled()
  })

  it('로그인하지 않았으면 홈으로 보낸다', async () => {
    renderPage({}, { auth: { isAuthenticated: false, user: null } })

    expect(await screen.findByText('홈')).toBeInTheDocument()
  })

  it('로그인 확인 중이면 홈으로 보내지 않는다', () => {
    renderPage({}, { auth: { isAuthenticated: false, isLoading: true, user: null } })

    expect(screen.queryByText('홈')).toBeNull()
  })
})
