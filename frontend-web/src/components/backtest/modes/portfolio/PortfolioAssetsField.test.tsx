import { describe, it, expect, vi } from 'vitest'
import { useState } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PortfolioAllocation } from '@services/api'
import { PortfolioAssetsField, type PortfolioAssetsFieldProps } from './PortfolioAssetsField'
import type { PortfolioRow } from '../../shared/portfolioRows'

const ok = { symbol: null, value: null }
const error = (text: string) => ({ kind: 'error' as const, text })
const row = (symbol: string, amount = '', weight = ''): PortfolioRow => ({ symbol, amount, weight })

const symbolInput = (n: number) => screen.getByRole('combobox', { name: `${n}번 종목` })
const amountInput = (n: number) => screen.getByRole('textbox', { name: `${n}번 금액(원)` })
const weightInput = (n: number) => screen.getByRole('textbox', { name: `${n}번 비율(퍼센트)` })
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
        patch={{ messages: [ok, { symbol: error('2번 줄 종목 비어 있음'), value: error('2번 줄 금액 0 보다 크게') }] }}
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

  it('비율 방식 총액 오류는 줄 오류보다 먼저 칸 아래에 보인다', async () => {
    render(
      <Harness
        initial={[row('', '1', '100')]}
        patch={{ totalMessage: error('총액 비어 있음'), messages: [{ symbol: error('1번 줄 종목 비어 있음'), value: null }] }}
      />
    )
    await userEvent.click(screen.getByRole('radio', { name: '비율' }))

    expect(screen.getByText('총액 비어 있음')).toBeInTheDocument()
    expect(screen.queryByText('1번 줄 종목 비어 있음')).toBeNull()
    expect(screen.getByRole('textbox', { name: '총액(원)' })).toHaveAccessibleDescription('총액 비어 있음')
  })
})
