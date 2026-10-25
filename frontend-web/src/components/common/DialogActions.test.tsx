import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DialogActions } from './DialogActions'

describe('DialogActions', () => {
  it('제출 버튼은 기본이 브랜드 색이고 tone 을 주면 그 바탕이다', () => {
    const { rerender } = render(<DialogActions onCancel={vi.fn()} onSubmit={vi.fn()} submitLabel="저장" />)
    expect(screen.getByRole('button', { name: '저장' })).toHaveClass('bg-brand')

    rerender(<DialogActions onCancel={vi.fn()} onSubmit={vi.fn()} submitLabel="삭제" tone="danger" />)
    expect(screen.getByRole('button', { name: '삭제' })).toHaveClass('bg-red-600')
  })

  it('막으면 제출 버튼만 막히고 두 버튼은 각자 부른다', async () => {
    const onCancel = vi.fn()
    const onSubmit = vi.fn()
    const { rerender } = render(
      <DialogActions onCancel={onCancel} onSubmit={onSubmit} submitLabel="저장" cancelLabel="닫기" disabled />
    )

    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: '닫기' }))
    expect(onCancel).toHaveBeenCalledTimes(1)

    rerender(<DialogActions onCancel={onCancel} onSubmit={onSubmit} submitLabel="저장" />)
    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    expect(onSubmit).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: '취소' })).toBeInTheDocument()
  })

  it('바깥 여백 같은 클래스를 줄에 더한다', () => {
    render(<DialogActions onCancel={vi.fn()} onSubmit={vi.fn()} submitLabel="저장" className="mt-6" />)

    expect(screen.getByRole('button', { name: '저장' }).parentElement).toHaveClass('flex', 'mt-6')
  })
})
