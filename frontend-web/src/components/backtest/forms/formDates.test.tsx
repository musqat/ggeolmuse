import { describe, it, expect, vi } from 'vitest'
import type { ReactElement } from 'react'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DCAStrategyForm } from './DCAStrategyForm'
import { ConditionalStrategyForm } from './ConditionalStrategyForm'
import { StrategyComparisonForm } from './StrategyComparisonForm'
import { SymbolComparisonForm } from './SymbolComparisonForm'

// 기대값은 TZ=Asia/Seoul 전제 (vitest.config.ts 에서 고정)
const options = {
  fxMode: 'auto' as const,
  setFxMode: vi.fn(),
  manualPurchaseFxRate: '1300',
  setManualPurchaseFxRate: vi.fn(),
  manualCurrentFxRate: '1350',
  setManualCurrentFxRate: vi.fn(),
  reinvestDividends: false,
  setReinvestDividends: vi.fn(),
  tradingFeeRate: '0',
  setTradingFeeRate: vi.fn(),
  dividendTax: false,
  setDividendTax: vi.fn(),
}

interface Period {
  start: string
  end: string
  setStart: (date: string) => void
  setEnd: (date: string) => void
}

// 폼마다 시작일 · 종료일 자리에 같은 값과 setter 를 넣어 그림
const FORMS: [string, (p: Period) => ReactElement][] = [
  ['적립식', (p) => (
    <DCAStrategyForm
      symbol="AAPL" setSymbol={vi.fn()} supportedSymbols={[]} options={options}
      dcaStartDate={p.start} setDcaStartDate={p.setStart} dcaEndDate={p.end} setDcaEndDate={p.setEnd}
      monthlyAmount="100000" setMonthlyAmount={vi.fn()} purchaseDay="15" setPurchaseDay={vi.fn()}
      investmentInterval="1" setInvestmentInterval={vi.fn()}
    />
  )],
  ['조건부', (p) => (
    <ConditionalStrategyForm
      symbol="AAPL" setSymbol={vi.fn()} supportedSymbols={[]} options={options}
      conditionalStartDate={p.start} setConditionalStartDate={p.setStart}
      conditionalEndDate={p.end} setConditionalEndDate={p.setEnd}
      investmentMode="TOTAL_BUDGET" setInvestmentMode={vi.fn()} totalInvestment="1000000" setTotalInvestment={vi.fn()}
      amountPerPurchase="100000" setAmountPerPurchase={vi.fn()} maxPurchases="10" setMaxPurchases={vi.fn()}
      dropPercentage="5" setDropPercentage={vi.fn()}
    />
  )],
  ['전략 비교', (p) => (
    <StrategyComparisonForm
      symbol="AAPL" setSymbol={vi.fn()} supportedSymbols={[]} options={options}
      startDate={p.start} setStartDate={p.setStart} endDate={p.end} setEndDate={p.setEnd}
      investment="1000000" setInvestment={vi.fn()} selectedStrategies={[]} toggleStrategy={vi.fn()}
    />
  )],
  ['종목 비교', (p) => (
    <SymbolComparisonForm
      compareSymbols={[]} setCompareSymbols={vi.fn()} compareSymbolInput="" setCompareSymbolInput={vi.fn()}
      comparePurchaseDate={p.start} setComparePurchaseDate={p.setStart}
      compareSaleDate={p.end} setCompareSaleDate={p.setEnd}
      compareInvestment="1000000" setCompareInvestment={vi.fn()} options={options} supportedSymbols={[]}
      onAddSymbol={vi.fn()} onRemoveSymbol={vi.fn()}
    />
  )],
]

async function pickDay(day: string) {
  const button = within(screen.getByRole('dialog'))
    .getAllByRole('button')
    .find((b) => b.textContent === day)
  await userEvent.click(button!)
}

describe.each(FORMS)('%s 폼 날짜', (_, renderForm) => {
  it('달력에서 고른 시작일 · 종료일을 그 폼의 날짜로 넘긴다', async () => {
    const setStart = vi.fn()
    const setEnd = vi.fn()
    render(renderForm({ start: '2023-01-01', end: '2024-06-01', setStart, setEnd }))

    await userEvent.click(screen.getByTestId('date-start'))
    await pickDay('14')
    await userEvent.click(screen.getByTestId('date-end'))
    await pickDay('14')

    expect(setStart).toHaveBeenLastCalledWith('2023-01-14')
    expect(setEnd).toHaveBeenLastCalledWith('2024-06-14')
  })
})
