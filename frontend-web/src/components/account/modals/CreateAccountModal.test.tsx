import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import CreateAccountModal from './CreateAccountModal'

afterEach(() => {
  vi.restoreAllMocks()
})

function renderModal() {
  const onSubmit = vi.fn().mockResolvedValue(undefined)
  render(<CreateAccountModal isOpen onClose={vi.fn()} onSubmit={onSubmit} />)
  return onSubmit
}

describe('CreateAccountModal', () => {
  it('슬리피지 기본값 0.1% 를 채워 두고 그대로 제출한다', async () => {
    const onSubmit = renderModal()

    await userEvent.type(screen.getByPlaceholderText('예: 주식 투자 계좌'), '테스트 계좌')
    expect(screen.getByLabelText('슬리피지 (%)')).toHaveValue(0.1)

    await userEvent.click(screen.getByRole('button', { name: '생성하기' }))

    expect(onSubmit).toHaveBeenCalledWith('테스트 계좌', 0.25, 0.1)
  })

  it('슬리피지율이 1% 를 넘으면 제출하지 않는다', async () => {
    const alert = vi.spyOn(window, 'alert').mockImplementation(() => {})
    const onSubmit = renderModal()

    await userEvent.type(screen.getByPlaceholderText('예: 주식 투자 계좌'), '테스트 계좌')
    const slippage = screen.getByLabelText('슬리피지 (%)')
    await userEvent.clear(slippage)
    await userEvent.type(slippage, '1.5')
    await userEvent.click(screen.getByRole('button', { name: '생성하기' }))

    expect(alert).toHaveBeenCalledWith('슬리피지율은 0 ~ 1% 사이여야 합니다. (입력값: 1.5%)')
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
