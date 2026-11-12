import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useState } from 'react'
import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { usePeriod } from './period'
import { PeriodField } from './PeriodField'
import { AmountField } from './AmountField'
import { BudgetField } from './BudgetField'
import { CadenceField } from './CadenceField'
import { FirstDateField } from './FirstDateField'
import { ConditionField } from './ConditionField'
import { SellField } from './SellField'
import { TrailingStopField } from './TrailingStopField'
import { PartialTakeProfitField } from './PartialTakeProfitField'
import { MaxField } from './MaxField'
import { WindowsField } from './WindowsField'
import { BandField } from './BandField'
import { SymbolField, SymbolsField } from './SymbolFields'
import { SignalSymbolField } from './SignalSymbolField'
import type { AmountUnitState } from '../shared/amountUnit'
import type { TakeProfitStep } from '../shared/partialTakeProfit'

// 기대값은 TZ=Asia/Seoul 전제 (vitest.config.ts 에서 고정)
const dayButton = (day: string) =>
  within(screen.getByRole('dialog'))
    .getAllByRole('button')
    .find((b) => b.textContent === day)!

const unit = (patch: Partial<AmountUnitState> = {}): AmountUnitState => ({
  unit: 'AMOUNT',
  setUnit: vi.fn(),
  shares: '1',
  setShares: vi.fn(),
  ...patch,
})

describe('PeriodField', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-08T12:00:00+09:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const Harness = () => <PeriodField period={usePeriod()} />

  it('처음은 오늘부터 1년 전이 켜져 있고 화살표에 기간을 보인다', () => {
    render(<Harness />)

    expect(screen.getByText('기간')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '오늘부터 몇' })).toHaveValue('1')
    expect(screen.getByRole('radio', { name: '년' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByText('← 1년')).toHaveAttribute('aria-label', '끝 날짜에서 1년 전')
    expect(screen.getByRole('button', { name: '시작일 2025. 10. 08.' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '끝 날짜 2026. 10. 08.' })).toBeInTheDocument()
  })

  it('단위를 바꾸면 끝 날짜에서 다시 센 시작일을 보인다', async () => {
    render(<Harness />)

    await userEvent.click(screen.getByRole('radio', { name: '달' }))

    expect(screen.getByTestId('date-start')).toHaveTextContent('2026. 09. 08.')
    expect(screen.getByText('← 1달')).toBeInTheDocument()
  })

  it('시작일을 달력에서 고르면 빠른 고르기가 꺼지고 화살표는 → 로', async () => {
    render(<Harness />)

    await userEvent.click(screen.getByTestId('date-start'))
    expect(screen.queryByRole('button', { name: '1년 전' })).toBeNull()
    await userEvent.click(dayButton('14'))

    expect(screen.getByTestId('date-start')).toHaveTextContent('2025. 10. 14.')
    expect(screen.getByText('→')).toHaveAttribute('aria-label', '부터')
    expect(screen.getAllByRole('radio').every((radio) => radio.getAttribute('aria-checked') === 'false')).toBe(true)
  })

  it('끝 날짜를 오늘이 아닌 날로 고르면 그 날짜부터 세고 시작일이 따라간다', async () => {
    render(<Harness />)

    await userEvent.click(screen.getByTestId('date-end'))
    await userEvent.click(dayButton('1'))

    expect(screen.getByRole('textbox', { name: '10월 1일부터 몇' })).toBeInTheDocument()
    expect(screen.getByTestId('date-start')).toHaveTextContent('2025. 10. 01.')
  })

  it('빠른 고르기 숫자가 오류면 숫자 칸을 오류 글과 잇고 날짜 단추는 두지 않는다', async () => {
    const Broken = () => {
      const period = usePeriod()
      return <PeriodField period={period} message={{ kind: 'error', text: '몇 주 · 달 · 년 전인지 1 이상' }} />
    }
    render(<Broken />)
    await userEvent.clear(screen.getByRole('textbox', { name: '오늘부터 몇' }))

    const count = screen.getByRole('textbox', { name: '오늘부터 몇' })
    expect(count).toHaveAttribute('aria-invalid', 'true')
    expect(count).toHaveAttribute('aria-describedby', screen.getByText('몇 주 · 달 · 년 전인지 1 이상').id)
    expect(screen.getByTestId('date-start')).not.toHaveAttribute('aria-invalid')
  })

  it('오류가 있으면 칸 아래에 보이고 날짜 단추를 오류 글과 잇는다', () => {
    const Broken = () => <PeriodField period={usePeriod()} message={{ kind: 'error', text: '끝 날짜가 시작일과 같거나 앞' }} />
    render(<Broken />)

    const message = screen.getByText('끝 날짜가 시작일과 같거나 앞')
    expect(screen.getByTestId('date-start')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByTestId('date-start')).toHaveAttribute('aria-describedby', message.id)
  })
})

