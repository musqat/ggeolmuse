import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import DepositModal from './DepositModal'

function renderModal(onSubmit = vi.fn().mockResolvedValue(undefined)) {
  const onClose = vi.fn()
  render(<DepositModal isOpen onClose={onClose} onSubmit={onSubmit} />)
  return { onClose, onSubmit }
}

const amountInput = () => screen.getByPlaceholderText('입금할 금액을 입력하세요')

describe('DepositModal', () => {
  it('빠른 금액은 누를 때마다 더한다', async () => {
    renderModal()

    await userEvent.click(screen.getByRole('button', { name: '+₩10만' }))
    await userEvent.click(screen.getByRole('button', { name: '+₩10만' }))

    expect(amountInput()).toHaveValue(200000)
  })

  it('입금하면 금액을 넘기고 닫는다', async () => {
    const { onClose, onSubmit } = renderModal()

    await userEvent.type(amountInput(), '50000')
    await userEvent.click(screen.getByRole('button', { name: '입금하기' }))

    expect(onSubmit).toHaveBeenCalledWith(50000)
    expect(onClose).toHaveBeenCalled()
  })

  it('입금이 실패하면 창과 금액을 그대로 둔다', async () => {
    const { onClose, onSubmit } = renderModal(vi.fn().mockRejectedValue(new Error('down')))

    await userEvent.type(amountInput(), '50000')
    await userEvent.click(screen.getByRole('button', { name: '입금하기' }))

    expect(onSubmit).toHaveBeenCalledWith(50000)
    expect(screen.getByRole('heading', { name: 'KRW 입금' })).toBeInTheDocument()
    expect(amountInput()).toHaveValue(50000)
    expect(onClose).not.toHaveBeenCalled()
  })
})
