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

function renderSection(user: UserDetail | null = USER) {
  const handlers = {
    onUpdateRole: vi.fn(), onUpdateEnabled: vi.fn(), onUpdateNickname: vi.fn(),
    onUpdatePassword: vi.fn(), onVerifyEmail: vi.fn(), onDeleteUser: vi.fn(),
  }
  render(<UserDetailSection user={user} loading={false} {...handlers} />)
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

  it('비밀번호 칸이 비어 있으면 변경 버튼을 막는다', async () => {
    const user = userEvent.setup()
    renderSection()

    await user.click(screen.getByRole('button', { name: '비밀번호 강제 변경' }))

    expect(screen.getByRole('button', { name: '변경하기' })).toBeDisabled()
  })

  it('비밀번호가 확인과 다르면 넘기지 않고 알린다', async () => {
    const user = userEvent.setup()
    const { onUpdatePassword } = renderSection()

    await user.click(screen.getByRole('button', { name: '비밀번호 강제 변경' }))
    const [newInput, confirmInput] = document.querySelectorAll<HTMLInputElement>('input[type="password"]')
    await user.type(newInput, 'Newpass1!')
    await user.type(confirmInput, 'Newpass2!')
    await user.click(screen.getByRole('button', { name: '변경하기' }))

    expect(notify.error).toHaveBeenCalledWith('비밀번호가 일치하지 않습니다.')
    expect(onUpdatePassword).not.toHaveBeenCalled()
  })

  it('규칙에 맞는 비밀번호는 사용자 번호와 함께 넘기고 창을 닫는다', async () => {
    const user = userEvent.setup()
    const { onUpdatePassword } = renderSection()

    await user.click(screen.getByRole('button', { name: '비밀번호 강제 변경' }))
    const [newInput, confirmInput] = document.querySelectorAll<HTMLInputElement>('input[type="password"]')
    await user.type(newInput, 'Newpass1!')
    await user.type(confirmInput, 'Newpass1!')
    await user.click(screen.getByRole('button', { name: '변경하기' }))

    expect(onUpdatePassword).toHaveBeenCalledWith(7, 'Newpass1!')
    expect(screen.queryByRole('button', { name: '변경하기' })).toBeNull()
  })

  it('사용자를 고르지 않았으면 고르라고 안내한다', () => {
    renderSection(null)

    expect(screen.getByText('사용자를 선택하세요')).toBeInTheDocument()
  })

  it('지금 역할 · 상태 버튼은 막고 다른 쪽을 누르면 사용자 번호와 함께 넘긴다', async () => {
    const user = userEvent.setup()
    const { onUpdateRole, onUpdateEnabled } = renderSection()

    expect(screen.getByRole('button', { name: '일반 사용자' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '활성화' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: '관리자' }))
    await user.click(screen.getByRole('button', { name: '비활성화' }))

    expect(onUpdateRole).toHaveBeenCalledWith(7, 'ADMIN')
    expect(onUpdateEnabled).toHaveBeenCalledWith(7, false)
  })

  it('계좌가 있으면 계좌마다 이름과 원화 · 달러 잔액을 보인다', () => {
    renderSection({
      ...USER,
      accounts: [{ accountId: 1, accountName: '연습 계좌', balanceKrw: 1500000, balanceUsd: 123.4, createdAt: '2026-01-06T00:00:00' }],
    })

    expect(screen.getByText('연습 계좌')).toBeInTheDocument()
    expect(screen.getByText('₩1,500,000')).toBeInTheDocument()
    expect(screen.getByText('$123.40')).toBeInTheDocument()
  })

  it.each([
    [true, 1],
    [false, 0],
  ])('미인증 사용자의 강제 인증 · 강제 탈퇴는 확인 창에서 %s 를 고르면 %i 번 넘긴다', async (answer, times) => {
    vi.spyOn(notify, 'confirm').mockResolvedValue(answer)
    const user = userEvent.setup()
    const { onVerifyEmail, onDeleteUser } = renderSection({ ...USER, emailVerified: false })

    await user.click(screen.getByRole('button', { name: '강제 인증' }))
    await user.click(screen.getByRole('button', { name: '사용자 강제 탈퇴' }))

    expect(onVerifyEmail).toHaveBeenCalledTimes(times)
    expect(onDeleteUser).toHaveBeenCalledTimes(times)
    if (times) expect(onDeleteUser).toHaveBeenCalledWith(7)
  })
})