describe('AmountField', () => {
  it('금액 단위면 ₩ 칸과 읽기 쉬운 금액, 주식 수 단추로 단위를 넘긴다', async () => {
    const amountUnit = unit()
    render(<AmountField label="회당 금액" sharesLabel="회당 주식 수" amount="100000" onAmountChange={vi.fn()} amountUnit={amountUnit} />)

    expect(screen.getByText('회당 금액')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '회당 금액(원)' })).toHaveValue('100,000')
    expect(screen.getByText('10만원')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: '금액' })).toHaveAttribute('aria-checked', 'true')
    await userEvent.click(screen.getByRole('radio', { name: '주식 수' }))

    expect(amountUnit.setUnit).toHaveBeenCalledWith('SHARES')
  })

  it('주식 수 단위면 칸 이름을 바꾸고 숫자만 넘기며 추정 금액을 보인다', () => {
    const amountUnit = unit({ unit: 'SHARES', shares: '2' })
    render(
      <AmountField label="금액" sharesLabel="주식 수" amount="" onAmountChange={vi.fn()} amountUnit={amountUnit} perRound={675000} />
    )

    expect(screen.getByText('주식 수', { selector: 'span' })).toBeInTheDocument()
    const shares = screen.getByRole('textbox', { name: '주식 수(주)' })
    expect(shares).toHaveValue('2')
    fireEvent.change(shares, { target: { value: '3주' } })
    expect(amountUnit.setShares).toHaveBeenCalledWith('3')
    expect(screen.getByText('≈ 67만 5,000원(시작일 값 기준)')).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '금액(원)' })).toBeNull()
  })

  it('시작일 환율을 못 받아 기본 환율로 센 추정이면 기준에 기본 환율을 적는다', () => {
    render(
      <AmountField
        label="금액"
        sharesLabel="주식 수"
        amount=""
        onAmountChange={vi.fn()}
        amountUnit={unit({ unit: 'SHARES', shares: '2' })}
        perRound={675000}
        defaultFx
      />
    )

    expect(screen.getByText('≈ 67만 5,000원(시작일 값 · 기본 환율 기준)')).toBeInTheDocument()
  })

  it('단위가 없는 모드는 금액만 받고 오류를 칸과 잇는다', () => {
    render(
      <AmountField label="전략마다 원금" amount="" onAmountChange={vi.fn()} message={{ kind: 'error', text: '전략마다 원금 비어 있음' }} />
    )

    expect(screen.queryByRole('radiogroup', { name: '단위' })).toBeNull()
    const input = screen.getByRole('textbox', { name: '전략마다 원금(원)' })
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', screen.getByText('전략마다 원금 비어 있음').id)
  })
})

describe('BudgetField', () => {
  it('비면 제한 없음만 보이고 ₩ · 비우기 단추를 숨긴다', () => {
    render(<BudgetField budget="" onChange={vi.fn()} />)

    expect(screen.getByRole('textbox', { name: '예산(원)' })).toHaveAttribute('placeholder', '제한 없음')
    expect(screen.queryByText('₩')).toBeNull()
    expect(screen.queryByRole('button', { name: '예산 비우기' })).toBeNull()
  })

  it('값이 있으면 읽기 쉬운 금액과 비우기 단추', async () => {
    const onChange = vi.fn()
    render(<BudgetField budget="1000000" onChange={onChange} message={{ kind: 'warning', text: '한 번 살 돈보다 적음' }} />)

    expect(screen.getByText('₩')).toBeInTheDocument()
    expect(screen.getByText('100만원')).toBeInTheDocument()
    expect(screen.getByText('한 번 살 돈보다 적음')).toHaveClass('text-warning')
    expect(screen.getByRole('textbox', { name: '예산(원)' })).not.toHaveAttribute('aria-invalid')
    await userEvent.click(screen.getByRole('button', { name: '예산 비우기' }))

    expect(onChange).toHaveBeenCalledWith('')
  })
})

