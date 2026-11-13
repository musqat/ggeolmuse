import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { IndicatorSheet } from './IndicatorSheet'
import { DEFAULT_INDICATORS } from './indicators'

describe('IndicatorSheet', () => {
  it('켜진 지표 수와 이름을 버튼 줄에 보인다', () => {
    render(<IndicatorSheet indicators={DEFAULT_INDICATORS} onToggle={vi.fn()} />)

    expect(screen.getByTestId('chart-indicator-button')).toHaveTextContent('지표2')
    expect(screen.getByText('MA 20 · 거래량 MA')).toBeInTheDocument()
  })

  it('버튼을 누르면 시트가 열리고, 줄을 누르면 그 지표를 바꾼다', async () => {
    const onToggle = vi.fn()
    render(<IndicatorSheet indicators={DEFAULT_INDICATORS} onToggle={onToggle} />)

    await userEvent.click(screen.getByTestId('chart-indicator-button'))
    const rsi = screen.getByRole('switch', { name: 'RSI (14)' })
    expect(rsi).toHaveAttribute('aria-checked', 'false')
    expect(screen.getByRole('switch', { name: 'MA 20' })).toHaveAttribute('aria-checked', 'true')

    await userEvent.click(rsi)
    expect(onToggle).toHaveBeenCalledWith('rsi')
    // 여러 개를 연달아 바꿀 수 있게 시트는 열린 채
    expect(screen.getByRole('dialog', { name: '지표 설정' })).toBeInTheDocument()
  })

  it('Esc · 배경 · 닫기 버튼으로 닫는다', async () => {
    render(<IndicatorSheet indicators={DEFAULT_INDICATORS} onToggle={vi.fn()} />)
    const open = () => userEvent.click(screen.getByTestId('chart-indicator-button'))

    await open()
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()

    await open()
    await userEvent.click(screen.getByTestId('chart-indicator-backdrop'))
    expect(screen.queryByRole('dialog')).toBeNull()

    await open()
    await userEvent.click(screen.getByRole('button', { name: '닫기' }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('열면 첫 스위치로 포커스, 바탕은 inert · 스크롤 잠금, 닫으면 지표 단추로 포커스', async () => {
    const user = userEvent.setup()
    const { container } = render(<IndicatorSheet indicators={DEFAULT_INDICATORS} onToggle={vi.fn()} />)

    await user.click(screen.getByTestId('chart-indicator-button'))
    expect(screen.getAllByRole('switch')[0]).toHaveFocus()
    expect(container).toHaveAttribute('inert')
    expect(document.body.style.overflow).toBe('hidden')
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(container).not.toHaveAttribute('inert')
    expect(document.body.style.overflow).toBe('')
    expect(screen.getByTestId('chart-indicator-button')).toHaveFocus()
  })
})
