import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import TradeDatePicker from './TradeDatePicker'
import type { CandlestickChartData } from '@/types/ohlc'

// 기대값은 TZ=Asia/Seoul 전제 (vitest.config.ts 에서 고정)
const candle = (time: string): CandlestickChartData => ({ time, open: 1, high: 1, low: 1, close: 1 })
const chartData = [candle('2026-01-02'), candle('2026-03-31')]

function renderPicker(onTradeDateChange = vi.fn(), onFindClosestPastDate = vi.fn()) {
  render(
    <TradeDatePicker
      tradeDate="2026-03-10"
      onTradeDateChange={onTradeDateChange}
      chartData={chartData}
      selectedDateOHLC={null}
      onFindClosestPastDate={onFindClosestPastDate}
    />
  )
  return { onTradeDateChange, onFindClosestPastDate }
}

describe('TradeDatePicker', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-04-01T05:00:00+09:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('1달전은 마지막 캔들에서 한 달 전을 찾는다. 3/31 이면 2/28 이다', async () => {
    const { onFindClosestPastDate } = renderPicker()

    await userEvent.click(screen.getByRole('button', { name: '1달전' }))

    expect(onFindClosestPastDate).toHaveBeenCalledWith('2026-02-28')
  })

  it('1주전은 마지막 캔들에서 7일 전을 찾고, 찾은 거래일로 바꾼다', async () => {
    const onFindClosestPastDate = vi.fn(() => candle('2026-03-23'))
    const { onTradeDateChange } = renderPicker(vi.fn(), onFindClosestPastDate)

    await userEvent.click(screen.getByRole('button', { name: '1주전' }))

    expect(onFindClosestPastDate).toHaveBeenCalledWith('2026-03-24')
    expect(onTradeDateChange).toHaveBeenCalledWith('2026-03-23')
  })

  it('달력에서 고른 날을 그대로 넘긴다. KST 새벽에도 하루 밀리지 않는다', async () => {
    const { onTradeDateChange } = renderPicker()

    await userEvent.click(screen.getByTestId('trade-date'))
    const day20 = within(screen.getByRole('dialog'))
      .getAllByRole('button')
      .find((b) => b.textContent === '20')!
    await userEvent.click(day20)

    expect(onTradeDateChange).toHaveBeenCalledWith('2026-03-20')
  })
})
