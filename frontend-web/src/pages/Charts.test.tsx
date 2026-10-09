import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import Charts from './Charts'
import { stockApi } from '../services/api'

vi.mock('../services/api', () => ({
  stockApi: { getAllSymbols: vi.fn(), getOHLCData: vi.fn(), getCurrentPrice: vi.fn() },
}))
// jsdom 에는 캔버스가 없다
vi.mock('../components/charts/KLineChartComponent', () => ({
  default: () => <div data-testid="kline" />,
}))
vi.mock('../hooks/useAiChat', () => ({ useAiChat: () => ({ openChat: vi.fn() }) }))
vi.mock('../components/common/SearchModal', () => ({
  default: ({ isOpen, onSelectStock }: { isOpen: boolean; onSelectStock?: (s: string) => void }) =>
    isOpen ? <button onClick={() => onSelectStock?.('MSFT')}>MSFT 고르기</button> : null,
}))

const getOHLCData = vi.mocked(stockApi.getOHLCData)

// 마지막 가격 요청의 [시작일, 종료일]
const lastRange = () => getOHLCData.mock.calls.at(-1)!.slice(1, 3)

const ohlc = (date: string, open: number, close: number) => ({
  symbol: 'AAPL',
  date,
  openPrice: open,
  highPrice: Math.max(open, close),
  lowPrice: Math.min(open, close),
  closePrice: close,
  adjustedClose: close,
  volume: 1000,
  currency: 'USD',
  available: true,
})

const pickDay = async (testId: string, day: string) => {
  await userEvent.click(screen.getByTestId(testId))
  const button = within(screen.getByRole('dialog'))
    .getAllByRole('button')
    .find((b) => b.textContent === day)!
  await userEvent.click(button)
}

const LocationProbe = () => {
  const { pathname, search } = useLocation()
  return <p data-testid="location">{pathname + search}</p>
}

function renderCharts(entry = '/charts/AAPL') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/charts/:symbol" element={<Charts />} />
        </Routes>
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

// 기대값은 TZ=Asia/Seoul 전제 (vitest.config.ts 에서 고정)
describe('Charts', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    // KST 오전 5시. UTC 로는 아직 전날이다
    vi.setSystemTime(new Date('2026-03-31T05:00:00+09:00'))
    vi.mocked(stockApi.getAllSymbols).mockResolvedValue({ data: [] } as never)
    vi.mocked(stockApi.getCurrentPrice).mockResolvedValue({ data: { name: 'Apple' } } as never)
    getOHLCData.mockReset()
    getOHLCData.mockResolvedValue({ data: [ohlc('2026-03-30', 98, 100), ohlc('2026-03-31', 105, 102)] } as never)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('기간', () => {
    it('주소에 기간이 없으면 1년 전부터 오늘까지 받는다. KST 새벽에도 오늘 날짜다', async () => {
      renderCharts()

      await waitFor(() => expect(getOHLCData).toHaveBeenCalled())
      expect(lastRange()).toEqual(['2025-03-31', '2026-03-31'])
    })

    it('1개월을 누르면 주소에 남기고 한 달 전 같은 날부터 받는다. 그날이 없으면 그 달 말일부터다', async () => {
      renderCharts()
      await waitFor(() => expect(getOHLCData).toHaveBeenCalled())

      await userEvent.click(screen.getByRole('button', { name: '1개월' }))

      await waitFor(() => expect(lastRange()).toEqual(['2026-02-28', '2026-03-31']))
      expect(screen.getByTestId('location')).toHaveTextContent('/charts/AAPL?period=1m')
    })

    it('주소의 기간으로 들어오면 그 기간을 받는다', async () => {
      renderCharts('/charts/AAPL?period=3y')

      await waitFor(() => expect(getOHLCData).toHaveBeenCalled())
      expect(lastRange()).toEqual(['2023-03-31', '2026-03-31'])
    })

    it('주소의 직접설정 날짜로 들어오면 그 날짜를 받는다', async () => {
      renderCharts('/charts/AAPL?period=custom&start=2024-01-02&end=2024-06-28')

      await waitFor(() => expect(getOHLCData).toHaveBeenCalled())
      expect(lastRange()).toEqual(['2024-01-02', '2024-06-28'])
      expect(screen.getByTestId('chart-custom-start')).toHaveTextContent('2024. 01. 02.')
    })

    it('직접설정은 두 날짜를 다 고를 때까지 안내만 보이고, 고르면 그 날짜를 받는다', async () => {
      renderCharts()
      await waitFor(() => expect(getOHLCData).toHaveBeenCalledTimes(1))

      await userEvent.click(screen.getByRole('button', { name: '직접설정' }))
      expect(screen.getByText('시작일과 종료일을 고르세요')).toBeInTheDocument()

      await pickDay('chart-custom-start', '10')
      expect(getOHLCData).toHaveBeenCalledTimes(1)
      await pickDay('chart-custom-end', '20')

      await waitFor(() => expect(lastRange()).toEqual(['2026-03-10', '2026-03-20']))
      expect(screen.getByTestId('location')).toHaveTextContent(
        '/charts/AAPL?period=custom&start=2026-03-10&end=2026-03-20'
      )
    })

    it('종목을 바꿔도 기간을 그대로 둔다', async () => {
      renderCharts('/charts/AAPL?period=6m')
      await waitFor(() => expect(getOHLCData).toHaveBeenCalled())

      await userEvent.click(screen.getByRole('button', { name: '종목 변경' }))
      await userEvent.click(screen.getByRole('button', { name: 'MSFT 고르기' }))

      expect(screen.getByTestId('location')).toHaveTextContent('/charts/MSFT?period=6m')
    })
  })

  describe('가격과 차트 칸', () => {
    it('등락을 전일 종가 대비로 보이고 기준일을 붙인다', async () => {
      renderCharts()

      expect(await screen.findByText('+2.00 (+2.00%)')).toBeInTheDocument()
      expect(screen.getByText('전일 종가 대비 · 2026. 03. 31. 종가 기준')).toBeInTheDocument()
    })

    it('받은 데이터가 없으면 차트 대신 안내를 보인다', async () => {
      getOHLCData.mockResolvedValue({ data: [] } as never)
      renderCharts()

      expect(await screen.findByText('이 기간에 데이터가 없습니다')).toBeInTheDocument()
      expect(screen.queryByTestId('kline')).toBeNull()
    })

    it('데이터가 있으면 차트를 그린다', async () => {
      renderCharts()

      expect(await screen.findByTestId('kline')).toBeInTheDocument()
    })
  })
})
