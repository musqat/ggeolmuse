import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import ProtectedRoute from './ProtectedRoute'

vi.mock('../hooks/useAuth', () => ({ useAuth: vi.fn() }))

function renderAdmin(auth: object) {
  vi.mocked(useAuth).mockReturnValue(auth as never)
  render(
    <MemoryRouter initialEntries={['/admin']}>
      <Routes>
        <Route path="/" element={<p>홈</p>} />
        <Route path="/unauthorized" element={<p>권한 없음</p>} />
        <Route
          path="/admin"
          element={
            <ProtectedRoute requireAdmin>
              <p>관리자 화면</p>
            </ProtectedRoute>
          }
        />
      </Routes>
    </MemoryRouter>
  )
}

describe('ProtectedRoute', () => {
  it('로그인 확인 중이면 로딩중을 보인다', () => {
    renderAdmin({ isLoading: true, isAuthenticated: false, user: null })

    expect(screen.getByText('로딩중...')).toBeInTheDocument()
    expect(screen.queryByText('관리자 화면')).toBeNull()
  })

  it('로그인 안 했으면 홈으로 보낸다', () => {
    renderAdmin({ isLoading: false, isAuthenticated: false, user: null })

    expect(screen.getByText('홈')).toBeInTheDocument()
  })

  it('관리자가 아니면 권한 없음으로 보낸다', () => {
    renderAdmin({ isLoading: false, isAuthenticated: true, user: { role: 'USER' } })

    expect(screen.getByText('권한 없음')).toBeInTheDocument()
  })

  it('관리자면 내용을 그린다', () => {
    renderAdmin({ isLoading: false, isAuthenticated: true, user: { role: 'ADMIN' } })

    expect(screen.getByText('관리자 화면')).toBeInTheDocument()
  })
})
