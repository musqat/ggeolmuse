import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LoginRequired } from './LoginRequired'

vi.mock('../../hooks/useAuthModal', () => ({ useAuthModal: () => ({ openLogin, openSignup: vi.fn() }) }))

const openLogin = vi.fn()

describe('LoginRequired', () => {
  it('설명과 로그인 버튼을 보이고 누르면 로그인 창을 연다', async () => {
    render(<LoginRequired description="거래내역을 확인하시려면 먼저 로그인해주세요" />)

    expect(screen.getByRole('heading', { name: '로그인이 필요한 서비스입니다' })).toBeInTheDocument()
    expect(screen.getByText('거래내역을 확인하시려면 먼저 로그인해주세요')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /로그인하기/ }))

    expect(openLogin).toHaveBeenCalledTimes(1)
  })
})
