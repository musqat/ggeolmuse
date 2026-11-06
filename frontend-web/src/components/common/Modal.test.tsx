import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Modal } from './Modal'

describe('Modal', () => {
  it('제목을 이름으로 가진 대화 상자로 알린다', () => {
    render(<Modal isOpen onClose={vi.fn()} title="환전">내용</Modal>)

    const dialog = screen.getByRole('dialog', { name: '환전' })
    expect(dialog).toHaveTextContent('내용')
  })

  it('바깥 알림을 가리는 aria-modal 은 두지 않는다', () => {
    render(<Modal isOpen onClose={vi.fn()} title="환전">내용</Modal>)

    expect(screen.getByRole('dialog', { name: '환전' })).not.toHaveAttribute('aria-modal')
  })

  it('닫혀 있으면 그리지 않는다', () => {
    render(<Modal isOpen={false} onClose={vi.fn()} title="환전">내용</Modal>)

    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('닫기 버튼을 누르면 onClose 를 부른다', async () => {
    const onClose = vi.fn()
    render(<Modal isOpen onClose={onClose} title="환전">내용</Modal>)

    await userEvent.click(screen.getByRole('button', { name: '닫기' }))

    expect(onClose).toHaveBeenCalledOnce()
  })
})
