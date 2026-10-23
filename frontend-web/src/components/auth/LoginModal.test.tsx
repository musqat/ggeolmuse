import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { useAuth } from '../../hooks/useAuth'
import LoginModal from './LoginModal'

vi.mock('../../hooks/useAuth', () => ({ useAuth: vi.fn() }))

const httpError = (status: number) =>
  new AxiosError('fail', 'ERR', undefined, undefined, { status, data: {}, statusText: '', headers: {}, config: {} } as AxiosResponse)

let forgotPassword: ReturnType<typeof vi.fn>
let loginWithGoogle: ReturnType<typeof vi.fn>

function renderModal(onLogin = vi.fn().mockResolvedValue(undefined)) {
  const onClose = vi.fn()
  render(<LoginModal isOpen onClose={onClose} onSwitchToSignup={vi.fn()} onLogin={onLogin} />)
  return { onClose, onLogin }
}

// 이메일 · 비밀번호를 넣고 로그인을 누름
async function login(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('이메일'), 'u@example.local')
  await user.type(screen.getByLabelText('비밀번호'), 'abcd1234')
  await user.click(screen.getByRole('button', { name: '로그인' }))
}

beforeEach(() => {
  vi.clearAllMocks()
  forgotPassword = vi.fn().mockResolvedValue(undefined)
  loginWithGoogle = vi.fn().mockResolvedValue(undefined)
  vi.mocked(useAuth).mockReturnValue({ forgotPassword, loginWithGoogle } as never)
})
afterEach(() => vi.restoreAllMocks())

describe('LoginModal', () => {
  it('이메일과 비밀번호로 로그인하고 창을 닫는다', async () => {
    const user = userEvent.setup()
    const { onClose, onLogin } = renderModal()

    await login(user)

    expect(onLogin).toHaveBeenCalledWith('u@example.local', 'abcd1234')
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('401 이면 이메일 또는 비밀번호가 올바르지 않다고 보인다', async () => {
    const user = userEvent.setup()
    const { onClose } = renderModal(vi.fn().mockRejectedValue(httpError(401)))

    await login(user)

    expect(await screen.findByText('이메일 또는 비밀번호가 올바르지 않습니다.')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('403 이면 이메일 인증이 필요하다고 보인다', async () => {
    const user = userEvent.setup()
    renderModal(vi.fn().mockRejectedValue(httpError(403)))

    await login(user)

    expect(await screen.findByText('이메일 인증이 완료되지 않았습니다. 이메일을 확인해주세요.')).toBeInTheDocument()
  })

  it('비밀번호 보기를 누르면 글자로 보인다', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.click(screen.getByRole('button', { name: '비밀번호 보기' }))

    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: '비밀번호 숨기기' })).toBeInTheDocument()
  })

  it('구글 로그인이 실패하면 알린다', async () => {
    const user = userEvent.setup()
    vi.spyOn(console, 'error').mockImplementation(() => {})
    loginWithGoogle.mockRejectedValue(new Error('down'))
    renderModal()

    await user.click(screen.getByRole('button', { name: '구글로 로그인' }))

    expect(await screen.findByText('Google 로그인에 실패했습니다. 다시 시도해주세요.')).toBeInTheDocument()
  })

  it('비밀번호 찾기에서 재설정 메일을 보내면 접수 문구를 보인다', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.click(screen.getByRole('button', { name: '비밀번호를 잊으셨나요?' }))
    await user.type(screen.getByLabelText('이메일'), 'u@example.local')
    await user.click(screen.getByRole('button', { name: '재설정 이메일 보내기' }))

    expect(forgotPassword).toHaveBeenCalledWith('u@example.local')
    expect(await screen.findByText('요청을 접수했습니다')).toBeInTheDocument()
  })
})
