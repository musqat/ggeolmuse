import { describe, it, expect, vi } from 'vitest'
import { useState } from 'react'
import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PortfolioAllocation } from '@services/api'
import { PortfolioAssetsField, type PortfolioAssetsFieldProps } from './PortfolioAssetsField'
import { newRow, type PortfolioRow } from '../../shared/portfolioRows'

const ok = {
  symbol: null,
  value: null,
  takeProfit: null,
  stopLoss: null,
  shortWindow: null,
  longWindow: null,
  bandWindow: null,
  upper: null,
  lower: null,
}
const error = (text: string) => ({ kind: 'error' as const, text })
const row = (symbol: string, amount = '', weight = '', takeProfit = '', stopLoss = ''): PortfolioRow => ({
  ...newRow(symbol, amount, weight),
  takeProfit,
  stopLoss,
})

const symbolInput = (n: number) => screen.getByRole('combobox', { name: `${n}번 종목` })
const amountInput = (n: number) => screen.getByRole('textbox', { name: `${n}번 금액(원)` })
const weightInput = (n: number) => screen.getByRole('textbox', { name: `${n}번 비율(퍼센트)` })
const takeProfitInput = (n: number) => screen.getByRole('textbox', { name: `${n}번 익절(평균 단가 대비 퍼센트)` })
const stopLossInput = (n: number) => screen.getByRole('textbox', { name: `${n}번 손절(평균 단가 대비 퍼센트)` })
const strategySelect = (n: number) => screen.getByRole('combobox', { name: `${n}번 전략` })
const shortInput = (n: number) => screen.getByRole('textbox', { name: `${n}번 짧은 기간(일)` })
const longInput = (n: number) => screen.getByRole('textbox', { name: `${n}번 긴 기간(일)` })
const bandWindowInput = (n: number) => screen.getByRole('textbox', { name: `${n}번 평균 기간(일)` })
const upperInput = (n: number) => screen.getByRole('textbox', { name: `${n}번 위 비율(평균 대비 퍼센트)` })
const lowerInput = (n: number) => screen.getByRole('textbox', { name: `${n}번 아래 비율(평균 대비 퍼센트)` })
const removeButton = (n: number) => screen.getByRole('button', { name: `${n}번 종목 지우기` })
const addButton = () => screen.getByRole('button', { name: '＋ 종목 더하기' })
const sum = () => screen.getByTestId('portfolio-sum')

// 줄 · 방식 · 총액을 화면 상태로 들고 있는 칸
const Harness = ({ initial, patch = {} }: { initial: PortfolioRow[]; patch?: Partial<PortfolioAssetsFieldProps> }) => {
  const [rows, setRows] = useState(initial)
  const [allocation, setAllocation] = useState<PortfolioAllocation>('AMOUNT')
  const [total, setTotal] = useState('10000000')
  return (
    <PortfolioAssetsField
      rows={rows}
      onRowsChange={setRows}
      allocation={allocation}
      onAllocationChange={setAllocation}
      total={total}
      onTotalChange={setTotal}
      perRound={false}
      method="LUMP_SUM"
      supportedSymbols={['SPY', 'QQQ', 'MSFT']}
      messages={rows.map(() => ok)}
      totalMessage={null}
      {...patch}
    />
  )
}

