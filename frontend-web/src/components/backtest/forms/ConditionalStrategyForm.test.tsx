import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { ComponentProps } from 'react'
import { ConditionalStrategyForm } from './ConditionalStrategyForm'
import type { BacktestOptionsState } from '../shared/backtestOptions'

const options: BacktestOptionsState = {
  reinvestDividends: false, setReinvestDividends: vi.fn(), tradingFeeRate: '0', setTradingFeeRate: vi.fn(),
  dividendTax: false, setDividendTax: vi.fn(), fxMode: 'auto', setFxMode: vi.fn(),
  manualPurchaseFxRate: '1300', setManualPurchaseFxRate: vi.fn(), manualCurrentFxRate: '1350', setManualCurrentFxRate: vi.fn(),
}

type Props = ComponentProps<typeof ConditionalStrategyForm>

const renderForm = (patch: Partial<Props> = {}) =>
  render(
    <ConditionalStrategyForm
      symbol="AAPL" setSymbol={vi.fn()} supportedSymbols={[]} options={options}
      conditionalStartDate="2023-01-01" setConditionalStartDate={vi.fn()}
      conditionalEndDate="" setConditionalEndDate={vi.fn()}
      amountPerPurchase="100000" setAmountPerPurchase={vi.fn()} totalInvestment="1000000" setTotalInvestment={vi.fn()}
      maxPurchases="" setMaxPurchases={vi.fn()}
      direction="DROP" setDirection={vi.fn()} dropPercentage="5" setDropPercentage={vi.fn()}
      takeProfit="" setTakeProfit={vi.fn()} stopLoss="" setStopLoss={vi.fn()}
      {...patch}
    />
  )

describe('ConditionalStrategyForm', () => {
  it('투자 모드 단추 없이 회당 금액 · 예산 · 최대 횟수 칸을 두고 예산으로 살 수 있는 횟수를 보인다', () => {
    renderForm()

    expect(screen.queryByText('총 예산 분할')).toBeNull()
    expect(screen.queryByText('회당 고정 금액')).toBeNull()
    expect(screen.getByText('회당 금액 (₩)')).toBeInTheDocument()
    expect(screen.getByText('예산 (₩)')).toBeInTheDocument()
    expect(screen.getByText('예산으로 최대 10회')).toBeInTheDocument()
    expect(screen.getByLabelText('최대 횟수')).toHaveAttribute('placeholder', '예산 한도까지')
  })

  it('예산을 비우면 두 칸 모두 제한 없음으로 안내한다', () => {
    renderForm({ totalInvestment: '' })

    expect(screen.queryByText(/예산으로 최대/)).toBeNull()
    expect(screen.getByLabelText('최대 횟수')).toHaveAttribute('placeholder', '제한 없음')
    expect(screen.getAllByPlaceholderText('제한 없음')).toHaveLength(2)
  })

  it('최대 횟수를 적으면 회당 금액 × 횟수의 투자 한도를 보인다', () => {
    renderForm({ totalInvestment: '', maxPurchases: '20' })

    expect(screen.getByText('많아야 ₩2,000,000 투자')).toBeInTheDocument()
  })

  it('예산이 회당 금액 × 횟수보다 작으면 투자 한도를 예산으로 보인다', () => {
    renderForm({ totalInvestment: '1000000', maxPurchases: '20' })

    expect(screen.getByText('많아야 ₩1,000,000 투자')).toBeInTheDocument()
  })
})
