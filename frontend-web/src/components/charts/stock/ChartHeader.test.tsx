import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ChartHeader } from './ChartHeader'

const LAST = { time: '2025-06-30', open: 100, high: 101, low: 99, close: 100, volume: 1000 }

const renderHeader = (change: number, changePercent: number) =>
  render(
    <ChartHeader symbol="AAPL" companyName="Apple Inc." summary={{ last: LAST, change, changePercent }} onSearch={vi.fn()} onAiAnalysis={vi.fn()} />
  )

describe('ChartHeader', () => {
  it('반올림해 0 이 되는 등락은 부호 · 손실 색 · 내림 화살표 없음', () => {
    const { container } = renderHeader(-0.004, -0.004)

    const change = screen.getByText('$0.00 (0.00%)')
    expect(change.parentElement).not.toHaveClass('text-loss')
    expect(container.querySelector('.lucide-trending-down')).toBeNull()
  })

  it('내린 등락은 손실 색과 내림 화살표', () => {
    const { container } = renderHeader(-1.5, -1.48)

    expect(screen.getByText('-$1.50 (-1.48%)').parentElement).toHaveClass('text-loss')
    expect(container.querySelector('.lucide-trending-down')).not.toBeNull()
  })
})
