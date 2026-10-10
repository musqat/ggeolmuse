import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AxiosError, type AxiosResponse } from 'axios'
import { stockApi, tradeApi } from '../services/api'
import { useAuth } from '../hooks/useAuth'
import { useAccounts } from '@/hooks/useAccounts'
import Trading from './Trading'

vi.mock('../services/api', () => ({
  stockApi: { getAllSymbols: vi.fn(), getOHLCData: vi.fn() },
  tradeApi: { buy: vi.fn(), sell: vi.fn() },
}))
vi.mock('../hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: vi.fn() }))
// 캔버스 차트 · 거래내역 탭 · 매수 가능 패널은 따로 테스트가 있다. 여기서는 주문 흐름만 본다
vi.mock('../components/trading/TradingChartSection', () => ({ default: () => null }))
vi.mock('../components/trading/TradeHistoryTab', () => ({ default: () => <div>거래내역 탭</div> }))
vi.mock('../components/trading/TradingCapacityPanel', () => ({ default: () => null }))

const OHLC = [
  { symbol: 'AAPL', date: '2026-10-08', openPrice: 100, highPrice: 110, lowPrice: 95, closePrice: 105, adjustedClose: 105, volume: 1 },
  { symbol: 'AAPL', date: '2026-10-09', openPrice: 106, highPrice: 112, lowPrice: 101, closePrice: 108, adjustedClose: 108, volume: 1 },
]
const TRADE = { tradeType: 'BUY', symbol: 'AAPL', quantity: 10, price: 108, fee: 1.08, totalAmount: 1081.08, tradeDate: '2026-10-09' }
const httpError = (detail: string) =>
  new AxiosError('fail', 'ERR', undefined, undefined, { status: 400, data: { detail }, statusText: '', headers: {}, config: {} } as AxiosResponse)

let alertSpy: ReturnType<typeof vi.spyOn>

function renderPage({ authenticated = true, accounts = [{ accountId: 3, accountName: '연습' }] } = {}) {
  vi.mocked(useAuth).mockReturnValue({ isAuthenticated: authenticated, login: vi.fn() } as never)
  vi.mocked(useAccounts).mockReturnValue({
    accounts, selectedAccountId: accounts[0]?.accountId ?? null, setSelectedAccountId: vi.fn(),
  } as never)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const invalidate = vi.spyOn(client, 'invalidateQueries')
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Trading />
      </MemoryRouter>
    </QueryClientProvider>
  )
  return { invalidate }
}

// 차트 응답을 받아 거래일이 마지막 거래일로 잡힐 때까지
const waitForChart = () => waitFor(() => expect(screen.getAllByText(/2026-10-09/).length).toBeGreaterThan(0))
const submitButton = () => screen.getAllByRole('button', { name: /^(매수|매도)$/ }).at(-1)!
// 주문 유형(시가 · 종가 · 지정가) 드롭다운을 지정가로 바꾸고 가격을 넣는다
const setLimitPrice = async (price: string) => {
  const priceType = screen.getAllByRole('combobox').find((el) => (el as HTMLSelectElement).value === 'close')!
  fireEvent.change(priceType, { target: { value: 'limit' } })
  fireEvent.change(await screen.findByPlaceholderText('0.00'), { target: { value: price } })
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(stockApi.getAllSymbols).mockResolvedValue({ data: [{ symbol: 'aapl' }, { symbol: 'msft' }] } as never)
  vi.mocked(stockApi.getOHLCData).mockResolvedValue({ data: OHLC } as never)
  alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

describe('Trading 로그인 전', () => {
  it('안내와 로그인 버튼을 보여주고, 누르면 로그인 창을 연다', async () => {
    renderPage({ authenticated: false })

    expect(screen.getByText('로그인이 필요한 서비스입니다')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /로그인하기/ }))

    expect(screen.getByRole('button', { name: /구글로 로그인/ })).toBeInTheDocument()
  })
})

describe('Trading 주문', () => {
  it('종가 매수: 마지막 거래일 · 종가로 주문하고 체결 문구를 띄운 뒤 수량을 1 로 되돌린다', async () => {
    vi.mocked(tradeApi.buy).mockResolvedValue({ data: TRADE } as never)
    const { invalidate } = renderPage()
    await waitForChart()

    await userEvent.click(submitButton())

    await waitFor(() => expect(tradeApi.buy).toHaveBeenCalledWith({
      accountId: 3, symbol: 'AAPL', quantity: 10, tradeDate: '2026-10-09', priceType: 'CLOSE', manualPrice: undefined,
    }))
    expect(alertSpy).toHaveBeenCalledWith(expect.stringContaining('매수 주문이 체결되었습니다.'))
    // 잔고 · 보유가 바뀌어 거래 쪽 조회를 다시 받는다
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['trade'] })
    expect(screen.getByRole('spinbutton')).toHaveValue(1)
  })

  it('매도로 바꾸면 매도 API 로 보낸다', async () => {
    vi.mocked(tradeApi.sell).mockResolvedValue({ data: { ...TRADE, tradeType: 'SELL' } } as never)
    renderPage()
    await waitForChart()

    await userEvent.click(screen.getAllByRole('button', { name: '매도' })[0])
    await userEvent.click(submitButton())

    await waitFor(() => expect(tradeApi.sell).toHaveBeenCalledWith(expect.objectContaining({ symbol: 'AAPL', priceType: 'CLOSE' })))
    expect(tradeApi.buy).not.toHaveBeenCalled()
  })

  it('지정가가 그날 고가 · 저가 밖이면 막고 범위를 알려준다', async () => {
    renderPage()
    await waitForChart()

    await setLimitPrice('200')
    await userEvent.click(submitButton())

    expect(tradeApi.buy).not.toHaveBeenCalled()
    expect(alertSpy).toHaveBeenCalledTimes(1)
    expect(alertSpy.mock.calls[0][0]).toMatch(/101/)
  })

  it('지정가가 범위 안이면 MANUAL 과 그 가격으로 보낸다', async () => {
    vi.mocked(tradeApi.buy).mockResolvedValue({ data: TRADE } as never)
    renderPage()
    await waitForChart()

    await setLimitPrice('104.5')
    await userEvent.click(submitButton())

    await waitFor(() => expect(tradeApi.buy).toHaveBeenCalledWith(expect.objectContaining({ priceType: 'MANUAL', manualPrice: 104.5 })))
  })

  it('서버가 거절하면 서버 문구로 실패를 알린다', async () => {
    vi.mocked(tradeApi.buy).mockRejectedValue(httpError('잔액이 부족합니다'))
    renderPage()
    await waitForChart()

    await userEvent.click(submitButton())

    await waitFor(() => expect(alertSpy).toHaveBeenCalledWith('주문 실패: 잔액이 부족합니다'))
  })

  it('계좌가 없으면 주문 버튼을 막는다', async () => {
    renderPage({ accounts: [] })
    await waitForChart()

    expect(submitButton()).toBeDisabled()
  })

  it('거래내역 탭으로 바꿀 수 있다', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: '거래내역' }))

    expect(screen.getByText('거래내역 탭')).toBeInTheDocument()
  })
})
