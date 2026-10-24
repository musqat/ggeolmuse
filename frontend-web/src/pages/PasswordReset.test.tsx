import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AxiosError, type AxiosResponse } from 'axios'
import { useAuth } from '../hooks/useAuth'
import PasswordReset from './PasswordReset'

vi.mock('../hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('../hooks/useAuthModal', () => ({ useAuthModal: () => ({ openLogin, openSignup: vi.fn() }) }))
vi.mock('../components/common/Header', () => ({ default: () => <p>앱 헤더</p> }))

const openLogin = vi.fn()
let resetPassword: ReturnType<typeof vi.fn>

function renderPage(path = '/reset-password?token=t1') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/" element={<p>홈</p>} />
        <Route path="/reset-password" element={<PasswordReset />} />
      </Routes>
    </MemoryRouter>
  )
}

// 두 칸에 비밀번호를 넣고 보냄
async function submit(user: ReturnType<typeof userEvent.setup>, password: string, confirm = password) {
  await user.type(screen.getByPlaceholderText('영문, 숫자, 특수문자 포함 8자 이상'), password)
  await user.type(screen.getByPlaceholderText('비밀번호를 다시 입력하세요'), confirm)
  await user.click(screen.getByRole('button', { name: '비밀번호 재설정' }))
}

beforeEach(() => {
  vi.clearAllMocks()
  resetPassword = vi.fn().mockResolvedValue(undefined)
  vi.mocked(useAuth).mockReturnValue({ resetPassword, isLoading: false } as never)
})
afterEach(() => vi.useRealTimers())

describe('PasswordReset', () => {
  it('토큰이 없으면 홈으로 보낸다', async () => {
    renderPage('/reset-password')

    expect(await screen.findByText('홈')).toBeInTheDocument()
  })

  it('앱 헤더를 따로 그리지 않는다', () => {
    renderPage()

    expect(screen.queryByText('앱 헤더')).toBeNull()
  })

  it('서버와 같은 규칙이면 # 같은 특수문자로도 바꾼다', async () => {
    const user = userEvent.setup()
    renderPage()

    await submit(user, 'abcd123#')

    expect(resetPassword).toHaveBeenCalledWith('t1', 'abcd123#')
  })

  it('규칙에 안 맞으면 보내지 않고 알린다', async () => {
    const user = userEvent.setup()
    renderPage()

    await submit(user, 'abcdefgh')

    expect(screen.getByText('비밀번호는 8 ~ 100자이고 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다.')).toBeInTheDocument()
    expect(resetPassword).not.toHaveBeenCalled()
  })

  it('두 칸이 다르면 알린다', async () => {
    const user = userEvent.setup()
    renderPage()

    await submit(user, 'abcd123!', 'abcd123@')

    expect(screen.getByText('비밀번호가 일치하지 않습니다.')).toBeInTheDocument()
    expect(resetPassword).not.toHaveBeenCalled()
  })

  it('보내는 동안 버튼이 처리 중이다', async () => {
    const user = userEvent.setup()
    resetPassword.mockReturnValue(new Promise(() => {}))
    renderPage()

    await submit(user, 'abcd123!')

    expect(screen.getByRole('button', { name: '처리 중...' })).toBeDisabled()
  })

  it('바꾸면 완료 문구 뒤 3초 뒤 홈으로 가 로그인 창을 연다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    await submit(user, 'abcd123!')
    expect(await screen.findByText('비밀번호 재설정 완료')).toBeInTheDocument()
    act(() => { vi.advanceTimersByTime(3000) })

    await waitFor(() => expect(screen.getByText('홈')).toBeInTheDocument())
    expect(openLogin).toHaveBeenCalledTimes(1)
  })

  it('실패하면 서버 문구를 보인다', async () => {
    const user = userEvent.setup()
    resetPassword.mockRejectedValue(new AxiosError('fail', 'ERR', undefined, undefined, {
      status: 400, data: { detail: '만료된 링크입니다.' }, statusText: '', headers: {}, config: {},
    } as AxiosResponse))
    renderPage()

    await submit(user, 'abcd123!')

    expect(await screen.findByText('만료된 링크입니다.')).toBeInTheDocument()
  })

  it('화면 색은 테마 클래스다', () => {
    const { container } = renderPage()

    expect(container.querySelector('[style]')).toBeNull()
  })
})
