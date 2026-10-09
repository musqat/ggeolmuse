import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BacktestModeTabs } from './BacktestModeTabs'

const tabs = [
  { id: 'simple' as const, label: '단순' },
  { id: 'history' as const, label: '히스토리', locked: true },
]

describe('BacktestModeTabs', () => {
  it('누른 탭의 id 를 넘긴다', async () => {
    const onChange = vi.fn()
    render(<BacktestModeTabs tabs={tabs} active="simple" onChange={onChange} />)

    await userEvent.click(screen.getByRole('button', { name: '히스토리' }))

    expect(onChange).toHaveBeenCalledWith('history')
  })

  it('고른 탭만 강조하고 잠긴 탭에는 아이콘을 붙인다', () => {
    render(<BacktestModeTabs tabs={tabs} active="simple" onChange={vi.fn()} />)

    expect(screen.getByRole('button', { name: '단순' }).className).toContain('border-brand')
    const history = screen.getByRole('button', { name: '히스토리' })
    expect(history.className).not.toContain('border-brand')
    expect(history.querySelector('svg')).not.toBeNull()
  })
})
