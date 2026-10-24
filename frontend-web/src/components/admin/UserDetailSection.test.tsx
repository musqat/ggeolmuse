import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { UserDetail } from '@services/adminApi'
import UserDetailSection from './UserDetailSection'
import { notify } from '@/components/common/notify'

const USER: UserDetail = {
  userId: 7, email: 'u@example.local', username: '옛이름', role: 'USER', enabled: true, emailVerified: true,
  createdAt: '2026-01-05T12:00:00', updatedAt: '2026-01-05T12:00:00', lastLoginAt: null, accounts: [],
}

function renderSection() {
  const handlers = {
    onUpdateRole: vi.fn(), onUpdateEnabled: vi.fn(), onUpdateNickname: vi.fn(),
    onUpdatePassword: vi.fn(), onVerifyEmail: vi.fn(), onDeleteUser: vi.fn(),
  }
  render(<UserDetailSection user={USER} loading={false} {...handlers} />)
  return handlers
}

beforeEach(() => {
  vi.spyOn(notify, 'error').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

describe('UserDetailSection', () => {
  it('닉네임을 바꾸면 사용자 번호와 새 닉네임을 넘긴다', async () => {
    const user = userEvent.setup()
    const { onUpdateNickname } = renderSection()

    await user.click(screen.getByRole('button', { name: '변경' }))
    const input = screen.getByPlaceholderText('새 닉네임 입력')
    await user.clear(input)
    await user.type(input, '새이름')
    await user.click(screen.getByRole('button', { name: '변경하기' }))

    expect(onUpdateNickname).toHaveBeenCalledWith(7, '새이름')
    expect(screen.queryByRole('button', { name: '변경하기' })).toBeNull()
  })

  it('닉네임이 규칙에 안 맞으면 넘기지 않고 서버와 같은 규칙을 알린다', async () => {
    const user = userEvent.setup()
    const { onUpdateNickname } = renderSection()

    await user.click(screen.getByRole('button', { name: '변경' }))
    const input = screen.getByPlaceholderText('새 닉네임 입력')
    await user.clear(input)
    await user.type(input, '가 나')
    await user.click(screen.getByRole('button', { name: '변경하기' }))

    expect(notify.error).toHaveBeenCalledWith('닉네임은 2 ~ 20자의 영문, 숫자, 한글, _ 만 쓸 수 있습니다.')
    expect(onUpdateNickname).not.toHaveBeenCalled()
  })

  it.each([['8자보다 짧으면', 'abc12!'], ['특수문자가 없으면', 'abcd1234']])('비밀번호가 %s 넘기지 않고 서버와 같은 규칙을 알린다', async (_, password) => {
    const user = userEvent.setup()
    const { onUpdatePassword } = renderSection()

    await user.click(screen.getByRole('button', { name: '비밀번호 강제 변경' }))
    const [newInput, confirmInput] = document.querySelectorAll<HTMLInputElement>('input[type="password"]')
    await user.type(newInput, password)
    await user.type(confirmInput, password)
    await user.click(screen.getByRole('button', { name: '변경하기' }))

    expect(notify.error).toHaveBeenCalledWith('비밀번호는 8 ~ 100자이고 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다.')
    expect(onUpdatePassword).not.toHaveBeenCalled()
  })
})
