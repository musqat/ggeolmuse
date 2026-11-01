import { describe, it, expect, vi, beforeAll } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import KLineChartComponent from './KLineChartComponent'

// 차트 본체 가짜(jsdom 에는 캔버스가 없음)
const chart = vi.hoisted(() => ({
  setStyles: vi.fn(),
  createIndicator: vi.fn(() => 'pane'),
  removeIndicator: vi.fn(),
  applyNewData: vi.fn(),
  resize: vi.fn(),
  createOverlay: vi.fn(),
  removeOverlay: vi.fn(),
}))

vi.mock('klinecharts', () => ({
  init: () => chart,
  dispose: vi.fn(),
  LineType: { Solid: 'solid', Dashed: 'dashed' },
  TooltipShowRule: { Always: 'always', FollowCross: 'follow_cross' },
}))

beforeAll(() => {
  globalThis.ResizeObserver = class {
    observe() {}
    disconnect() {}
    unobserve() {}
  } as unknown as typeof ResizeObserver
})

describe('KLineChartComponent', () => {
  it.each(['dark', 'light'])('%s 테마에서 거래량 막대는 캔들과 같은 오름 · 내림 색이다', (theme) => {
    document.documentElement.dataset.theme = theme
    chart.setStyles.mockClear()
    render(<KLineChartComponent data={[]} />)

    const styles = chart.setStyles.mock.calls[0][0] as {
      candle: { bar: { upColor: string; downColor: string } }
      indicator: { bars: { upColor: string; downColor: string }[] }
    }
    const [bars] = styles.indicator.bars
    expect(bars.upColor.startsWith(styles.candle.bar.upColor)).toBe(true)
    expect(bars.downColor.startsWith(styles.candle.bar.downColor)).toBe(true)
    delete document.documentElement.dataset.theme
  })

  it('RSI 를 켜면 라벨대로 14 하나로 그린다', async () => {
    render(<KLineChartComponent data={[]} showIndicatorPanel />)

    await userEvent.click(screen.getByRole('checkbox', { name: 'RSI (14)' }))

    expect(chart.createIndicator).toHaveBeenCalledWith(
      { name: 'RSI', calcParams: [14] },
      false,
      expect.objectContaining({ gap: { top: 32 } }),
    )
  })
})
