import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import UserListSection from './UserListSection'

const USERS = [
  { userId: 1, email: 'a@example.local', username: '가나', role: 'USER', enabled: true, emailVerified: true, createdAt: '2026-10-01', lastLoginAt: null },
  { userId: 2, email: 'b@example.local', username: '다라', role: 'ADMIN', enabled: false, emailVerified: false, createdAt: '2026-10-02', lastLoginAt: null },
] as const

function renderList(onSelectUser = vi.fn()) {
  render(
    <UserListSection
      users={[...USERS]}
      selectedUserId={1}
      page={0}
      totalPages={1}
      loading={false}
      onSelectUser={onSelectUser}
      onRefresh={vi.fn()}
      onPageChange={vi.fn()}
    />
  )
  return onSelectUser
}

describe('UserListSection', () => {
  it('사용자 줄은 단추이고 고른 줄은 눌림으로 알리며, 누르면 그 사용자를 고른다', async () => {
    const onSelectUser = renderList()

    expect(screen.getByRole('button', { name: /가나/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /다라/ })).toHaveAttribute('aria-pressed', 'false')
    await userEvent.click(screen.getByRole('button', { name: /다라/ }))

    expect(onSelectUser).toHaveBeenCalledWith(2)
  })
})
