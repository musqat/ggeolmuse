import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useAuth } from '../../hooks/useAuth'
import { authApi } from '../../services/api'
import { notify } from '../common/notify'
import { NicknameRequiredPrompt } from './NicknameRequiredPrompt'

vi.mock('../../hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('../../services/api', () => ({ authApi: { changeNickname: vi.fn() } }))

const REASON = '이미 쓰는 닉네임이라 정하지 못했어요. 다른 닉네임을 정해 주세요.'

let refreshUserData: ReturnType<typeof vi.fn>

function loginAs(nickname: string | null, isAuthenticated = true) {
  vi.mocked(useAuth).mockReturnValue({
    isAuthenticated,
    user: isAuthenticated ? { id: 1, email: 'g@example.com', nickname } : null,
    refreshUserData,
  } as never)
}

beforeEach(() => {
  refreshUserData = vi.fn().mockResolvedValue(undefined)
  vi.mocked(authApi.changeNickname).mockResolvedValue({} as never)
  vi.spyOn(notify, 'error').mockImplementation(() => {})
  vi.spyOn(notify, 'success').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

describe('NicknameRequiredPrompt', () => {
  it('로그인했는데 닉네임이 비어 있으면 다른 닉네임을 정해 달라고 요청한다', () => {
    loginAs(null)
    render(<NicknameRequiredPrompt />)

    expect(screen.getByText('닉네임 정하기')).toBeInTheDocument()
    expect(screen.getByText(REASON)).toBeInTheDocument()
  })

  it('닉네임이 있거나 로그인하지 않았으면 요청하지 않는다', () => {
    loginAs('투자자')
    const { rerender } = render(<NicknameRequiredPrompt />)
    expect(screen.queryByText('닉네임 정하기')).toBeNull()

    loginAs(null, false)
    rerender(<NicknameRequiredPrompt />)
    expect(screen.queryByText('닉네임 정하기')).toBeNull()
  })

  it('정하면 보내고 사용자 정보를 새로 받는다', async () => {
    const user = userEvent.setup()
    loginAs(null)
    render(<NicknameRequiredPrompt />)

    await user.type(screen.getByLabelText('새 닉네임'), '새이름')
    await user.click(screen.getByRole('button', { name: '정하기' }))

    expect(authApi.changeNickname).toHaveBeenCalledWith({ nickname: '새이름' })
    expect(refreshUserData).toHaveBeenCalledTimes(1)
  })

  it('취소하면 이번에는 다시 요청하지 않는다', async () => {
    const user = userEvent.setup()
    loginAs(null)
    render(<NicknameRequiredPrompt />)

    await user.click(screen.getByRole('button', { name: '취소' }))

    expect(screen.queryByText('닉네임 정하기')).toBeNull()
  })
})