describe('CadenceField', () => {
  const Harness = ({ onCadence = vi.fn(), onMonths = vi.fn() }) => {
    const [cadence, setCadence] = useState<'DAILY' | 'WEEKLY' | 'MONTHLY' | 'MONTHS'>('MONTHLY')
    const [months, setMonths] = useState('3')
    return (
      <CadenceField
        cadence={cadence}
        onCadenceChange={(next) => {
          onCadence(next)
          setCadence(next)
        }}
        months={months}
        onMonthsChange={(next) => {
          onMonths(next)
          setMonths(next)
        }}
      />
    )
  }

  it('매일 · 매주 · 매달 · N개월마다를 하나만 고르는 묶음으로 둔다', async () => {
    const onCadence = vi.fn()
    render(<Harness onCadence={onCadence} />)

    const group = screen.getByRole('radiogroup', { name: '언제' })
    expect(within(group).getAllByRole('radio').map((radio) => radio.textContent)).toEqual(['매일', '매주', '매달', '개월마다'])
    expect(within(group).getByRole('radio', { name: '매달' })).toHaveAttribute('aria-checked', 'true')
    await userEvent.click(within(group).getByRole('radio', { name: '매주' }))

    expect(onCadence).toHaveBeenLastCalledWith('WEEKLY')
  })

  it('개월 칸에 숫자를 넣으면 N개월마다를 고르고 숫자만 남긴다', () => {
    const onCadence = vi.fn()
    const onMonths = vi.fn()
    render(<Harness onCadence={onCadence} onMonths={onMonths} />)

    fireEvent.change(screen.getByRole('textbox', { name: '몇 개월마다' }), { target: { value: '6개' } })

    expect(onMonths).toHaveBeenLastCalledWith('6')
    expect(onCadence).toHaveBeenLastCalledWith('MONTHS')
    expect(screen.getByRole('radio', { name: '개월마다' })).toHaveAttribute('aria-checked', 'true')
  })

  it('화살표로 개월마다까지 옮겨 간다', async () => {
    const onCadence = vi.fn()
    render(<Harness onCadence={onCadence} />)

    screen.getByRole('radio', { name: '매달' }).focus()
    await userEvent.keyboard('{ArrowRight}')

    expect(onCadence).toHaveBeenLastCalledWith('MONTHS')
  })

  it('칩 설정에서는 칸 이름을 바꾼다', () => {
    render(<CadenceField label="적립식 2 주기" cadence="DAILY" onCadenceChange={vi.fn()} months="3" onMonthsChange={vi.fn()} />)

    expect(screen.getByRole('radiogroup', { name: '적립식 2 주기' })).toBeInTheDocument()
  })
})

describe('FirstDateField', () => {
  it('고르지 않았으면 시작일을 보이고 시작일에 맞추기 단추가 없다', () => {
    render(<FirstDateField value="2025-10-08" picked={false} min="2025-10-08" max="2026-10-08" onPick={vi.fn()} onFollow={vi.fn()} />)

    expect(screen.getByRole('button', { name: '첫 매수일 2025. 10. 08.' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '시작일에 맞추기' })).toBeNull()
  })

  it('달력에서 고른 날을 넘기고, 고른 뒤에는 시작일에 맞추기로 되돌린다', async () => {
    const onPick = vi.fn()
    const onFollow = vi.fn()
    const { rerender } = render(
      <FirstDateField value="2025-10-08" picked={false} min="2025-10-08" max="2026-10-08" onPick={onPick} onFollow={onFollow} />
    )

    await userEvent.click(screen.getByTestId('date-first'))
    expect(screen.queryByRole('button', { name: '1년 전' })).toBeNull()
    await userEvent.click(dayButton('15'))
    expect(onPick).toHaveBeenCalledWith('2025-10-15')

    rerender(
      <FirstDateField
        value="2025-10-31"
        picked
        min="2025-10-08"
        max="2026-10-08"
        onPick={onPick}
        onFollow={onFollow}
        message={{ kind: 'warning', text: '31일이 없는 달은 그 달 마지막 거래일' }}
      />
    )
    expect(screen.getByText('31일이 없는 달은 그 달 마지막 거래일')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '시작일에 맞추기' }))
    expect(onFollow).toHaveBeenCalled()
  })
})

