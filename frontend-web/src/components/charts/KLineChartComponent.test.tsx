import { describe, it, expect, vi, beforeAll } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import KLineChartComponent from './KLineChartComponent'

// jsdom 에는 캔버스가 없어 차트 본체를 가짜로 바꾼다
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
