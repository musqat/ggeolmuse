import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SimpleStrategyForm } from './SimpleStrategyForm'

// 기대값은 TZ=Asia/Seoul 전제 (vitest.config.ts 에서 고정)
function renderForm() {
  const props = {
    symbol: 'AAPL',
    setSymbol: vi.fn(),
    purchaseDate: '2023-01-01',
    setPurchaseDate: vi.fn(),
    saleDate: '2024-06-01',
    setSaleDate: vi.fn(),
    initialInvestment: '300000',
    setInitialInvestment: vi.fn(),
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
    supportedSymbols: [],
  }
  render(<SimpleStrategyForm {...props} />)
  return props
}

async function pickDay(day: string) {
  const button = within(screen.getByRole('dialog'))
    .getAllByRole('button')
    .find((b) => b.textContent === day)
  await userEvent.click(button!)
}

describe('SimpleStrategyForm 날짜', () => {
  it('달력에서 고른 매수일을 그날 그대로 넘긴다', async () => {
    const props = renderForm()

    await userEvent.click(screen.getByTestId('date-start'))
    await pickDay('14')

    expect(props.setPurchaseDate).toHaveBeenLastCalledWith('2023-01-14')
  })

  it('달력에서 고른 매도일을 그날 그대로 넘긴다', async () => {
    const props = renderForm()

    await userEvent.click(screen.getByTestId('date-end'))
    await pickDay('14')

    expect(props.setSaleDate).toHaveBeenLastCalledWith('2024-06-14')
  })
})
