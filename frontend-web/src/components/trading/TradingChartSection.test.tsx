import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import TradingChartSection from './TradingChartSection'
import type { Timeframe } from '@/utils/dateUtils'

// jsdom 에는 캔버스가 없다
vi.mock('@/components/charts/trading/CandlestickChart', () => ({
  default: () => <div data-testid="candles" />,
}))

function renderSection(timeframe: Timeframe, start = '', end = '') {
  const onCustomStartDateChange = vi.fn()
  const onCustomEndDateChange = vi.fn()
  render(
    <TradingChartSection
      chartData={[]}
      chartLoading={false}
      timeframe={timeframe}
      onTimeframeChange={vi.fn()}
      customStartDate={start}
      customEndDate={end}
      onCustomStartDateChange={onCustomStartDateChange}
      onCustomEndDateChange={onCustomEndDateChange}
    />
  )
  return { onCustomStartDateChange, onCustomEndDateChange }
}

// 기대값은 TZ=Asia/Seoul 전제 (vitest.config.ts 에서 고정)
describe('TradingChartSection 직접설정', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-03-31T05:00:00+09:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('고른 날을 그대로 넘긴다. KST 새벽에도 하루 밀리지 않는다', async () => {
    const { onCustomEndDateChange } = renderSection('직접설정', '2026-03-02')
    expect(screen.getByTestId('trade-chart-start')).toHaveTextContent('2026. 03. 02.')

    await userEvent.click(screen.getByTestId('trade-chart-end'))
    const day20 = within(screen.getByRole('dialog'))
      .getAllByRole('button')
      .find((b) => b.textContent === '20')!
    await userEvent.click(day20)

    expect(onCustomEndDateChange).toHaveBeenCalledWith('2026-03-20')
  })

  it('두 날짜를 다 고르기 전에는 안내를 보인다', () => {
    renderSection('직접설정', '2026-03-02')

    expect(screen.getByText('시작일과 종료일을 고르세요')).toBeInTheDocument()
  })

  it('직접설정이 아니면 날짜 칸이 없다', () => {
    renderSection('1년')

    expect(screen.queryByTestId('trade-chart-start')).toBeNull()
    expect(screen.getByText('차트 데이터가 없습니다')).toBeInTheDocument()
  })
})
