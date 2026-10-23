import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import AdminLayout from './AdminLayout'

vi.mock('../../hooks/useAuth', () => ({ useAuth: vi.fn() }))

describe('AdminLayout', () => {
  it('관리자 이름 앞을 띄어 쓴다', () => {
    vi.mocked(useAuth).mockReturnValue({ user: { nickname: '운영자' }, logout: vi.fn() } as never)
    render(
      <MemoryRouter>
        <AdminLayout />
      </MemoryRouter>
    )

    expect(screen.getByText('관리자:').parentElement?.textContent).toBe('관리자: 운영자')
  })
})