describe('ConditionField', () => {
  it('방향을 칸 이름 줄에서 고르고 전날 종가보다 몇 % 를 문장으로 받는다', async () => {
    const onDirection = vi.fn()
    const onPercent = vi.fn()
    const { rerender } = render(
      <ConditionField direction="DROP" onDirectionChange={onDirection} percent="5" onPercentChange={onPercent} />
    )

    expect(screen.getByRole('radio', { name: '하락' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByText('전날 종가보다')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '전날 종가보다 몇 % 내리면' })).toHaveValue('5')
    await userEvent.click(screen.getByRole('radio', { name: '둘 다' }))
    expect(onDirection).toHaveBeenCalledWith('BOTH')
    fireEvent.change(screen.getByRole('textbox', { name: '전날 종가보다 몇 % 내리면' }), { target: { value: '2.5.1%' } })
    expect(onPercent).toHaveBeenCalledWith('2.51')

    rerender(<ConditionField direction="RISE" onDirectionChange={onDirection} percent="5" onPercentChange={onPercent} />)
    expect(screen.getByText('% 오르면')).toBeInTheDocument()
  })
})

describe('SellField', () => {
  it('비운 칸은 부호 · 단위 없이 안 함, 적으면 + · - 와 %', () => {
    const { rerender } = render(
      <SellField takeProfit="" onTakeProfitChange={vi.fn()} stopLoss="" onStopLossChange={vi.fn()} />
    )

    expect(screen.getByRole('textbox', { name: '익절(평균 단가 대비 퍼센트)' })).toHaveAttribute('placeholder', '안 함')
    expect(screen.queryByText('+')).toBeNull()
    expect(screen.queryByText('%')).toBeNull()
    expect(screen.queryByRole('button', { name: '익절 · 손절 비우기' })).toBeNull()

    rerender(<SellField takeProfit="20" onTakeProfitChange={vi.fn()} stopLoss="" onStopLossChange={vi.fn()} />)
    expect(screen.getByText('+')).toBeInTheDocument()
    expect(screen.getAllByText('%')).toHaveLength(1)
  })

  it('비우기 단추는 익절 · 손절을 모두 비운다', async () => {
    const onTakeProfit = vi.fn()
    const onStopLoss = vi.fn()
    render(<SellField takeProfit="20" onTakeProfitChange={onTakeProfit} stopLoss="10" onStopLossChange={onStopLoss} />)

    await userEvent.click(screen.getByRole('button', { name: '익절 · 손절 비우기' }))

    expect(onTakeProfit).toHaveBeenCalledWith('')
    expect(onStopLoss).toHaveBeenCalledWith('')
  })
})

describe('TrailingStopField', () => {
  const noMessages = { stop: null, sell: null }
  const stopInput = () => screen.getByRole('textbox', { name: '고점 대비 하락 비율(퍼센트)' })
  const sellInput = () => screen.getByRole('textbox', { name: '고점 대비 하락 때 팔 비율(보유 대비 퍼센트)' })

  it('둘 다 비면 안 함만, 하락을 적으면 - · % 마다와 100 을 흐린 글로 둔 팔 비율 칸을 보인다', () => {
    const { rerender } = render(
      <TrailingStopField stop="" onStopChange={vi.fn()} sell="" onSellChange={vi.fn()} messages={noMessages} />
    )

    expect(screen.getByText('고점 대비 하락 매도')).toBeInTheDocument()
    expect(screen.queryByText('고점 대비')).toBeNull()
    expect(stopInput()).toHaveAttribute('placeholder', '안 함')
    expect(screen.queryByRole('textbox', { name: '고점 대비 하락 때 팔 비율(보유 대비 퍼센트)' })).toBeNull()
    expect(screen.queryByText('-')).toBeNull()
    expect(screen.queryByText('마다')).toBeNull()
    expect(screen.queryByRole('button', { name: '고점 대비 하락 매도 비우기' })).toBeNull()

    rerender(<TrailingStopField stop="10" onStopChange={vi.fn()} sell="" onSellChange={vi.fn()} messages={noMessages} />)
    expect(sellInput()).toHaveAttribute('placeholder', '100')
    expect(stopInput().closest('[data-field-value]')).toHaveTextContent('-%마다· 보유의%팔기')
  })

  it('하락 · 팔 비율은 소수까지 넘기고, 비우기 단추는 두 칸을 비운다', async () => {
    const onStop = vi.fn()
    const onSell = vi.fn()
    render(<TrailingStopField stop="10" onStopChange={onStop} sell="50" onSellChange={onSell} messages={noMessages} />)

    fireEvent.change(stopInput(), { target: { value: '7..5%' } })
    fireEvent.change(sellInput(), { target: { value: '33.3%' } })
    expect(onStop).toHaveBeenLastCalledWith('7.5')
    expect(onSell).toHaveBeenLastCalledWith('33.3')

    await userEvent.click(screen.getByRole('button', { name: '고점 대비 하락 매도 비우기' }))
    expect(onStop).toHaveBeenLastCalledWith('')
    expect(onSell).toHaveBeenLastCalledWith('')
  })

  it('오류가 있는 칸만 오류로 두고 칸 아래 한 줄과 잇는다', () => {
    render(
      <TrailingStopField
        stop=""
        onStopChange={vi.fn()}
        sell="50"
        onSellChange={vi.fn()}
        messages={{ stop: null, sell: { kind: 'error', text: '하락 비율과 함께' } }}
      />
    )

    expect(stopInput()).not.toHaveAttribute('aria-invalid')
    expect(sellInput()).toHaveAttribute('aria-invalid', 'true')
    expect(sellInput()).toHaveAttribute('aria-describedby', screen.getByText('하락 비율과 함께').id)
  })
})

