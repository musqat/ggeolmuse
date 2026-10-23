import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import DeleteAccountModal from './DeleteAccountModal'

describe('DeleteAccountModal', () => {
  it('삭제가 실패하면 창을 그대로 둔다', async () => {
    const onClose = vi.fn()
    const onConfirm = vi.fn().mockRejectedValue(new Error('down'))
    render(<DeleteAccountModal isOpen onClose={onClose} onConfirm={onConfirm} />)

    await userEvent.click(screen.getByRole('button', { name: '삭제하기' }))

    expect(onConfirm).toHaveBeenCalled()
    expect(screen.getByText('계좌 삭제 확인')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })
})
