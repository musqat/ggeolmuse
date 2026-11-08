import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { stockApi } from '../../../services/api'
import { SimpleStrategyForm } from './SimpleStrategyForm'
import type { AmountUnitState } from '../shared/amountUnit'

vi.mock('../../../services/api', () => ({
  stockApi: { getOHLCData: vi.fn(), getExchangeRatesBulk: vi.fn() },
}))

// 기대값은 TZ=Asia/Seoul 전제 (vitest.config.ts 에서 고정)
function renderForm(amountUnit: AmountUnitState = { unit: 'AMOUNT', setUnit: vi.fn(), shares: '1', setShares: vi.fn() }) {
  const props = {
    symbol: 'AAPL',
    setSymbol: vi.fn(),
    purchaseDate: '2023-01-01',
    setPurchaseDate: vi.fn(),
    saleDate: '2024-06-01',
    setSaleDate: vi.fn(),
    initialInvestment: '300000',
    setInitialInvestment: vi.fn(),
    amountUnit,
    options: {
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
    },
    supportedSymbols: [],
  }
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <SimpleStrategyForm {...props} />
    </QueryClientProvider>
  )
  return props
}

async function pickDay(day: string) {
  const button = within(screen.getByRole('dialog'))
    .getAllByRole('button')
    .find((b) => b.textContent === day)
  await userEvent.click(button!)
}

describe('SimpleStrategyForm 날짜', () => {
  it('달력에서 고른 매수일을 그 날짜 그대로 넘긴다', async () => {
    const props = renderForm()

    await userEvent.click(screen.getByTestId('date-start'))
    await pickDay('14')

    expect(props.setPurchaseDate).toHaveBeenLastCalledWith('2023-01-14')
  })

  it('달력에서 고른 매도일을 그 날짜 그대로 넘긴다', async () => {
    const props = renderForm()

    await userEvent.click(screen.getByTestId('date-end'))
    await pickDay('14')

    expect(props.setSaleDate).toHaveBeenLastCalledWith('2024-06-14')
  })
})

describe('SimpleStrategyForm 금액 · 주식 수', () => {
  it('금액 단위면 초기 투자금 칸을 보인다', () => {
    renderForm()

    expect(screen.getByText('초기 투자금 (₩)')).toBeInTheDocument()
    expect(screen.getByText('최소 약 30만원 권장')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '금액' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('주식 수 단위면 주식 수 칸을 보이고 매수일 시세로 금액을 추정한다', async () => {
    vi.mocked(stockApi.getOHLCData).mockResolvedValue({ data: [] } as never)
    renderForm({ unit: 'SHARES', setUnit: vi.fn(), shares: '1', setShares: vi.fn() })

    expect(screen.getByLabelText('주식 수')).toHaveValue(1)
    expect(screen.queryByText('초기 투자금 (₩)')).toBeNull()
    expect(screen.queryByText('최소 약 30만원 권장')).toBeNull()
    await vi.waitFor(() => expect(stockApi.getOHLCData).toHaveBeenCalledWith('AAPL', '2023-01-01', '2023-01-15'))
  })
})
