import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { useAiChat } from '../../hooks/useAiChat'
import { AuthModalProvider } from '../../contexts/AuthModalContext'
import AiChatButton from './AiChatButton'

vi.mock('../../hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('../../hooks/useAiChat', () => ({ useAiChat: vi.fn() }))
vi.mock('./AiChatModal', () => ({
  default: ({ onRequireLogin }: { onRequireLogin: () => void }) => <button onClick={onRequireLogin}>로그인 요구</button>,
}))

let closeChat: ReturnType<typeof vi.fn>

beforeEach(() => {
  vi.clearAllMocks()
  closeChat = vi.fn()
  vi.mocked(useAiChat).mockReturnValue({ openChat: vi.fn(), closeChat } as never)
  vi.mocked(useAuth).mockReturnValue({
    login: vi.fn(), signup: vi.fn(), forgotPassword: vi.fn(), loginWithGoogle: vi.fn(), resendVerificationEmail: vi.fn(),
  } as never)
})

describe('AiChatButton', () => {
  it('채팅이 로그인을 요구하면 로그인 창을 열고 회원가입으로 넘어갈 수 있다', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <AuthModalProvider>
          <AiChatButton />
        </AuthModalProvider>
      </MemoryRouter>
    )

    await user.click(screen.getByRole('button', { name: '로그인 요구' }))
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '회원가입' }))

    expect(await screen.findByLabelText('닉네임')).toBeInTheDocument()
    expect(closeChat).toHaveBeenCalled()
  })
})
