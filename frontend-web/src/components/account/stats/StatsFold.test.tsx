import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StatsFold, StatsFoldState } from './StatsFold'

describe('StatsFold', () => {
  it('바깥 상태가 없으면 닫힌 채 시작해 누를 때마다 열고 닫으며 제목 줄 오른쪽에 요약을 둔다', async () => {
    const user = userEvent.setup()
    render(<StatsFold summary="종목 3개">표</StatsFold>)

    const fold = screen.getByRole('button', { name: /자세히 펼치기/ })
    expect(fold).toHaveAttribute('aria-expanded', 'false')
    expect(fold).toHaveTextContent('자세히 펼치기종목 3개')
    expect(screen.queryByText('표')).toBeNull()

    await user.click(fold)
    expect(fold).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('표')).toHaveAttribute('id', fold.getAttribute('aria-controls'))

    await user.click(fold)
    expect(fold).toHaveAttribute('aria-expanded', 'false')
  })

  it('aria-controls 는 열려 있어 내용을 그렸을 때만 둔다', async () => {
    const user = userEvent.setup()
    render(<StatsFold>표</StatsFold>)

    const fold = screen.getByRole('button', { name: /자세히 펼치기/ })
    expect(fold).not.toHaveAttribute('aria-controls')

    await user.click(fold)
    expect(fold).toHaveAttribute('aria-controls')
    await user.click(fold)
    expect(fold).not.toHaveAttribute('aria-controls')
  })

  it('바깥 상태가 있으면 그 열림으로 그리고 누르면 반대 값을 바깥에 알린다', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()
    const { rerender } = render(
      <StatsFoldState open onOpenChange={onOpenChange}>
        <StatsFold>표</StatsFold>
      </StatsFoldState>
    )

    const fold = screen.getByRole('button', { name: /자세히 펼치기/ })
    expect(fold).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('표')).toBeInTheDocument()

    await user.click(fold)
    expect(onOpenChange).toHaveBeenLastCalledWith(false)
    expect(fold).toHaveAttribute('aria-expanded', 'true')

    rerender(
      <StatsFoldState open={false} onOpenChange={onOpenChange}>
        <StatsFold>표</StatsFold>
      </StatsFoldState>
    )
    expect(fold).toHaveAttribute('aria-expanded', 'false')
    await user.click(fold)
    expect(onOpenChange).toHaveBeenLastCalledWith(true)
  })
})
