import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrategyComparisonForm } from './StrategyComparisonForm'
import type { ComparisonStrategyType, StrategyParams } from '../comparison/types'

const options = {
  fxMode: 'auto' as const,
  setFxMode: vi.fn(),
  manualPurchaseFxRate: '1300',
  setManualPurchaseFxRate: vi.fn(),
  manualCurrentFxRate: '1350',
  setManualCurrentFxRate: vi.fn(),
  reinvestDividends: true,
  setReinvestDividends: vi.fn(),
  tradingFeeRate: '0',
  setTradingFeeRate: vi.fn(),
  dividendTax: false,
  setDividendTax: vi.fn(),
}

const DCA = { amountPerPurchase: '100000', cadence: 'MONTHLY', months: '3', firstPurchaseDate: '' }
const CONDITIONAL = { amountPerPurchase: '100000', maxPurchases: '', dropPercentage: '5', direction: 'DROP' }

function renderForm(patch: {
  investment?: string
  selectedStrategies?: ComparisonStrategyType[]
  strategyParameters?: Record<string, StrategyParams>
} = {}) {
  const props = {
    symbol: 'AAPL',
    setSymbol: vi.fn(),
    supportedSymbols: [],
    startDate: '2023-01-01',
    setStartDate: vi.fn(),
    endDate: '2023-12-31',
    setEndDate: vi.fn(),
    investment: '1000000',
    setInvestment: vi.fn(),
    selectedStrategies: ['SIMPLE', 'DCA'] as ComparisonStrategyType[],
    strategyParameters: { DCA } as Record<string, StrategyParams>,
    toggleStrategy: vi.fn(),
    editStrategy: vi.fn(),
    options,
    ...patch,
  }
  render(<StrategyComparisonForm {...props} />)
  return props
}

const chips = () => within(screen.getByRole('group', { name: '비교할 전략' }))

describe('StrategyComparisonForm', () => {
  it('금액 칸 이름은 전략마다 원금이고 전략 단추는 고른 상태를 aria-pressed 로 보인다', async () => {
    const props = renderForm()

    expect(screen.getByText('전략마다 원금 (₩)')).toBeInTheDocument()
    expect(chips().getByRole('button', { name: '적립식' })).toHaveAttribute('aria-pressed', 'true')
    expect(chips().getByRole('button', { name: '조건부 매수' })).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(chips().getByRole('button', { name: '조건부 매수' }))
    expect(props.toggleStrategy).toHaveBeenCalledWith('CONDITIONAL_PURCHASE')
  })

  it('고른 전략마다 요약 한 줄을 보이고 원금이 회차보다 모자란 적립식은 경고 색으로 보인다', () => {
    renderForm()

    expect(screen.getByText('단순 매수: 시작일에 ₩1,000,000 매수')).toBeInTheDocument()
    expect(
      screen.getByText('적립식: 매달 1일 ₩100,000 · 12회 중 10회 ≈ ₩1,000,000, 나머지 2회는 원금이 모자라 미매수')
    ).toHaveClass('text-warning')
  })

  it('설정 단추는 그 전략을 고치게 넘기고 단순 매수에는 없다', async () => {
    const props = renderForm()

    await userEvent.click(screen.getByRole('button', { name: '적립식 설정' }))

    expect(props.editStrategy).toHaveBeenCalledWith('DCA')
    expect(screen.queryByRole('button', { name: '단순 매수 설정' })).toBeNull()
  })

  it('원금 · 기간과 안 맞는 칩은 요약 자리에 그 칩의 오류를 보인다', () => {
    renderForm({ investment: '50000' })

    expect(screen.getByText('적립식: 회당 금액이 원금보다 큽니다.')).toHaveClass('text-danger')
  })

  it('익절 · 손절이 있는 조건부 매수를 고르면 재투자 칸은 켜 둔 채 그 칩만 재투자하지 않는다고 보인다', () => {
    renderForm({
      selectedStrategies: ['SIMPLE', 'CONDITIONAL_PURCHASE'],
      strategyParameters: { CONDITIONAL_PURCHASE: { ...CONDITIONAL, takeProfit: '20' } },
    })

    const reinvest = screen.getByRole('checkbox', { name: /배당금 재투자/ })
    expect(reinvest).toBeEnabled()
    expect(reinvest).toBeChecked()
    expect(screen.getByText('익절 · 손절이 있는 조건부 매수는 재투자 안 함')).toBeInTheDocument()
  })
})
