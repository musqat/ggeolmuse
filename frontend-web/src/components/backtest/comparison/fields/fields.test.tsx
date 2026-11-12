import { describe, it, expect, vi } from 'vitest'
import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DcaFields } from './DcaFields'
import { ConditionalFields } from './ConditionalFields'
import { MaCrossFields } from './MaCrossFields'
import { MaBandFields } from './MaBandFields'

// 기대값은 TZ=Asia/Seoul 전제 (vitest.config.ts 에서 고정)
const COMMON = { startDate: '2023-01-02', endDate: '2023-12-29', investment: '1000000' }
const DCA = { amountPerPurchase: '100000', cadence: 'MONTHLY', months: '3', firstPurchaseDate: '' }
const CONDITIONAL = { amountPerPurchase: '100000', maxPurchases: '', dropPercentage: '5', direction: 'DROP' }

describe('전략 칩 설정 칸', () => {
  it('적립식 칩은 칩 이름을 단 회당 금액 · 주기 칸이고 바꾼 값을 키와 함께 넘긴다', async () => {
    const onChange = vi.fn()
    render(<DcaFields name="적립식 2" params={DCA} onChange={onChange} common={COMMON} />)

    const amount = screen.getByRole('textbox', { name: '적립식 2 회당 금액(원)' })
    expect(amount).toHaveValue('100,000')
    fireEvent.change(amount, { target: { value: '200,000' } })
    await userEvent.click(within(screen.getByRole('radiogroup', { name: '적립식 2 주기' })).getByRole('radio', { name: '매주' }))

    expect(onChange).toHaveBeenCalledWith('amountPerPurchase', '200000')
    expect(onChange).toHaveBeenCalledWith('cadence', 'WEEKLY')
  })

  it('적립식 칩 첫 매수일은 시작일을 보이고, 고르면 고를 때의 시작일과 함께 넘긴다', async () => {
    const onChange = vi.fn()
    render(<DcaFields name="적립식" params={DCA} onChange={onChange} common={COMMON} />)

    expect(screen.getByTestId('date-first')).toHaveTextContent('2023. 01. 02.')
    await userEvent.click(screen.getByTestId('date-first'))
    await userEvent.click(within(screen.getByRole('dialog')).getAllByRole('button').find((b) => b.textContent === '16')!)

    expect(onChange).toHaveBeenCalledWith('firstPurchaseDate', '2023-01-16')
    expect(onChange).toHaveBeenCalledWith('firstPickedStart', '2023-01-02')
  })

  it('적립식 칩 N개월마다 칸이 범위 밖이면 그 칸에 오류', () => {
    render(<DcaFields name="적립식" params={{ ...DCA, cadence: 'MONTHS', months: '0' }} onChange={vi.fn()} common={COMMON} />)

    expect(screen.getByText('개월은 1 ~ 12')).toBeInTheDocument()
  })

  it('조건부 칩은 회당 금액 · 조건 · 매도 조건 · 최대 횟수(비우면 원금 한도까지)', () => {
    const onChange = vi.fn()
    render(<ConditionalFields name="조건부 매수" params={CONDITIONAL} onChange={onChange} common={COMMON} />)

    expect(screen.getByRole('radiogroup', { name: '방향' })).toBeInTheDocument()
    expect(screen.getByText('조건부 매수 조건')).toBeInTheDocument()
    const max = screen.getByRole('textbox', { name: '최대 횟수(회)' })
    expect(max).toHaveAttribute('placeholder', '원금 한도까지')
    fireEvent.change(screen.getByRole('textbox', { name: '조건부 매수 회당 금액(원)' }), { target: { value: '300000' } })
    fireEvent.change(max, { target: { value: '4' } })
    fireEvent.change(screen.getByRole('textbox', { name: '전날 종가보다 몇 % 내리면' }), { target: { value: '7' } })
    fireEvent.change(screen.getByRole('textbox', { name: '익절(평균 단가 대비 퍼센트)' }), { target: { value: '20' } })

    expect(onChange).toHaveBeenCalledWith('amountPerPurchase', '300000')
    expect(onChange).toHaveBeenCalledWith('maxPurchases', '4')
    expect(onChange).toHaveBeenCalledWith('dropPercentage', '7')
    expect(onChange).toHaveBeenCalledWith('takeProfit', '20')
  })

  it('이동평균 교차 칩은 칩 이름으로 시작하는 기간 칸 하나이고 바꾼 기간을 키와 함께 넘긴다', () => {
    const onChange = vi.fn()
    render(<MaCrossFields name="이동평균 교차 2" params={{ shortWindow: '20', longWindow: '60' }} onChange={onChange} common={COMMON} />)

    expect(screen.getByText('이동평균 교차 2 기간')).toBeInTheDocument()
    fireEvent.change(screen.getByRole('textbox', { name: '짧은 기간(일)' }), { target: { value: '5' } })
    fireEvent.change(screen.getByRole('textbox', { name: '긴 기간(일)' }), { target: { value: '120' } })

    expect(onChange).toHaveBeenCalledWith('shortWindow', '5')
    expect(onChange).toHaveBeenCalledWith('longWindow', '120')
  })

  it('이동평균 교차 칩의 짧은 기간이 긴 기간 이상이면 그 칸에 오류', () => {
    render(<MaCrossFields name="이동평균 교차" params={{ shortWindow: '60', longWindow: '60' }} onChange={vi.fn()} common={COMMON} />)

    expect(screen.getByText('긴 기간보다 짧게')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '짧은 기간(일)' })).toHaveAttribute('aria-invalid', 'true')
  })

  it('밴드 칩은 칩 이름으로 시작하는 밴드 칸 하나이고 바꾼 값을 키와 함께 넘기며, 맞지 않는 칸에 오류', () => {
    const onChange = vi.fn()
    render(
      <MaBandFields
        name="밴드 2"
        params={{ bandWindow: '200', upperPercent: '2.5', lowerPercent: '100' }}
        onChange={onChange}
        common={COMMON}
      />
    )

    expect(screen.getByText('밴드 2 설정')).toBeInTheDocument()
    expect(screen.getByText('0 이상 100 미만')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '아래 비율(평균 대비 퍼센트)' })).toHaveAttribute('aria-invalid', 'true')
    fireEvent.change(screen.getByRole('textbox', { name: '평균 기간(일)' }), { target: { value: '100' } })
    fireEvent.change(screen.getByRole('textbox', { name: '위 비율(평균 대비 퍼센트)' }), { target: { value: '3' } })
    fireEvent.change(screen.getByRole('textbox', { name: '아래 비율(평균 대비 퍼센트)' }), { target: { value: '5' } })

    expect(onChange).toHaveBeenCalledWith('bandWindow', '100')
    expect(onChange).toHaveBeenCalledWith('upperPercent', '3')
    expect(onChange).toHaveBeenCalledWith('lowerPercent', '5')
  })

  it('조건부 칩의 조건 % 를 비우면 그 칸에 오류', () => {
    render(<ConditionalFields name="조건부 매수" params={{ ...CONDITIONAL, dropPercentage: '' }} onChange={vi.fn()} common={COMMON} />)

    expect(screen.getByText('% 비어 있음')).toBeInTheDocument()
  })
})