describe('PortfolioAssetsField', () => {
  it('금액 방식은 줄마다 종목 · ₩ 금액 · 합계 대비 몫과 지우기 단추, 아래 합계 ₩ 를 보이고 줄이 하나면 지우기 단추가 없다', () => {
    const { unmount } = render(<Harness initial={[row('SPY', '6000000'), row('QQQ', '4000000')]} />)

    expect(screen.getByText('종목')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: '금액' })).toHaveAttribute('aria-checked', 'true')
    expect(symbolInput(1)).toHaveValue('SPY')
    expect(amountInput(2)).toHaveValue('4,000,000')
    expect(amountInput(1).closest('[data-row]')).toHaveTextContent('60%')
    expect(amountInput(2).closest('[data-row]')).toHaveTextContent('40%')
    expect(removeButton(2)).toBeInTheDocument()
    expect(sum()).toHaveTextContent('합계 ₩10,000,000')
    expect(screen.queryByRole('textbox', { name: '총액(원)' })).toBeNull()
    unmount()

    render(<Harness initial={[row('SPY', '6000000')]} />)
    expect(screen.queryByRole('button', { name: '1번 종목 지우기' })).toBeNull()
  })

  it('비율을 고르면 총액 칸과 줄마다 비율 % · 어림 금액을 보이고, 비율 합이 100 이 아니면 합계 줄을 경고 색으로 둔다', async () => {
    render(<Harness initial={[row('SPY', '6000000', '60'), row('QQQ', '4000000', '')]} />)

    await userEvent.click(screen.getByRole('radio', { name: '비율' }))
    expect(screen.getByRole('textbox', { name: '총액(원)' })).toHaveValue('10,000,000')
    expect(weightInput(1)).toHaveValue('60')
    expect(weightInput(1).closest('[data-row]')).toHaveTextContent('≈ 600만원')
    expect(screen.queryByRole('textbox', { name: '1번 금액(원)' })).toBeNull()
    expect(sum()).toHaveTextContent('합계 60% · 100% 로 맞추기')
    expect(sum()).toHaveClass('text-warning')

    fireEvent.change(weightInput(2), { target: { value: '4..0%' } })
    expect(weightInput(2)).toHaveValue('4.0')
    fireEvent.change(weightInput(2), { target: { value: '40' } })
    expect(sum()).toHaveTextContent('합계 100%')
    expect(sum()).not.toHaveTextContent('맞추기')
    expect(sum()).not.toHaveClass('text-warning')
  })

  it('줄마다 익절 · 손절 칸은 비면 안 함, 적으면 + · - 부호와 % 를 보이고 그 줄 값만 바꾼다', async () => {
    render(<Harness initial={[row('SPY', '6000000'), row('QQQ', '4000000', '', '', '5')]} />)
    const first = amountInput(1).closest('[data-row]') as HTMLElement

    expect(takeProfitInput(1)).toHaveValue('')
    expect(takeProfitInput(1)).toHaveAttribute('placeholder', '안 함')
    expect(stopLossInput(1)).toHaveAttribute('placeholder', '안 함')
    expect(within(first).queryByText('+')).toBeNull()
    expect(within(first).queryByText('%')).toBeNull()
    expect(stopLossInput(2)).toHaveValue('5')

    fireEvent.change(takeProfitInput(1), { target: { value: '3..5%' } })
    fireEvent.change(stopLossInput(1), { target: { value: '10' } })
    expect(takeProfitInput(1)).toHaveValue('3.5')
    expect(stopLossInput(1)).toHaveValue('10')
    expect(within(first).getByText('+')).toBeInTheDocument()
    expect(within(first).getByText('-')).toBeInTheDocument()
    expect(within(first).getAllByText('%')).toHaveLength(2)
    expect(stopLossInput(2)).toHaveValue('5')
    expect(takeProfitInput(2)).toHaveValue('')

    await userEvent.click(screen.getByRole('radio', { name: '비율' }))
    expect(takeProfitInput(1)).toHaveValue('3.5')
    expect(stopLossInput(2)).toHaveValue('5')
  })

  it('＋ 종목 더하기의 새 줄은 익절 · 손절이 비어 있다', async () => {
    const onRows = vi.fn()
    const rows = [row('SPY', '6000000', '', '30', '10')]
    render(<Harness initial={rows} patch={{ onRowsChange: onRows, rows }} />)

    await userEvent.click(addButton())
    expect(onRows).toHaveBeenLastCalledWith([row('SPY', '6000000', '', '30', '10'), row('')])
  })

  it('익절 · 손절 오류는 두 칸 다 오류로 두고 칸 아래 한 줄은 익절 것, 그 글을 낸 익절 칸만 그 줄과 잇는다', () => {
    render(
      <Harness
        initial={[row('SPY', '1', '', '0', '100')]}
        patch={{ messages: [{ ...ok, takeProfit: error('1번 줄 익절 0 보다 크게'), stopLoss: error('1번 줄 손절 100 아래') }] }}
      />
    )

    expect(screen.getByText('1번 줄 익절 0 보다 크게')).toBeInTheDocument()
    expect(screen.queryByText('1번 줄 손절 100 아래')).toBeNull()
    expect(amountInput(1)).not.toHaveAttribute('aria-invalid')
    expect(takeProfitInput(1)).toHaveAttribute('aria-invalid', 'true')
    expect(stopLossInput(1)).toHaveAttribute('aria-invalid', 'true')
    expect(takeProfitInput(1)).toHaveAccessibleDescription('1번 줄 익절 0 보다 크게')
    expect(stopLossInput(1)).not.toHaveAttribute('aria-describedby')
  })

  it('적립식이면 총액 · 합계가 회당이다', async () => {
    render(<Harness initial={[row('SPY', '1000000', '100')]} patch={{ perRound: true }} />)
    expect(sum()).toHaveTextContent('회당 합계 ₩1,000,000')

    await userEvent.click(screen.getByRole('radio', { name: '비율' }))
    expect(screen.getByRole('textbox', { name: '회당 총액(원)' })).toHaveValue('10,000,000')
  })

  it('＋ 종목 더하기는 빈 줄을 뒤에 잇고 그 줄 종목 칸으로, 10줄이면 단추 대신 최대 10개를 보인다', async () => {
    render(<Harness initial={[row('SPY', '6000000')]} />)

    await userEvent.click(addButton())
    expect(symbolInput(2)).toHaveValue('')
    expect(amountInput(2)).toHaveValue('')
    expect(symbolInput(2)).toHaveFocus()

    for (let i = 0; i < 8; i++) await userEvent.click(addButton())
    expect(screen.queryByRole('button', { name: '＋ 종목 더하기' })).toBeNull()
    expect(screen.getByText('최대 10개')).toBeInTheDocument()
  })

  it('지우면 그 줄만 빼고 그 자리 줄(없으면 앞 줄) 종목 칸으로 간다', async () => {
    render(<Harness initial={[row('SPY', '1'), row('QQQ', '2'), row('MSFT', '3')]} />)

    await userEvent.click(removeButton(2))
    expect(symbolInput(2)).toHaveValue('MSFT')
    expect(amountInput(2)).toHaveValue('3')
    expect(symbolInput(2)).toHaveFocus()

    await userEvent.click(removeButton(2))
    expect(screen.queryByRole('combobox', { name: '2번 종목' })).toBeNull()
    expect(symbolInput(1)).toHaveFocus()
  })

  it('종목 칸에서 목록의 종목을 고르면 그 줄 종목만 바꾼다', async () => {
    const onRows = vi.fn()
    render(
      <Harness
        initial={[row('SPY', '1'), row('', '2')]}
        patch={{ onRowsChange: onRows, rows: [row('SPY', '1'), row('', '2')] }}
      />
    )

    await userEvent.type(symbolInput(2), 'MSF')
    await userEvent.click(screen.getByRole('option', { name: /MSFT/ }))
    expect(onRows).toHaveBeenLastCalledWith([row('SPY', '1'), row('MSFT', '2')])
  })

  it('오류가 있는 칸만 오류로 두고 칸 아래 한 줄은 앞 것 하나, 그 글을 낸 칸만 그 줄과 잇는다', () => {
    render(
      <Harness
        initial={[row('SPY', '1'), row('', '0')]}
        patch={{ messages: [ok, { ...ok, symbol: error('2번 줄 종목 비어 있음'), value: error('2번 줄 금액 0 보다 크게') }] }}
      />
    )

    expect(screen.getByText('2번 줄 종목 비어 있음')).toBeInTheDocument()
    expect(screen.queryByText('2번 줄 금액 0 보다 크게')).toBeNull()
    expect(symbolInput(1)).not.toHaveAttribute('aria-invalid')
    expect(symbolInput(2)).toHaveAttribute('aria-invalid', 'true')
    expect(amountInput(2)).toHaveAttribute('aria-invalid', 'true')
    expect(symbolInput(2)).toHaveAccessibleDescription('2번 줄 종목 비어 있음')
    expect(amountInput(2)).not.toHaveAttribute('aria-describedby')
  })

  it('줄마다 전략은 방식대로면 방식(한 번에 · 적립식)을 보이고, 고르면 그 줄만 바꾸며 방식과 같은 전략을 고르면 방식대로로 둔다', async () => {
    const onRows = vi.fn()
    const rows = [row('SPY', '1'), { ...row('QQQ', '2'), strategy: 'DCA' as const }]
    const { rerender } = render(<Harness initial={rows} patch={{ onRowsChange: onRows, rows }} />)

    expect(within(strategySelect(1)).getAllByRole('option').map((option) => option.textContent)).toEqual([
      '한 번에',
      '적립식',
      '이동평균',
      '밴드',
    ])
    expect(strategySelect(1)).toHaveValue('LUMP_SUM')
    expect(strategySelect(2)).toHaveValue('DCA')
    await userEvent.selectOptions(strategySelect(1), '이동평균')
    expect(onRows).toHaveBeenLastCalledWith([{ ...row('SPY', '1'), strategy: 'MA_CROSS' }, rows[1]])
    await userEvent.selectOptions(strategySelect(2), '한 번에')
    expect(onRows).toHaveBeenLastCalledWith([rows[0], { ...rows[1], strategy: '' }])

    rerender(<Harness initial={rows} patch={{ onRowsChange: onRows, rows, method: 'DCA' }} />)
    expect(strategySelect(1)).toHaveValue('DCA')
    await userEvent.selectOptions(strategySelect(1), '한 번에')
    expect(onRows).toHaveBeenLastCalledWith([{ ...rows[0], strategy: 'LUMP_SUM' }, rows[1]])
  })

  it('이동평균 줄은 짧은 · 긴 기간 칸, 밴드 줄은 평균 기간 · 위 + · 아래 - 비율 칸을 그 줄에 보이고 값은 그 줄에만 넣는다', async () => {
    render(<Harness initial={[row('SPY', '1'), row('QQQ', '2')]} />)

    expect(screen.queryByRole('textbox', { name: '1번 짧은 기간(일)' })).toBeNull()
    expect(screen.queryByRole('textbox', { name: '1번 평균 기간(일)' })).toBeNull()
    await userEvent.selectOptions(strategySelect(1), '이동평균')
    expect(shortInput(1)).toHaveValue('20')
    expect(longInput(1)).toHaveValue('60')
    fireEvent.change(shortInput(1), { target: { value: '5일' } })
    fireEvent.change(longInput(1), { target: { value: '3.0' } })
    expect(shortInput(1)).toHaveValue('5')
    expect(longInput(1)).toHaveValue('30')
    expect(screen.queryByRole('textbox', { name: '2번 짧은 기간(일)' })).toBeNull()

    await userEvent.selectOptions(strategySelect(2), '밴드')
    expect(bandWindowInput(2)).toHaveValue('200')
    expect(upperInput(2)).toHaveValue('2.5')
    expect(lowerInput(2)).toHaveValue('2.5')
    fireEvent.change(upperInput(2), { target: { value: '1..5%' } })
    fireEvent.change(lowerInput(2), { target: { value: '0' } })
    fireEvent.change(bandWindowInput(2), { target: { value: '120.5' } })
    expect(upperInput(2)).toHaveValue('1.5')
    expect(lowerInput(2)).toHaveValue('0')
    expect(bandWindowInput(2)).toHaveValue('1205')
    expect(shortInput(1)).toHaveValue('5')

    await userEvent.selectOptions(strategySelect(1), '한 번에')
    expect(screen.queryByRole('textbox', { name: '1번 짧은 기간(일)' })).toBeNull()
    await userEvent.selectOptions(strategySelect(1), '이동평균')
    expect(shortInput(1)).toHaveValue('5')
  })

  it('이동평균 · 밴드 칸 오류는 익절 · 손절 오류 다음 차례로 칸 아래에 보이고 그 글을 낸 칸만 그 줄과 잇는다', () => {
    const cross = { ...row('SPY', '1'), strategy: 'MA_CROSS' as const, shortWindow: '60' }
    const band = { ...row('QQQ', '1'), strategy: 'MA_BAND' as const, upper: '' }
    const { unmount } = render(
      <Harness
        initial={[cross, band]}
        patch={{
          messages: [
            { ...ok, shortWindow: error('1번 줄 짧은 기간 긴 기간보다 짧게') },
            { ...ok, stopLoss: error('2번 줄 손절 100 아래'), upper: error('2번 줄 위 비율 비어 있음') },
          ],
        }}
      />
    )

    expect(screen.getByText('1번 줄 짧은 기간 긴 기간보다 짧게')).toBeInTheDocument()
    expect(shortInput(1)).toHaveAttribute('aria-invalid', 'true')
    expect(shortInput(1)).toHaveAccessibleDescription('1번 줄 짧은 기간 긴 기간보다 짧게')
    expect(longInput(1)).not.toHaveAttribute('aria-invalid')
    expect(upperInput(2)).toHaveAttribute('aria-invalid', 'true')
    expect(upperInput(2)).not.toHaveAttribute('aria-describedby')
    unmount()

    render(<Harness initial={[band]} patch={{ messages: [{ ...ok, stopLoss: error('1번 줄 손절 100 아래'), upper: error('1번 줄 위 비율 비어 있음') }] }} />)
    expect(screen.getByText('1번 줄 손절 100 아래')).toBeInTheDocument()
    expect(screen.queryByText('1번 줄 위 비율 비어 있음')).toBeNull()
  })

  it('비율 방식 총액 오류는 줄 오류보다 먼저 칸 아래에 보인다', async () => {
    render(
      <Harness
        initial={[row('', '1', '100')]}
        patch={{ totalMessage: error('총액 비어 있음'), messages: [{ ...ok, symbol: error('1번 줄 종목 비어 있음') }] }}
      />
    )
    await userEvent.click(screen.getByRole('radio', { name: '비율' }))

    expect(screen.getByText('총액 비어 있음')).toBeInTheDocument()
    expect(screen.queryByText('1번 줄 종목 비어 있음')).toBeNull()
    expect(screen.getByRole('textbox', { name: '총액(원)' })).toHaveAccessibleDescription('총액 비어 있음')
  })
})
