import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps } from 'react'
import { stockApi } from '../../../services/api'
import { DCAStrategyForm } from './DCAStrategyForm'
import type { BacktestOptionsState } from '../shared/backtestOptions'

vi.mock('../../../services/api', () => ({
  stockApi: { getOHLCData: vi.fn(), getExchangeRatesBulk: vi.fn() },
}))

const options: BacktestOptionsState = {
  reinvestDividends: false, setReinvestDividends: vi.fn(), tradingFeeRate: '0', setTradingFeeRate: vi.fn(),
  dividendTax: false, setDividendTax: vi.fn(), fxMode: 'auto', setFxMode: vi.fn(),
  manualPurchaseFxRate: '1300', setManualPurchaseFxRate: vi.fn(), manualCurrentFxRate: '1350', setManualCurrentFxRate: vi.fn(),
}

type Props = ComponentProps<typeof DCAStrategyForm>

const renderForm = (patch: Partial<Props> = {}) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <DCAStrategyForm
        symbol="AAPL" setSymbol={vi.fn()} supportedSymbols={[]} options={options}
        dcaStartDate="2023-01-01" setDcaStartDate={vi.fn()} dcaEndDate="2024-06-30" setDcaEndDate={vi.fn()}
        amountPerPurchase="100000" setAmountPerPurchase={vi.fn()}
        amountUnit={{ unit: 'AMOUNT', setUnit: vi.fn(), shares: '1', setShares: vi.fn() }}
        cadence="MONTHLY" setCadence={vi.fn()} months="3" setMonths={vi.fn()}
        firstPurchaseDate="" setFirstPurchaseDate={vi.fn()}
        {...patch}
      />
    </QueryClientProvider>
  )

describe('DCAStrategyForm', () => {
  it('회당 금액 칸과 매일 · 매주 · 매달 · N개월마다 주기를 두고, 월 투자금 · 매월 투자일 칸은 두지 않는다', () => {
    renderForm()

    expect(screen.getByText('회당 금액 (₩)')).toBeInTheDocument()
    expect(screen.queryByText('월 투자금 (₩)')).toBeNull()
    expect(screen.queryByText('매월 투자일')).toBeNull()
    const cadence = screen.getByLabelText('투자 주기')
    expect(within(cadence).getAllByRole('option').map((option) => option.textContent)).toEqual([
      '매일',
      '매주',
      '매달',
      'N개월마다',
    ])
    expect(cadence).toHaveValue('MONTHLY')
    expect(screen.queryByLabelText('몇 개월마다')).toBeNull()
  })

  it('주식 수 단위면 회당 주식 수 칸을 보이고 시작일 시세로 회당 금액을 추정한다', async () => {
    vi.mocked(stockApi.getOHLCData).mockResolvedValue({ data: [] } as never)
    renderForm({ amountUnit: { unit: 'SHARES', setUnit: vi.fn(), shares: '2', setShares: vi.fn() } })

    expect(screen.getByLabelText('회당 주식 수')).toHaveValue(2)
    expect(screen.queryByText('회당 금액 (₩)')).toBeNull()
    await vi.waitFor(() => expect(stockApi.getOHLCData).toHaveBeenCalledWith('AAPL', '2023-01-01', '2023-01-15'))
  })

  it('고른 주기를 넘긴다', async () => {
    const setCadence = vi.fn()
    renderForm({ setCadence })

    await userEvent.selectOptions(screen.getByLabelText('투자 주기'), 'WEEKLY')

    expect(setCadence).toHaveBeenCalledWith('WEEKLY')
  })

  it('N개월마다면 몇 개월마다 칸을 보이고 고친 값을 넘긴다', async () => {
    const setMonths = vi.fn()
    renderForm({ cadence: 'MONTHS', setMonths })

    const months = screen.getByLabelText('몇 개월마다')
    expect(months).toHaveValue(3)
    await userEvent.type(months, '6')

    expect(setMonths).toHaveBeenLastCalledWith('36')
    expect(screen.getByText('3개월마다 1일')).toBeInTheDocument()
  })

  it('첫 매수일이 비면 시작일을 보이고 그 날짜로 정한 매수 날짜를 안내한다', () => {
    renderForm()

    expect(screen.getByTestId('date-first')).toHaveTextContent('2023. 01. 01.')
    expect(screen.getByText('매달 1일')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '시작일에 맞추기' })).toBeNull()
  })

  it('매주면 첫 매수일의 요일을 안내한다', () => {
    renderForm({ cadence: 'WEEKLY', firstPurchaseDate: '2023-01-04' })

    expect(screen.getByText('매주 수요일')).toBeInTheDocument()
  })

  it('달력에서 고른 첫 매수일을 시작일 ~ 종료일 안에서 넘긴다', async () => {
    const setFirstPurchaseDate = vi.fn()
    renderForm({ setFirstPurchaseDate })

    await userEvent.click(screen.getByTestId('date-first'))
    const dialog = within(screen.getByRole('dialog'))
    await userEvent.click(dialog.getAllByRole('button').find((b) => b.textContent === '14')!)

    expect(setFirstPurchaseDate).toHaveBeenCalledWith('2023-01-14')
  })

  it('고른 첫 매수일이 있으면 시작일에 맞추기 단추로 비운다', async () => {
    const setFirstPurchaseDate = vi.fn()
    renderForm({ firstPurchaseDate: '2023-01-31', setFirstPurchaseDate })

    expect(screen.getByTestId('date-first')).toHaveTextContent('2023. 01. 31.')
    expect(screen.getByText('매달 31일 · 31일이 없는 달은 그 달 마지막 거래일')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '시작일에 맞추기' }))

    expect(setFirstPurchaseDate).toHaveBeenCalledWith('')
  })
})