describe('PartialTakeProfitField', () => {
  const ok = { gain: null, sell: null }
  const gainInput = (n: number) => screen.getByRole('textbox', { name: `${n}단계 수익률(평균 단가 대비 퍼센트)` })
  const sellInput = (n: number) => screen.getByRole('textbox', { name: `${n}단계 팔 비율(보유 대비 퍼센트)` })
  const addButton = () => screen.getByRole('button', { name: '＋ 단계 더하기' })
  const steps = (...pairs: [string, string][]): TakeProfitStep[] => pairs.map(([gain, sell]) => ({ gain, sell }))

  // 단계를 화면 상태로 들고 있는 칸
  const Harness = ({ initial }: { initial: TakeProfitStep[] }) => {
    const [value, setValue] = useState(initial)
    return <PartialTakeProfitField steps={value} onStepsChange={setValue} messages={value.map(() => ok)} />
  }

  it('단계가 없으면 이름 줄의 평균 단가 대비와 안 함 · 단계 더하기만 보이고, 더하면 빈 단계를 뒤에 잇는다', async () => {
    const onSteps = vi.fn()
    render(<PartialTakeProfitField steps={[]} onStepsChange={onSteps} messages={[]} />)

    expect(screen.getByText('부분 익절')).toBeInTheDocument()
    expect(screen.getByText('평균 단가 대비')).toBeInTheDocument()
    expect(screen.getByText('안 함')).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).toBeNull()

    await userEvent.click(addButton())
    expect(onSteps).toHaveBeenLastCalledWith([{ gain: '', sell: '' }])
  })

  it('단계마다 + 수익률 % 오르면 · 보유의 팔 비율 % 팔기와 지우기 단추를 두고, 소수까지 넘기며 지우면 그 단계만 뺀다', async () => {
    const onSteps = vi.fn()
    render(<PartialTakeProfitField steps={steps(['20', '25'], ['50', '100'])} onStepsChange={onSteps} messages={[ok, ok]} />)

    expect(gainInput(1)).toHaveValue('20')
    expect(sellInput(2)).toHaveValue('100')
    expect(gainInput(1).closest('[data-step]')).toHaveTextContent('+%오르면· 보유의%팔기')
    expect(screen.queryByText('안 함')).toBeNull()

    fireEvent.change(gainInput(2), { target: { value: '7..5%' } })
    expect(onSteps).toHaveBeenLastCalledWith(steps(['20', '25'], ['7.5', '100']))
    fireEvent.change(sellInput(1), { target: { value: '33.3%' } })
    expect(onSteps).toHaveBeenLastCalledWith(steps(['20', '33.3'], ['50', '100']))

    await userEvent.click(screen.getByRole('button', { name: '1단계 지우기' }))
    expect(onSteps).toHaveBeenLastCalledWith(steps(['50', '100']))
  })

  it('더하면 새 단계의 수익률 칸으로, 지우면 그 자리 단계(없으면 앞 단계, 다 지우면 더하기 단추)로 포커스를 옮긴다', async () => {
    render(<Harness initial={steps(['10', '50'])} />)

    await userEvent.click(addButton())
    await vi.waitFor(() => expect(gainInput(2)).toHaveFocus())

    await userEvent.click(screen.getByRole('button', { name: '2단계 지우기' }))
    await vi.waitFor(() => expect(gainInput(1)).toHaveFocus())
    await userEvent.click(screen.getByRole('button', { name: '1단계 지우기' }))
    await vi.waitFor(() => expect(addButton()).toHaveFocus())
  })

  it('5단계면 더하기 단추 대신 최대 5단계를 보인다', () => {
    const five = steps(['10', '10'], ['20', '10'], ['30', '10'], ['40', '10'], ['50', '10'])
    render(<PartialTakeProfitField steps={five} onStepsChange={vi.fn()} messages={five.map(() => ok)} />)

    expect(screen.queryByRole('button', { name: '＋ 단계 더하기' })).toBeNull()
    expect(screen.getByText('최대 5단계')).toBeInTheDocument()
  })

  it('오류가 있는 칸만 오류로 두고 칸 아래 한 줄은 앞 단계 것 하나, 그 글을 낸 칸만 그 줄과 잇는다', () => {
    render(
      <PartialTakeProfitField
        steps={steps(['20', '25'], ['20', ''])}
        onStepsChange={vi.fn()}
        messages={[
          ok,
          { gain: { kind: 'error', text: '2단계 수익률 앞 단계보다 크게' }, sell: { kind: 'error', text: '2단계 팔 비율 비어 있음' } },
        ]}
      />
    )

    expect(gainInput(1)).not.toHaveAttribute('aria-invalid')
    expect(gainInput(2)).toHaveAttribute('aria-invalid', 'true')
    expect(gainInput(2)).toHaveAttribute('aria-describedby', screen.getByText('2단계 수익률 앞 단계보다 크게').id)
    expect(sellInput(2)).toHaveAttribute('aria-invalid', 'true')
    expect(sellInput(2)).not.toHaveAttribute('aria-describedby')
    expect(screen.queryByText('2단계 팔 비율 비어 있음')).toBeNull()
  })
})

