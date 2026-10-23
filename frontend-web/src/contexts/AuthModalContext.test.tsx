import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useAuth } from '../hooks/useAuth'
import { useAuthModal } from '../hooks/useAuthModal'
import { AuthModalProvider } from './AuthModalContext'

vi.mock('../hooks/useAuth', () => ({ useAuth: vi.fn() }))

let signup: ReturnType<typeof vi.fn>

// 로그인 · 가입 창을 여는 버튼 둘
function Openers() {
  const { openLogin, openSignup } = useAuthModal()
  return (
    <>
      <button onClick={openLogin}>로그인 열기</button>
      <button onClick={openSignup}>가입 열기</button>
    </>
  )
}

const renderProvider = () =>
  render(
    <AuthModalProvider>
      <Openers />
    </AuthModalProvider>
  )

beforeEach(() => {
  vi.clearAllMocks()
  signup = vi.fn().mockResolvedValue(undefined)
  vi.mocked(useAuth).mockReturnValue({
    login: vi.fn(), signup, forgotPassword: vi.fn(), loginWithGoogle: vi.fn(), resendVerificationEmail: vi.fn(),
  } as never)
})

describe('AuthModalProvider', () => {
  it('로그인 창에서 회원가입을 누르면 가입 창으로 넘어간다', async () => {
    const user = userEvent.setup()
    renderProvider()

    await user.click(screen.getByRole('button', { name: '로그인 열기' }))
    await user.click(screen.getByRole('button', { name: '회원가입' }))

    expect(await screen.findByLabelText('닉네임')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '로그인' })).toBeNull()
  })

  it('가입하면 완료 창을 연다', async () => {
    const user = userEvent.setup()
    renderProvider()

    await user.click(screen.getByRole('button', { name: '가입 열기' }))
    await user.type(screen.getByLabelText('닉네임'), '새이름')
    await user.type(screen.getByLabelText('이메일'), 'new@example.local')
    await user.type(screen.getByLabelText('비밀번호'), 'abcd123!')
    await user.type(screen.getByLabelText('비밀번호 확인'), 'abcd123!')
    await user.click(screen.getByRole('button', { name: '회원가입' }))

    expect(signup).toHaveBeenCalledWith('new@example.local', 'abcd123!', '새이름')
    expect(await screen.findByText('회원가입 완료')).toBeInTheDocument()
  })

  it('로그인 · 가입 창 아래 문장은 띄어 쓴다', async () => {
    const user = userEvent.setup()
    renderProvider()

    await user.click(screen.getByRole('button', { name: '로그인 열기' }))
    expect(screen.getByRole('button', { name: '회원가입' }).parentElement?.textContent).toBe('계정이 없으신가요? 회원가입')

    await user.click(screen.getByRole('button', { name: '회원가입' }))
    expect((await screen.findByRole('button', { name: '로그인' })).parentElement?.textContent).toBe('이미 계정이 있으신가요? 로그인')
  })
})
