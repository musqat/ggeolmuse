import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ChartPeriodSelector } from './ChartPeriodSelector'

describe('ChartPeriodSelector', () => {
  it('직접설정 버튼은 하나이고, 누르면 custom 으로 바꾼다', async () => {
    const onPeriodChange = vi.fn()
    render(
      <ChartPeriodSelector
        chartPeriod="purchase"
        customStartDate=""
        onPeriodChange={onPeriodChange}
        onCustomDateChange={vi.fn()}
      />
    )

    expect(screen.getAllByRole('button', { name: '직접설정' })).toHaveLength(1)
    await userEvent.click(screen.getByRole('button', { name: '직접설정' }))

    expect(onPeriodChange).toHaveBeenCalledWith('custom')
  })

  it('custom 일 때만 시작일 칸을 보인다', () => {
    const { rerender } = render(
      <ChartPeriodSelector
        chartPeriod="1y"
        customStartDate=""
        onPeriodChange={vi.fn()}
        onCustomDateChange={vi.fn()}
      />
    )
    expect(screen.queryByTestId('chart-custom-start')).toBeNull()

    rerender(
      <ChartPeriodSelector
        chartPeriod="custom"
        customStartDate="2022-06-01"
        onPeriodChange={vi.fn()}
        onCustomDateChange={vi.fn()}
      />
    )
    expect(screen.getByTestId('chart-custom-start')).toHaveTextContent('2022. 06. 01.')
  })
})
