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
import { MaxField } from './MaxField'
import { WindowsField } from './WindowsField'
import { SymbolField, SymbolsField } from './SymbolFields'
import type { AmountUnitState } from '../shared/amountUnit'

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
        label="이동평균 2 기간"
        short="1"
        onShortChange={vi.fn()}
        long="300"
        onLongChange={vi.fn()}
        messages={{ short: range, long: range }}
      />
    )
    expect(screen.getByText('이동평균 2 기간')).toBeInTheDocument()
    expect(screen.getAllByText('2 ~ 250 사이 정수')).toHaveLength(1)
    expect(screen.getByRole('textbox', { name: '짧은 기간(일)' })).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('textbox', { name: '긴 기간(일)' })).toHaveAttribute('aria-invalid', 'true')
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
