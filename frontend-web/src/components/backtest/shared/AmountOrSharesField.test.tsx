import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps } from 'react'
import { stockApi } from '../../../services/api'
import { AmountOrSharesField } from './AmountOrSharesField'
import type { BacktestOptions } from './backtestOptions'

vi.mock('../../../services/api', () => ({
  stockApi: { getOHLCData: vi.fn(), getExchangeRatesBulk: vi.fn() },
}))

const getOHLCData = vi.mocked(stockApi.getOHLCData)
const getExchangeRatesBulk = vi.mocked(stockApi.getExchangeRatesBulk)

const options: BacktestOptions = {
  reinvestDividends: false, tradingFeeRate: '0', dividendTax: false, fxMode: 'auto',
  manualPurchaseFxRate: '1300', manualCurrentFxRate: '1350',
}

type Props = ComponentProps<typeof AmountOrSharesField>

const props = (patch: Partial<Props>): Props => ({
  amountLabel: '회당 금액 (₩)',
  sharesLabel: '회당 주식 수',
  amount: '100000',
  onAmountChange: vi.fn(),
  amountPlaceholder: '100,000',
  amountHelp: '회차마다 넣는 금액',
  amountUnit: { unit: 'AMOUNT', setUnit: vi.fn(), shares: '1', setShares: vi.fn() },
  symbol: 'AAPL',
  startDate: '2023-01-01',
  options,
  ...patch,
})

// 다시 그리는 함수를 돌려줌
const renderField = (patch: Partial<Props> = {}) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const view = (next: Partial<Props>) => (
    <QueryClientProvider client={client}>
      <AmountOrSharesField {...props(next)} />
    </QueryClientProvider>
  )
  const { rerender } = render(view(patch))
  return (next: Partial<Props>) => rerender(view(next))
}

const shares = (value: string, setShares = vi.fn()) => ({ unit: 'SHARES' as const, setUnit: vi.fn(), shares: value, setShares })

beforeEach(() => {
  getOHLCData.mockReset()
  getExchangeRatesBulk.mockReset()
  getOHLCData.mockResolvedValue({ data: [{ symbol: 'AAPL', date: '2023-01-03', closePrice: 125, adjustedClose: 125 }] } as never)
  getExchangeRatesBulk.mockResolvedValue({ data: { '2023-01-03': 1270 } } as never)
})

describe('AmountOrSharesField', () => {
  it('금액 단위면 금액 칸과 안내를 보이고, 주식 수 단추로 단위를 넘긴다', async () => {
    const setUnit = vi.fn()
    renderField({ amountUnit: { unit: 'AMOUNT', setUnit, shares: '1', setShares: vi.fn() } })

    expect(screen.getByText('회당 금액 (₩)')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('100,000')).toHaveValue('100,000')
    expect(screen.getByText('회차마다 넣는 금액')).toBeInTheDocument()
    expect(screen.queryByLabelText('회당 주식 수')).toBeNull()
    const unit = screen.getByRole('group', { name: '단위' })
    expect(within(unit).getByRole('button', { name: '금액' })).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(within(unit).getByRole('button', { name: '주식 수' }))

    expect(setUnit).toHaveBeenCalledWith('SHARES')
    expect(getOHLCData).not.toHaveBeenCalled()
  })

  it('주식 수 단위면 주식 수 칸으로 바꾸고 고친 값을 넘긴다', async () => {
    const setShares = vi.fn()
    renderField({ amountUnit: shares('1', setShares) })

    expect(screen.queryByText('회당 금액 (₩)')).toBeNull()
    expect(screen.queryByPlaceholderText('100,000')).toBeNull()
    expect(screen.getByRole('button', { name: '주식 수' })).toHaveAttribute('aria-pressed', 'true')
    const input = screen.getByLabelText('회당 주식 수')
    expect(input).toHaveValue(1)
    await userEvent.type(input, '2')

    expect(setShares).toHaveBeenLastCalledWith('12')
  })

  it('주식 수 단위면 시작일 종가 × 주식 수 × (1 + 수수료)를 그 날짜 환율로 바꾼 추정 금액을 보인다', async () => {
    renderField({ amountUnit: shares('2'), options: { ...options, tradingFeeRate: '0.25' } })

    expect(await screen.findByText('≈ ₩318,294(시작일 값 기준)')).toBeInTheDocument()
    expect(getOHLCData).toHaveBeenCalledWith('AAPL', '2023-01-01', '2023-01-15')
  })

  it('수동 환율이면 매수 환율로 추정한다', async () => {
    renderField({ amountUnit: shares('2'), options: { ...options, fxMode: 'manual' } })

    expect(await screen.findByText('≈ ₩325,000(시작일 값 기준)')).toBeInTheDocument()
  })

  it('주식 수가 1 이상 정수가 아니면 추정 금액을 지운다', async () => {
    const rerender = renderField({ amountUnit: shares('2') })
    expect(await screen.findByText(/시작일 값 기준/)).toBeInTheDocument()

    rerender({ amountUnit: shares('1.5') })

    expect(screen.queryByText(/시작일 값 기준/)).toBeNull()
  })
})