describe('MaxField', () => {
  it('비면 placeholder 만, 적으면 회와 비우기 단추, 숫자만 넘긴다', async () => {
    const onChange = vi.fn()
    const { rerender } = render(<MaxField value="" onChange={onChange} placeholder="예산 한도까지" />)

    expect(screen.getByRole('textbox', { name: '최대 횟수(회)' })).toHaveAttribute('placeholder', '예산 한도까지')
    expect(screen.queryByText('회')).toBeNull()
    fireEvent.change(screen.getByRole('textbox', { name: '최대 횟수(회)' }), { target: { value: '5회' } })
    expect(onChange).toHaveBeenLastCalledWith('5')

    rerender(<MaxField value="5" onChange={onChange} placeholder="예산 한도까지" />)
    expect(screen.getByText('회')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '최대 횟수 비우기' }))
    expect(onChange).toHaveBeenLastCalledWith('')
  })
})

describe('WindowsField', () => {
  it('짧은 · 긴 기간을 일 수로 받고 숫자만 넘긴다', () => {
    const onShort = vi.fn()
    const onLong = vi.fn()
    render(<WindowsField short="20" onShortChange={onShort} long="60" onLongChange={onLong} messages={{ short: null, long: null }} />)

    expect(screen.getByText('이동평균 기간')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '짧은 기간(일)' })).toHaveValue('20')
    expect(screen.getByRole('textbox', { name: '긴 기간(일)' })).toHaveValue('60')
    fireEvent.change(screen.getByRole('textbox', { name: '짧은 기간(일)' }), { target: { value: '5일' } })
    fireEvent.change(screen.getByRole('textbox', { name: '긴 기간(일)' }), { target: { value: '120' } })

    expect(onShort).toHaveBeenLastCalledWith('5')
    expect(onLong).toHaveBeenLastCalledWith('120')
  })

  it('오류가 있는 칸만 오류로 두고 칸 아래 한 줄과 잇는다, 칩 설정이면 칸 이름을 바꾼다', () => {
    const range = { kind: 'error' as const, text: '2 ~ 250 사이 정수' }
    const { rerender } = render(
      <WindowsField short="20" onShortChange={vi.fn()} long="300" onLongChange={vi.fn()} messages={{ short: null, long: range }} />
    )

    const short = screen.getByRole('textbox', { name: '짧은 기간(일)' })
    const long = screen.getByRole('textbox', { name: '긴 기간(일)' })
    expect(short).not.toHaveAttribute('aria-invalid')
    expect(long).toHaveAttribute('aria-invalid', 'true')
    expect(long).toHaveAttribute('aria-describedby', screen.getByText('2 ~ 250 사이 정수').id)

    rerender(
      <WindowsField
        label="이동평균 교차 2 기간"
        short="1"
        onShortChange={vi.fn()}
        long="300"
        onLongChange={vi.fn()}
        messages={{ short: range, long: range }}
      />
    )
    expect(screen.getByText('이동평균 교차 2 기간')).toBeInTheDocument()
    expect(screen.getAllByText('2 ~ 250 사이 정수')).toHaveLength(1)
    expect(screen.getByRole('textbox', { name: '짧은 기간(일)' })).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('textbox', { name: '긴 기간(일)' })).toHaveAttribute('aria-invalid', 'true')
  })
})

