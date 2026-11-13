import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AxiosError, type AxiosResponse } from 'axios'
import { stockApi, tradeApi } from '../services/api'
import { useAuth } from '../hooks/useAuth'
import { useAccounts } from '@/hooks/useAccounts'
import Trading from './Trading'
import { notify } from '@/components/common/notify'

vi.mock('../services/api', () => ({
  stockApi: { getAllSymbols: vi.fn(), getOHLCData: vi.fn() },
  tradeApi: { buy: vi.fn(), sell: vi.fn() },
}))
vi.mock('../hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: vi.fn() }))
// 주문 흐름만(캔버스 차트 · 거래내역 탭 · 매수 가능 패널은 따로 테스트)
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
let errorSpy: ReturnType<typeof vi.spyOn>

function renderPage({ accounts = [{ accountId: 3, accountName: '연습' }] } = {}) {
  vi.mocked(useAuth).mockReturnValue({ isAuthenticated: true } as never)
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
// 주문 유형(시가 · 종가 · 지정가) 드롭다운을 지정가로 바꾸고 가격 입력
const setLimitPrice = async (price: string) => {
  const priceType = screen.getAllByRole('combobox').find((el) => (el as HTMLSelectElement).value === 'close')!
  fireEvent.change(priceType, { target: { value: 'limit' } })
  fireEvent.change(await screen.findByPlaceholderText('0.00'), { target: { value: price } })
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(stockApi.getAllSymbols).mockResolvedValue({ data: [{ symbol: 'aapl' }, { symbol: 'msft' }] } as never)
  vi.mocked(stockApi.getOHLCData).mockResolvedValue({ data: OHLC } as never)
  alertSpy = vi.spyOn(notify, 'success').mockImplementation(() => {})
  errorSpy = vi.spyOn(notify, 'error').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

describe('Trading 주문', () => {
  it('종가 매수: 마지막 거래일 · 종가로 주문하고 체결 문구를 띄운 뒤 수량을 1 로 되돌린다', async () => {
    vi.mocked(tradeApi.buy).mockResolvedValue({ data: TRADE } as never)
    const { invalidate } = renderPage()
    await waitForChart()

    await userEvent.click(submitButton())

    await waitFor(() => expect(tradeApi.buy).toHaveBeenCalledWith({
      accountId: 3, symbol: 'AAPL', quantity: 10, tradeDate: '2026-10-09', priceType: 'CLOSE', manualPrice: undefined,
    }, expect.any(String)))
    expect(alertSpy).toHaveBeenCalledWith(expect.stringContaining('매수 주문이 체결되었습니다.'))
    // 잔고 · 보유가 바뀌어 거래 · 계좌 쪽 조회를 다시 받음
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['trade'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['account'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['accounts'] }, { cancelRefetch: false })
    expect(screen.getByRole('spinbutton')).toHaveValue(1)
  })

  it('주문 요청이 끝나기 전에는 매수 버튼을 다시 눌러도 한 번만 보내고 버튼을 막는다', async () => {
    let finish!: (value: unknown) => void
    vi.mocked(tradeApi.buy).mockImplementation(() => new Promise((resolve) => { finish = resolve }) as never)
    renderPage()
    await waitForChart()

    await userEvent.dblClick(submitButton())

    expect(tradeApi.buy).toHaveBeenCalledTimes(1)
    expect(submitButton()).toBeDisabled()
    await act(async () => finish({ data: TRADE }))
    await waitFor(() => expect(submitButton()).toBeEnabled())
  })

  it('시세가 없는 종목으로 바꾸면 이전 종목 가격으로 주문 금액을 셈하지 않는다', async () => {
    const user = userEvent.setup()
    vi.mocked(stockApi.getOHLCData).mockImplementation(async (symbol: string) => ({ data: symbol === 'AAPL' ? OHLC : [] }) as never)
    renderPage()
    await waitForChart()
    // 종가 108 × 10주
    expect(screen.getByText('$1080.00')).toBeInTheDocument()

    const search = screen.getByPlaceholderText('종목명 또는 티커 입력')
    await user.clear(search)
    await user.type(search, 'MSFT')
    await user.click(screen.getAllByTestId('symbol-option').find((option) => option.textContent?.startsWith('MSFT'))!)

    await waitFor(() => expect(stockApi.getOHLCData).toHaveBeenCalledWith('MSFT', expect.any(String), expect.any(String)))
    await waitFor(() => expect(screen.queryByText('$1080.00')).toBeNull())
  })

  it('정산 전 매수면 접수 문구를 띄우고 2초 뒤 보유 · 거래 · 계좌 조회를 한 번 더 받는다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      vi.mocked(tradeApi.buy).mockResolvedValue({ data: { ...TRADE, status: 'PENDING' } } as never)
      const { invalidate } = renderPage()
      await waitForChart()

      await userEvent.click(submitButton())
      await waitFor(() => expect(alertSpy).toHaveBeenCalledWith(expect.stringContaining('매수 주문을 받았습니다.')))
      expect(invalidate).not.toHaveBeenCalledWith({ queryKey: ['portfolio'] })

      const accountRefreshes = () => invalidate.mock.calls.filter(([filters]) => filters?.queryKey?.[0] === 'account').length
      expect(accountRefreshes()).toBe(1)

      await vi.advanceTimersByTimeAsync(2000)

      expect(invalidate).toHaveBeenCalledWith({ queryKey: ['portfolio'] })
      expect(accountRefreshes()).toBe(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('완료된 체결이면 보유 조회를 다시 받지 않는다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      vi.mocked(tradeApi.buy).mockResolvedValue({ data: { ...TRADE, status: 'COMPLETED' } } as never)
      const { invalidate } = renderPage()
      await waitForChart()

      await userEvent.click(submitButton())
      await waitFor(() => expect(alertSpy).toHaveBeenCalled())
      await vi.advanceTimersByTimeAsync(2000)

      expect(invalidate).not.toHaveBeenCalledWith({ queryKey: ['portfolio'] })
    } finally {
      vi.useRealTimers()
    }
  })

  it('매도로 바꾸면 매도 API 로 보낸다', async () => {
    vi.mocked(tradeApi.sell).mockResolvedValue({ data: { ...TRADE, tradeType: 'SELL' } } as never)
    renderPage()
    await waitForChart()

    await userEvent.click(screen.getAllByRole('button', { name: '매도' })[0])
    await userEvent.click(submitButton())

    await waitFor(() => expect(tradeApi.sell).toHaveBeenCalledWith(expect.objectContaining({ symbol: 'AAPL', priceType: 'CLOSE' }), expect.any(String)))
    expect(tradeApi.buy).not.toHaveBeenCalled()
  })

  it('지정가가 거래일 고가 · 저가 밖이면 막고 범위를 안내한다', async () => {
    renderPage()
    await waitForChart()

    await setLimitPrice('200')
    await userEvent.click(submitButton())

    expect(tradeApi.buy).not.toHaveBeenCalled()
    expect(errorSpy).toHaveBeenCalledTimes(1)
    expect(errorSpy.mock.calls[0][0]).toMatch(/101/)
  })

  it('지정가가 범위 안이면 MANUAL 과 그 가격으로 보낸다', async () => {
    vi.mocked(tradeApi.buy).mockResolvedValue({ data: TRADE } as never)
    renderPage()
    await waitForChart()

    await setLimitPrice('104.5')
    await userEvent.click(submitButton())

    await waitFor(() => expect(tradeApi.buy).toHaveBeenCalledWith(expect.objectContaining({ priceType: 'MANUAL', manualPrice: 104.5 }), expect.any(String)))
  })

  it('서버가 거절하면 서버 문구로 실패를 알린다', async () => {
    vi.mocked(tradeApi.buy).mockRejectedValue(httpError('잔액이 부족합니다'))
    renderPage()
    await waitForChart()

    await userEvent.click(submitButton())

    await waitFor(() => expect(errorSpy).toHaveBeenCalledWith('주문 실패: 잔액이 부족합니다'))
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
