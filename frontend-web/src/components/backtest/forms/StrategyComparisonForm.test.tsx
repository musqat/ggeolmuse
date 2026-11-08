import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ComponentProps } from 'react'
import { StrategyComparisonForm } from './StrategyComparisonForm'
import type { BacktestOptionsState } from '../shared/backtestOptions'

const options: BacktestOptionsState = {
  reinvestDividends: true, setReinvestDividends: vi.fn(), tradingFeeRate: '0', setTradingFeeRate: vi.fn(),
  dividendTax: false, setDividendTax: vi.fn(), fxMode: 'auto', setFxMode: vi.fn(),
  manualPurchaseFxRate: '1300', setManualPurchaseFxRate: vi.fn(), manualCurrentFxRate: '1350', setManualCurrentFxRate: vi.fn(),
}

type Props = ComponentProps<typeof StrategyComparisonForm>

const chip = (id: string, type: 'SIMPLE' | 'DCA' | 'CONDITIONAL_PURCHASE', name: string, patch = {}) => ({
  id,
  type,
  name,
  error: null,
  short: false,
  ...patch,
})

function renderForm(patch: Partial<Props> = {}) {
  const props: Props = {
    symbol: { symbol: 'AAPL', onChange: vi.fn(), supportedSymbols: ['AAPL'] },
    period: {
      period: {
        start: '2023-01-02', end: '2023-12-29', agoCount: '1', agoUnit: 'YEAR', agoOn: false,
        setStart: vi.fn(), setEnd: vi.fn(), setAgoCount: vi.fn(), setAgoUnit: vi.fn(),
      },
    },
    amount: { label: '전략마다 원금', amount: '1000000', onAmountChange: vi.fn() },
    chips: [chip('s1', 'SIMPLE', '단순 매수'), chip('s2', 'DCA', '적립식')],
    openId: 's2',
    onOpen: vi.fn(),
    onRemove: vi.fn(),
    onAdd: vi.fn(),
    panel: <p>열린 칩 설정</p>,
    options,
    ...patch,
  }
  render(<StrategyComparisonForm {...props} />)
  return props
}

const chips = () => within(screen.getByRole('group', { name: '견줄 전략' }))

describe('StrategyComparisonForm', () => {
  it('칩은 누르면 아래 설정을 여는 단추이고, 열린 칩의 설정을 보인다', async () => {
    const props = renderForm()

    expect(screen.getByText('전략마다 원금')).toBeInTheDocument()
    expect(chips().getByRole('button', { name: '적립식' })).toHaveAttribute('aria-expanded', 'true')
    expect(chips().getByRole('button', { name: '단순 매수' })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByText('열린 칩 설정')).toBeInTheDocument()
    await userEvent.click(chips().getByRole('button', { name: '단순 매수' }))

    expect(props.onOpen).toHaveBeenCalledWith('s1')
  })

  it('× 로 칩을 빼고 ＋ 로 종류마다 더하며, 단순 매수는 하나만 둔다', async () => {
    const props = renderForm()

    await userEvent.click(chips().getByRole('button', { name: '적립식 빼기' }))
    expect(props.onRemove).toHaveBeenCalledWith('s2')
    expect(chips().queryByRole('button', { name: '＋ 단순 매수' })).toBeNull()
    await userEvent.click(chips().getByRole('button', { name: '＋ 적립식' }))
    await userEvent.click(chips().getByRole('button', { name: '＋ 조건부 매수' }))

    expect(props.onAdd).toHaveBeenNthCalledWith(1, 'DCA')
    expect(props.onAdd).toHaveBeenNthCalledWith(2, 'CONDITIONAL_PURCHASE')
  })

  it('단순 매수 칩이 없으면 ＋ 단순 매수를 보인다', () => {
    renderForm({ chips: [chip('s2', 'DCA', '적립식')] })

    expect(chips().getByRole('button', { name: '＋ 단순 매수' })).toBeInTheDocument()
  })

  it('고칠 칩은 ! 표시와 고칠 것을 이름에 담고, 원금이 모자란 칩은 점선', () => {
    renderForm({
      chips: [
        chip('s1', 'SIMPLE', '단순 매수'),
        chip('s2', 'DCA', '적립식', { short: true }),
        chip('s3', 'CONDITIONAL_PURCHASE', '조건부 매수', { error: '회당 금액이 원금보다 큽니다.' }),
      ],
      chipsMessage: { kind: 'error', text: '고칠 칩 1개(! 표시)는 눌러서 고치기' },
    })

    const bad = chips().getByRole('button', { name: '조건부 매수 (고칠 것: 회당 금액이 원금보다 큽니다.)' })
    expect(bad).toHaveAttribute('data-bad', 'true')
    expect(bad.parentElement).toHaveClass('border-danger')
    expect(chips().getByRole('button', { name: '적립식' }).parentElement).toHaveClass('border-dashed')
    expect(screen.getByText('고칠 칩 1개(! 표시)는 눌러서 고치기')).toBeInTheDocument()
  })

  it('익절 · 손절이 있는 조건부 칩이 있으면 재투자 칸은 켜 둔 채 그 칩만 재투자하지 않는다고 보인다', () => {
    renderForm({ reinvestNote: '익절 · 손절이 있는 조건부 매수는 재투자 안 함' })

    const reinvest = screen.getByRole('checkbox', { name: /배당금 재투자/ })
    expect(reinvest).toBeEnabled()
    expect(reinvest).toBeChecked()
    expect(screen.getByText('익절 · 손절이 있는 조건부 매수는 재투자 안 함')).toBeInTheDocument()
  })
})
