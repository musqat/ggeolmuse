import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { useAuth } from '../../hooks/useAuth'
import LoginModal from './LoginModal'

vi.mock('../../hooks/useAuth', () => ({ useAuth: vi.fn() }))

const httpError = (status: number, data: object = {}) =>
  new AxiosError('fail', 'ERR', undefined, undefined, { status, data, statusText: '', headers: {}, config: {} } as AxiosResponse)

let forgotPassword: ReturnType<typeof vi.fn>
let loginWithGoogle: ReturnType<typeof vi.fn>
let resendVerificationEmail: ReturnType<typeof vi.fn>

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
  resendVerificationEmail = vi.fn().mockResolvedValue(undefined)
  vi.mocked(useAuth).mockReturnValue({ forgotPassword, loginWithGoogle, resendVerificationEmail } as never)
})
afterEach(() => vi.restoreAllMocks())

describe('LoginModal', () => {
  it('로그인 대화 상자로 열려 이메일 칸에 포커스, Esc 로 닫는다', async () => {
    const { onClose } = renderModal()

    expect(screen.getByRole('dialog', { name: '로그인' })).toHaveAttribute('aria-modal', 'true')
    expect(screen.getByLabelText('이메일')).toHaveFocus()
    await userEvent.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalledOnce()
  })

  it('이메일과 비밀번호로 로그인하고 창을 닫는다', async () => {
    const user = userEvent.setup()
    const { onClose, onLogin } = renderModal()

    await login(user)

    expect(onLogin).toHaveBeenCalledWith('u@example.local', 'abcd1234')
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it.each([
    ['INVALID_CREDENTIALS', 401, '이메일 또는 비밀번호가 올바르지 않습니다.'],
    ['EMAIL_NOT_VERIFIED', 400, '이메일 인증이 완료되지 않았습니다. 받은 메일의 인증 링크를 눌러 주세요.'],
    ['ACCOUNT_DISABLED', 403, '비활성화된 계정입니다. 관리자에게 문의해 주세요.'],
  ])('오류 코드 %s(%i) 면 그 문구를 보이고 창을 닫지 않는다', async (errorCode, status, text) => {
    const user = userEvent.setup()
    const { onClose } = renderModal(vi.fn().mockRejectedValue(httpError(status, { errorCode, detail: '서버 문구' })))

    await login(user)

    expect(await screen.findByText(text)).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('오류 코드가 없으면 401 · 403 · 429 를 상태 코드로 고른다', async () => {
    const user = userEvent.setup()
    const onLogin = vi.fn()
      .mockRejectedValueOnce(httpError(401))
      .mockRejectedValueOnce(httpError(403))
      .mockRejectedValueOnce(httpError(429))
    renderModal(onLogin)

    await login(user)
    expect(await screen.findByText('이메일 또는 비밀번호가 올바르지 않습니다.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '로그인' }))
    expect(await screen.findByText('비활성화된 계정입니다. 관리자에게 문의해 주세요.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '로그인' }))
    expect(await screen.findByText('로그인 시도가 많습니다. 잠시 뒤 다시 시도해 주세요.')).toBeInTheDocument()
  })

  it('인증 전 계정이면 인증 메일 다시 받기로 입력한 이메일에 다시 보내고 보냈다고 보인다', async () => {
    const user = userEvent.setup()
    renderModal(vi.fn().mockRejectedValue(httpError(400, { errorCode: 'EMAIL_NOT_VERIFIED' })))

    await login(user)
    await user.click(await screen.findByRole('button', { name: '인증 메일 다시 받기' }))

    expect(resendVerificationEmail).toHaveBeenCalledWith('u@example.local')
    expect(await screen.findByRole('status')).toHaveTextContent('u@example.local 로 인증 메일을 다시 보냈습니다.')
    expect(screen.queryByRole('button', { name: '인증 메일 다시 받기' })).not.toBeInTheDocument()
  })

  it('다시 보내기가 실패하면 서버 문구를 보이고 다시 누를 수 있다', async () => {
    const user = userEvent.setup()
    resendVerificationEmail.mockRejectedValueOnce(httpError(429, { detail: '잠시 뒤 다시 시도해 주세요.' }))
    renderModal(vi.fn().mockRejectedValue(httpError(400, { errorCode: 'EMAIL_NOT_VERIFIED' })))

    await login(user)
    await user.click(await screen.findByRole('button', { name: '인증 메일 다시 받기' }))

    expect(await screen.findByText('잠시 뒤 다시 시도해 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '인증 메일 다시 받기' })).toBeEnabled()
  })

  it('비밀번호가 틀린 오류에는 다시 받기가 없고, 인증 전 오류 뒤 이메일을 고치면 사라진다', async () => {
    const user = userEvent.setup()
    const onLogin = vi.fn()
      .mockRejectedValueOnce(httpError(401, { errorCode: 'INVALID_CREDENTIALS' }))
      .mockRejectedValueOnce(httpError(400, { errorCode: 'EMAIL_NOT_VERIFIED' }))
    renderModal(onLogin)

    await login(user)
    await screen.findByText('이메일 또는 비밀번호가 올바르지 않습니다.')
    expect(screen.queryByRole('button', { name: '인증 메일 다시 받기' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '로그인' }))
    await screen.findByRole('button', { name: '인증 메일 다시 받기' })
    await user.type(screen.getByLabelText('이메일'), 'x')
    expect(screen.queryByRole('button', { name: '인증 메일 다시 받기' })).not.toBeInTheDocument()
  })

  it('코드도 상태도 모르는 오류는 서버 문구를 보인다', async () => {
    const user = userEvent.setup()
    renderModal(vi.fn().mockRejectedValue(httpError(400, { errorCode: 'INVALID_INPUT', detail: '이메일 형식이 아닙니다.' })))

    await login(user)

    expect(await screen.findByText('이메일 형식이 아닙니다.')).toBeInTheDocument()
  })

  it('비밀번호 보기를 누르면 글자로 보인다', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.click(screen.getByRole('button', { name: '비밀번호 보기' }))

    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: '비밀번호 숨기기' })).toBeInTheDocument()
  })

  it('구글 로그인은 로그인을 시작한 화면으로 돌아오게 지금 경로를 넘긴다', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', '/trading?symbol=AAPL')
    try {
      renderModal()

      await user.click(screen.getByRole('button', { name: '구글로 로그인' }))

      expect(loginWithGoogle).toHaveBeenCalledWith({ returnTo: '/trading?symbol=AAPL' })
    } finally {
      window.history.pushState({}, '', '/')
    }
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
