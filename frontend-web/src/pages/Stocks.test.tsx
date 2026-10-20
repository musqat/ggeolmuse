import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import Stocks from './Stocks'
import { stockApi } from '../services/api'

vi.mock('../services/api', () => ({ stockApi: { getAllStocksWithPrices: vi.fn() } }))

const getAllStocksWithPrices = vi.mocked(stockApi.getAllStocksWithPrices)

const stock = (symbol: string, dividendYieldPercent: number | null) => ({
  symbol,
  name: `${symbol} Inc`,
  assetType: 'EQUITY',
  marketCap: 1_000_000_000,
  currentPrice: 100,
  date: '2026-10-19',
  available: true,
  dividendYieldPercent,
})

const page = (content: ReturnType<typeof stock>[], totalPages = 1) =>
  ({ data: { content, totalPages, totalElements: content.length * totalPages } }) as never

function renderStocks() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Stocks />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

beforeEach(() => {
  getAllStocksWithPrices.mockReset()
})

describe('Stocks 정렬과 배당수익률', () => {
  it('처음에는 시가총액 순으로 받고, 배당수익률 열에 값을 소수 둘째 자리 % 로, 없으면 - 로 보여 준다', async () => {
    getAllStocksWithPrices.mockResolvedValue(page([stock('AAPL', 0.4), stock('NVDA', null)]))
    renderStocks()

    const aapl = (await screen.findByText('AAPL')).closest('tr')!
    expect(within(aapl).getByText('0.40%')).toBeInTheDocument()
    expect(within(screen.getByText('NVDA').closest('tr')!).getByText('-')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: '배당수익률' })).toBeInTheDocument()
    expect(getAllStocksWithPrices).toHaveBeenLastCalledWith(0, 50, undefined, 'marketCap')
  })

  it('배당수익률 높은 순을 고르면 첫 페이지부터 그 기준으로 다시 받는다', async () => {
    getAllStocksWithPrices.mockResolvedValue(page([stock('AAPL', 0.4)], 3))
    renderStocks()
    await screen.findByText('AAPL')
    await userEvent.click(screen.getByRole('button', { name: '2' }))
    await waitFor(() => expect(getAllStocksWithPrices).toHaveBeenLastCalledWith(1, 50, undefined, 'marketCap'))

    await userEvent.selectOptions(screen.getByLabelText('정렬'), '배당수익률 높은 순')

    await waitFor(() => expect(getAllStocksWithPrices).toHaveBeenLastCalledWith(0, 50, undefined, 'dividendYield'))
  })

  it('데이터 갱신 날짜는 목록에서 가장 늦은 시세 날짜를 보여 준다', async () => {
    getAllStocksWithPrices.mockResolvedValue(page([{ ...stock('KO', 3.1), date: '2026-10-17' }, stock('AAPL', 0.4)]))
    renderStocks()

    expect(await screen.findByText(/데이터 갱신: 2026년 10월 19일/)).toBeInTheDocument()
  })
})