describe('BandField', () => {
  const noMessages = { window: null, upper: null, lower: null }

  it('평균 기간은 일 수 · 위 · 아래 비율은 퍼센트로 받고, 기간은 숫자만 · 비율은 소수까지 넘긴다', () => {
    const onWindow = vi.fn()
    const onUpper = vi.fn()
    const onLower = vi.fn()
    render(
      <BandField
        window="200"
        onWindowChange={onWindow}
        upper="2.5"
        onUpperChange={onUpper}
        lower="2.5"
        onLowerChange={onLower}
        messages={noMessages}
      />
    )

    expect(screen.getByText('밴드')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '평균 기간(일)' })).toHaveValue('200')
    expect(screen.getByRole('textbox', { name: '위 비율(평균 대비 퍼센트)' })).toHaveValue('2.5')
    expect(screen.getByRole('textbox', { name: '아래 비율(평균 대비 퍼센트)' })).toHaveValue('2.5')
    fireEvent.change(screen.getByRole('textbox', { name: '평균 기간(일)' }), { target: { value: '120일' } })
    fireEvent.change(screen.getByRole('textbox', { name: '위 비율(평균 대비 퍼센트)' }), { target: { value: '3.5%' } })
    fireEvent.change(screen.getByRole('textbox', { name: '아래 비율(평균 대비 퍼센트)' }), { target: { value: '1..5' } })

    expect(onWindow).toHaveBeenLastCalledWith('120')
    expect(onUpper).toHaveBeenLastCalledWith('3.5')
    expect(onLower).toHaveBeenLastCalledWith('1.5')
  })

  it('휴대폰은 평균 기간 한 줄 · 비율 두 칸 한 줄로 나누고 넓은 화면은 한 줄로 둔다', () => {
    render(
      <BandField window="200" onWindowChange={vi.fn()} upper="2.5" onUpperChange={vi.fn()} lower="2.5" onLowerChange={vi.fn()} messages={noMessages} />
    )

    const windowGroup = screen.getByRole('textbox', { name: '평균 기간(일)' }).parentElement!
    expect(windowGroup).toHaveClass('basis-full', 'sm:basis-auto')
    expect(windowGroup).toHaveTextContent('일선')
  })

  it('오류가 있는 칸만 오류로 두고 칸 아래 한 줄과 잇는다, 칩 설정이면 칸 이름을 바꾼다', () => {
    const range = { kind: 'error' as const, text: '0 이상 100 미만' }
    const { rerender } = render(
      <BandField
        window="200"
        onWindowChange={vi.fn()}
        upper="2.5"
        onUpperChange={vi.fn()}
        lower="120"
        onLowerChange={vi.fn()}
        messages={{ window: null, upper: null, lower: range }}
      />
    )

    const lower = screen.getByRole('textbox', { name: '아래 비율(평균 대비 퍼센트)' })
    expect(screen.getByRole('textbox', { name: '평균 기간(일)' })).not.toHaveAttribute('aria-invalid')
    expect(lower).toHaveAttribute('aria-invalid', 'true')
    expect(lower).toHaveAttribute('aria-describedby', screen.getByText('0 이상 100 미만').id)

    rerender(
      <BandField
        label="밴드 2 설정"
        window="1"
        onWindowChange={vi.fn()}
        upper="2.5"
        onUpperChange={vi.fn()}
        lower="120"
        onLowerChange={vi.fn()}
        messages={{ window: { kind: 'error', text: '2 ~ 250 사이 정수' }, upper: null, lower: range }}
      />
    )
    expect(screen.getByText('밴드 2 설정')).toBeInTheDocument()
    expect(screen.getByText('2 ~ 250 사이 정수')).toBeInTheDocument()
    expect(screen.queryByText('0 이상 100 미만')).toBeNull()
  })
})

