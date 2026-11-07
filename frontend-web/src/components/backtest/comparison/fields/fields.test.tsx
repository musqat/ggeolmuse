import { describe, it, expect, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DcaFields } from './DcaFields'
import { ConditionalFields } from './ConditionalFields'

const COMMON = { startDate: '2023-01-01', investment: '1000000' }

describe('비교 창 전략 칸', () => {
  it('적립식 칸은 매월 투자일 · 투자 주기를 바꾼 값으로 넘긴다', async () => {
    const onChange = vi.fn()
    render(<DcaFields params={{}} onChange={onChange} common={COMMON} />)

    const day = screen.getByPlaceholderText('15')
    const interval = screen.getByRole('combobox')
    expect(day).toHaveValue(15)
    expect(interval).toHaveValue('1')

    fireEvent.change(day, { target: { value: '20' } })
    await userEvent.selectOptions(interval, '3')

    expect(onChange).toHaveBeenCalledWith('purchaseDay', '20')
    expect(onChange).toHaveBeenCalledWith('investmentInterval', '3')
  })

  it('조건부 칸은 하락률을 바꾼 값으로 넘긴다', () => {
    const onChange = vi.fn()
    render(<ConditionalFields params={{}} onChange={onChange} common={COMMON} />)

    const drop = screen.getByPlaceholderText('5')
    expect(drop).toHaveValue(5)

    fireEvent.change(drop, { target: { value: '7' } })

    expect(onChange).toHaveBeenCalledWith('dropPercentage', '7')
  })

  it('조건부 칸은 익절 · 손절 퍼센트를 바꾼 값으로 넘기고 기본은 비어 있다', () => {
    const onChange = vi.fn()
    render(<ConditionalFields params={{}} onChange={onChange} common={COMMON} />)

    const takeProfit = screen.getByLabelText('익절 (%)')
    expect(takeProfit).toHaveValue(null)

    fireEvent.change(takeProfit, { target: { value: '20' } })
    fireEvent.change(screen.getByLabelText('손절 (%)'), { target: { value: '10' } })

    expect(onChange).toHaveBeenCalledWith('takeProfit', '20')
    expect(onChange).toHaveBeenCalledWith('stopLoss', '10')
  })
})
