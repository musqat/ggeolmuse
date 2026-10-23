import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { stockApi } from '../services/api'
import Home from './Home'

vi.mock('../services/api', () => ({
  stockApi: { getAllStocksWithPrices: vi.fn(), getCurrentPrices: vi.fn(), getOHLCData: vi.fn() },
}))

const STOCKS = [
  { symbol: 'AAPL', name: 'Apple Inc.', currentPrice: 105, available: true },
  { symbol: 'MSFT', name: 'Microsoft', currentPrice: 420.5, available: true },
  { symbol: 'NVDA', name: 'NVIDIA', currentPrice: 130, available: true },
  { symbol: 'GOOGL', name: 'Alphabet', currentPrice: 170.25, available: true },
  { symbol: 'AMZN', name: 'Amazon', currentPrice: 190, available: true },
]
const OHLC = [
  { date: '2026-10-08', closePrice: 100, adjustedClose: 100 },
  { date: '2026-10-09', closePrice: 105, adjustedClose: 105 },
]

// 이동한 경로
function Where() {
  return <p>{useLocation().pathname}</p>
}

function renderHome() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(stockApi.getAllStocksWithPrices).mockResolvedValue({ data: { content: STOCKS } } as never)
  vi.mocked(stockApi.getCurrentPrices).mockResolvedValue({
    data: { MSFT: { changePercent: 1.2 }, NVDA: { changePercent: -0.5 } },
  } as never)
  vi.mocked(stockApi.getOHLCData).mockResolvedValue({ data: OHLC } as never)
})

describe('Home', () => {
  it('시총 1위는 큰 칸에 종가 날짜와 등락을, 2 ~ 5위는 목록에 값과 등락을 보인다', async () => {
    renderHome()

    expect(await screen.findByText('AAPL · 10월 9일 종가')).toBeInTheDocument()
    // 100 → 105
    expect(screen.getByText('▲ 5.00%')).toBeInTheDocument()
    expect(screen.getByText('$420.50')).toBeInTheDocument()
    expect(await screen.findByText('▲ 1.20%')).toBeInTheDocument()
    expect(screen.getByText('▼ 0.50%')).toBeInTheDocument()
  })

  it('거래할 수 없거나 가격이 없는 종목은 뺀다', async () => {
    vi.mocked(stockApi.getAllStocksWithPrices).mockResolvedValue({
      data: {
        content: [
          STOCKS[0],
          { symbol: 'XYZ', name: 'Delisted', currentPrice: 10, available: false },
          { symbol: 'NOP', name: 'No Price', currentPrice: null, available: true },
          STOCKS[1],
        ],
      },
    } as never)
    renderHome()

    expect(await screen.findByText('$420.50')).toBeInTheDocument()
    expect(screen.queryByText('XYZ')).toBeNull()
    expect(screen.queryByText('NOP')).toBeNull()
  })

  it('시세를 못 받으면 자리 표시 넷과 대시를 보인다', async () => {
    vi.mocked(stockApi.getAllStocksWithPrices).mockRejectedValue(new Error('down'))
    renderHome()

    expect(await screen.findByText('시총 1위')).toBeInTheDocument()
    for (const symbol of ['AAPL', 'MSFT', 'GOOGL', 'AMZN']) {
      expect(screen.getByText(symbol)).toBeInTheDocument()
    }
    expect(screen.getAllByText('—')).toHaveLength(5)
  })

  it('목록 종목을 누르면 그 종목 차트로 간다', async () => {
    const user = userEvent.setup()
    renderHome()

    await user.click(await screen.findByText('MSFT'))

    expect(screen.getByText('/charts/MSFT')).toBeInTheDocument()
  })

  it.each([
    ['종목 둘러보기', '/stocks'],
    ['매매 해보기', '/trading'],
    ['백테스트 해보기', '/backtest'],
  ])('%s 버튼은 %s 로 간다', async (label, path) => {
    const user = userEvent.setup()
    renderHome()

    await user.click(screen.getByRole('button', { name: label }))

    expect(screen.getByText(path)).toBeInTheDocument()
  })
})
