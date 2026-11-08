import { describe, it, expect, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DcaFields } from './DcaFields'
import { ConditionalFields } from './ConditionalFields'

const COMMON = { startDate: '2023-01-01', endDate: '2023-12-31', investment: '1000000' }
const DCA = { amountPerPurchase: '100000', cadence: 'MONTHLY', months: '3', firstPurchaseDate: '' }
const CONDITIONAL = { amountPerPurchase: '100000', maxPurchases: '', dropPercentage: '5', direction: 'DROP' }

describe('비교 창 전략 칸', () => {
  it('적립식 칸은 원금을 예산으로 안내하고 회당 금액 · 투자 주기를 바꾼 값으로 넘긴다', async () => {
    const onChange = vi.fn()
    render(<DcaFields params={DCA} onChange={onChange} common={COMMON} />)

    expect(screen.getByText(/예산은 위에서 정한 원금/)).toHaveTextContent('예산은 위에서 정한 원금 ₩1,000,000입니다.')
    const amount = screen.getByPlaceholderText('100,000')
    expect(amount).toHaveValue('100,000')
    expect(screen.getByRole('combobox', { name: '투자 주기' })).toHaveValue('MONTHLY')
    expect(screen.queryByRole('spinbutton', { name: '몇 개월마다' })).toBeNull()
    expect(screen.getByText('매달 1일')).toBeInTheDocument()

    fireEvent.change(amount, { target: { value: '200,000' } })
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '투자 주기' }), 'WEEKLY')

    expect(onChange).toHaveBeenCalledWith('amountPerPurchase', '200000')
    expect(onChange).toHaveBeenCalledWith('cadence', 'WEEKLY')
  })

  it('적립식 N개월마다는 개월 수 칸을 열고 바꾼 값을 넘긴다', () => {
    const onChange = vi.fn()
    render(<DcaFields params={{ ...DCA, cadence: 'MONTHS' }} onChange={onChange} common={COMMON} />)

    const months = screen.getByRole('spinbutton', { name: '몇 개월마다' })
    expect(months).toHaveValue(3)
    fireEvent.change(months, { target: { value: '6' } })

    expect(onChange).toHaveBeenCalledWith('months', '6')
  })

  it('조건부 칸은 원금을 예산으로 안내하고 회당 금액 · 최대 횟수 · 하락률을 바꾼 값으로 넘긴다', () => {
    const onChange = vi.fn()
    render(<ConditionalFields params={CONDITIONAL} onChange={onChange} common={COMMON} />)

    expect(screen.getByText(/예산은 위에서 정한 원금/)).toHaveTextContent('예산은 위에서 정한 원금 ₩1,000,000입니다.')
    const max = screen.getByRole('spinbutton', { name: '최대 횟수' })
    expect(max).toHaveValue(null)
    expect(max).toHaveAttribute('placeholder', '원금 한도까지')
    const drop = screen.getByRole('spinbutton', { name: '하락률 (%)' })
    expect(drop).toHaveValue(5)

    fireEvent.change(screen.getByPlaceholderText('100,000'), { target: { value: '300000' } })
    fireEvent.change(max, { target: { value: '4' } })
    fireEvent.change(drop, { target: { value: '7' } })

    expect(onChange).toHaveBeenCalledWith('amountPerPurchase', '300000')
    expect(onChange).toHaveBeenCalledWith('maxPurchases', '4')
    expect(onChange).toHaveBeenCalledWith('dropPercentage', '7')
  })

  it('조건부 칸은 익절 · 손절 퍼센트를 바꾼 값으로 넘기고 기본은 비어 있다', () => {
    const onChange = vi.fn()
    render(<ConditionalFields params={CONDITIONAL} onChange={onChange} common={COMMON} />)

    const takeProfit = screen.getByLabelText('익절 (%)')
    expect(takeProfit).toHaveValue(null)

    fireEvent.change(takeProfit, { target: { value: '20' } })
    fireEvent.change(screen.getByLabelText('손절 (%)'), { target: { value: '10' } })

    expect(onChange).toHaveBeenCalledWith('takeProfit', '20')
    expect(onChange).toHaveBeenCalledWith('stopLoss', '10')
  })
})
