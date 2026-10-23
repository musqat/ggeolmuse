import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { useAuth } from '../../hooks/useAuth'
import SignupModal from './SignupModal'

vi.mock('../../hooks/useAuth', () => ({ useAuth: vi.fn() }))

let resendVerificationEmail: ReturnType<typeof vi.fn>

function renderModal(onSignup = vi.fn().mockResolvedValue(undefined)) {
  const onClose = vi.fn()
  const onSignupSuccess = vi.fn()
  render(
    <SignupModal isOpen onClose={onClose} onSwitchToLogin={vi.fn()} onSignup={onSignup} onSignupSuccess={onSignupSuccess} />
  )
  return { onClose, onSignup, onSignupSuccess }
}

// 칸을 모두 채우고 회원가입을 누름
async function signup(user: ReturnType<typeof userEvent.setup>, password: string, confirm = password) {
  await user.type(screen.getByLabelText('닉네임'), '새이름')
  await user.type(screen.getByLabelText('이메일'), 'new@example.local')
  await user.type(screen.getByLabelText('비밀번호'), password)
  await user.type(screen.getByLabelText('비밀번호 확인'), confirm)
  await user.click(screen.getByRole('button', { name: '회원가입' }))
}

beforeEach(() => {
  vi.clearAllMocks()
  resendVerificationEmail = vi.fn().mockResolvedValue(undefined)
  vi.mocked(useAuth).mockReturnValue({ resendVerificationEmail, loginWithGoogle: vi.fn() } as never)
})

describe('SignupModal', () => {
  it('비밀번호가 다르면 보내지 않고 알린다', async () => {
    const user = userEvent.setup()
    const { onSignup } = renderModal()

    await signup(user, 'abcd1234', 'abcd9999')

    expect(screen.getByText('비밀번호가 일치하지 않습니다.')).toBeInTheDocument()
    expect(onSignup).not.toHaveBeenCalled()
  })

  it('8자보다 짧으면 보내지 않고 알린다', async () => {
    const user = userEvent.setup()
    const { onSignup } = renderModal()

    await signup(user, 'abc12')

    expect(screen.getByText('비밀번호는 8자 이상이어야 합니다.')).toBeInTheDocument()
    expect(onSignup).not.toHaveBeenCalled()
  })

  it('가입하면 창을 닫고 가입한 이메일을 넘긴다', async () => {
    const user = userEvent.setup()
    const { onClose, onSignup, onSignupSuccess } = renderModal()

    await signup(user, 'abcd1234')

    expect(onSignup).toHaveBeenCalledWith('new@example.local', 'abcd1234', '새이름')
    expect(onClose).toHaveBeenCalledTimes(1)
    expect(onSignupSuccess).toHaveBeenCalledWith('new@example.local')
  })

  it('409 면 이미 가입된 이메일이라고 보인다', async () => {
    const user = userEvent.setup()
    renderModal(vi.fn().mockRejectedValue(new AxiosError('fail', 'ERR', undefined, undefined, {
      status: 409, data: {}, statusText: '', headers: {}, config: {},
    } as AxiosResponse)))

    await signup(user, 'abcd1234')

    expect(await screen.findByText('이미 가입된 이메일입니다.')).toBeInTheDocument()
  })

  it('인증 메일을 다시 보내면 보냈다고 보인다', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.click(screen.getByRole('button', { name: '이메일을 못 받으셨나요?' }))
    await user.type(screen.getByPlaceholderText('이메일을 입력하세요'), 'new@example.local')
    await user.click(screen.getByRole('button', { name: '인증 이메일 재전송' }))

    expect(resendVerificationEmail).toHaveBeenCalledWith('new@example.local')
    expect(await screen.findByText('인증이 필요한 계정이면 인증 메일을 다시 보냈습니다. 메일함을 확인해주세요.')).toBeInTheDocument()
  })
})
