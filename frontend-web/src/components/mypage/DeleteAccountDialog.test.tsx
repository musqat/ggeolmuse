import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { DeleteAccountDialog } from './DeleteAccountDialog'

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: () => ({ logout: vi.fn(), loginWithGoogle: vi.fn() }) }))
vi.mock('../../services/api', () => ({ authApi: { deleteAccount: vi.fn() } }))

describe('DeleteAccountDialog', () => {
  it('탈퇴하면 지워지는 것(계좌 · 거래 · 백테스트 기록)과 AI 대화가 사라지는 때를 알린다', () => {
    render(<DeleteAccountDialog hasPassword onClose={vi.fn()} />)

    const notices = within(screen.getByRole('list')).getAllByRole('listitem').map((item) => item.textContent)
    expect(notices).toEqual([
      '모든 계좌 정보가 삭제됩니다',
      '거래 내역이 모두 삭제됩니다',
      '백테스트 기록이 모두 삭제됩니다',
      'AI 대화는 5시간 안에 사라집니다',
      '이 작업은 되돌릴 수 없습니다',
    ])
  })
})