describe('SymbolField · SymbolsField', () => {
  it('한 종목 칸은 밑줄 칸에 회사 이름, 오류를 칸과 잇는다', () => {
    render(
      <SymbolField symbol="AAPL" onChange={vi.fn()} supportedSymbols={['AAPL']} message={{ kind: 'error', text: '목록에서 고르기' }} />
    )

    const input = screen.getByRole('combobox', { name: '종목' })
    expect(input).toHaveValue('AAPL')
    expect(screen.getByText('Apple Inc.')).toBeInTheDocument()
    expect(input).toHaveAttribute('aria-describedby', screen.getByText('목록에서 고르기').id)
  })

  it('종목 비교는 칩 · 개수와 더하기 칸, 빼기 단추로 넘긴다', async () => {
    const onAdd = vi.fn()
    const onRemove = vi.fn()
    render(<SymbolsField symbols={['AAPL', 'MSFT']} onAdd={onAdd} onRemove={onRemove} supportedSymbols={['AAPL', 'MSFT', 'NVDA']} />)

    expect(screen.getByText('2 / 10')).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'AAPL Apple Inc.' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'MSFT 빼기' }))
    expect(onRemove).toHaveBeenCalledWith('MSFT')
    await vi.waitFor(() => expect(screen.getByRole('button', { name: 'AAPL 빼기' })).toHaveFocus())

    const add = screen.getByRole('combobox', { name: '종목 더하기' })
    await userEvent.type(add, 'nv')
    await userEvent.click(screen.getByRole('option', { name: /NVDA/ }))
    expect(onAdd).toHaveBeenCalledWith('NVDA')
    expect(add).toHaveValue('')
  })

  it('10개면 더하기 칸을 숨긴다', () => {
    const ten = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J']
    render(<SymbolsField symbols={ten} onAdd={vi.fn()} onRemove={vi.fn()} supportedSymbols={ten} />)

    expect(screen.getByText('10 / 10')).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: '종목 더하기' })).toBeNull()
  })
})

describe('SignalSymbolField', () => {
  const field = (patch: Partial<Parameters<typeof SignalSymbolField>[0]> = {}) => ({
    choice: 'same' as const,
    onChoiceChange: vi.fn(),
    other: '',
    onOtherChange: vi.fn(),
    supportedSymbols: ['AAPL', 'MSFT'],
    ...patch,
  })

  it('매매 종목과 같게 · S&P 500 · 나스닥 100 · 다른 종목을 하나만 고르고, 같게 · 지수면 종목 칸이 없다', async () => {
    const props = field()
    const { rerender } = render(<SignalSymbolField {...props} />)

    const group = screen.getByRole('radiogroup', { name: '신호 종목' })
    expect(within(group).getAllByRole('radio').map((radio) => radio.textContent)).toEqual([
      '매매 종목과 같게',
      'S&P 500',
      '나스닥 100',
      '다른 종목',
    ])
    expect(screen.getByRole('radio', { name: '매매 종목과 같게' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.queryByRole('combobox', { name: '신호 종목' })).toBeNull()
    await userEvent.click(screen.getByRole('radio', { name: 'S&P 500' }))
    expect(props.onChoiceChange).toHaveBeenCalledWith('^GSPC')

    rerender(<SignalSymbolField {...props} choice="^NDX" />)
    expect(screen.getByRole('radio', { name: '나스닥 100' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.queryByRole('combobox', { name: '신호 종목' })).toBeNull()
  })

  it('다른 종목이면 목록에서 고르는 종목 칸을 보이고 오류를 칸과 잇는다', async () => {
    const props = field({ choice: 'other', message: { kind: 'error', text: '신호 종목 비어 있음' } })
    render(<SignalSymbolField {...props} />)

    const input = screen.getByRole('combobox', { name: '신호 종목' })
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', screen.getByText('신호 종목 비어 있음').id)
    await userEvent.type(input, 'ms')
    await userEvent.click(screen.getByRole('option', { name: /MSFT/ }))
    expect(props.onOtherChange).toHaveBeenCalledWith('MSFT')
  })
})
