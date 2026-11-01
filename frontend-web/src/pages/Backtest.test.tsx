import { describe, it, expect, vi, beforeEach, afterEach, type MockInstance } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import Backtest from './Backtest'
import { backtestApi, stockApi } from '../services/api'
import { saveLocalBacktestHistory } from '../utils/localBacktestHistory'
import { notify } from '@/components/common/notify'

vi.mock('../services/api', () => ({
  backtestApi: {
    runSimulation: vi.fn(),
    runDcaStrategy: vi.fn(),
    runConditionalStrategy: vi.fn(),
    compareSymbols: vi.fn(),
    compareStrategies: vi.fn(),
    getHistory: vi.fn(),
  },
  stockApi: { getAllSymbols: vi.fn() },
}))

const auth = vi.hoisted(() => ({
  isAuthenticated: true,
  user: { email: 'tester@example.com' } as { email: string } | null,
}))
vi.mock('../hooks/useAuth', () => ({ useAuth: () => auth }))

vi.mock('../utils/localBacktestHistory', () => ({
  saveLocalBacktestHistory: vi.fn(),
  getLocalBacktestHistory: vi.fn(() => []),
}))

// 결과 화면 대역(차트 대신 어느 결과가 떴는지만)
vi.mock('@components/backtest/results/SimpleResult', () => ({
  SimpleResult: () => <div>결과: simple</div>,
}))
vi.mock('@components/backtest/results/DcaConditionalResult', () => ({
  DcaConditionalResult: ({ result }: { result: { mode: string } }) => <div>결과: {result.mode}</div>,
}))
vi.mock('@components/backtest/results/SymbolComparisonResult', () => ({
  SymbolComparisonResult: () => <div>결과: compare-symbols</div>,
}))
vi.mock('@components/backtest/results/StrategyComparisonResult', () => ({
  StrategyComparisonResult: () => <div>결과: compare-strategies</div>,
}))

const USER = 'tester@example.com'
const TODAY = '2026-10-08'

let alertSpy: MockInstance<typeof notify.error>

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Backtest />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

// 모드 탭(전략 비교 폼에도 "적립식" 버튼이 있어 탭 영역 안에서 찾음)
async function selectTab(name: string) {
  const tabs = screen.getByRole('heading', { name: '백테스트 모드' }).parentElement!
  await userEvent.click(within(tabs).getByRole('button', { name }))
}

// 전략 비교 폼의 전략 버튼
async function toggleStrategy(name: string) {
  const area = screen.getByText('비교할 전략 선택 (최소 2개)').parentElement!
  await userEvent.click(within(area).getByRole('button', { name }))
}

const runBacktest = () => userEvent.click(screen.getByTestId('backtest-run'))

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-08T12:00:00+09:00'))
  vi.clearAllMocks()
  auth.isAuthenticated = true
  auth.user = { email: USER }
  alertSpy = vi.spyOn(notify, 'error').mockImplementation(() => {})
  vi.mocked(stockApi.getAllSymbols).mockResolvedValue({ data: [] } as never)
  vi.mocked(backtestApi.runSimulation).mockResolvedValue({ data: {} } as never)
  vi.mocked(backtestApi.runDcaStrategy).mockResolvedValue({ data: {} } as never)
  vi.mocked(backtestApi.runConditionalStrategy).mockResolvedValue({ data: {} } as never)
  vi.mocked(backtestApi.compareSymbols).mockResolvedValue({ data: {} } as never)
  vi.mocked(backtestApi.compareStrategies).mockResolvedValue({ data: {} } as never)
})

afterEach(() => {
  alertSpy.mockRestore()
  vi.useRealTimers()
})

const common = { reinvestDividends: false, tradingFeeRate: 0, dividendTaxRate: 0 }

describe('Backtest 페이지', () => {
  it('단순: 기본값으로 실행하면 매도일을 오늘로 보낸다', async () => {
    renderPage()
    await runBacktest()

    expect(backtestApi.runSimulation).toHaveBeenCalledWith({
      symbol: 'AAPL',
      purchaseDate: '2023-01-01',
      saleDate: TODAY,
      investmentAmount: 300000,
      ...common,
    })
    expect(await screen.findByText('결과: simple')).toBeInTheDocument()
    expect(saveLocalBacktestHistory).not.toHaveBeenCalled()
  })

  it('적립식: 기본값 요청', async () => {
    renderPage()
    await selectTab('적립식')
    await runBacktest()

    expect(backtestApi.runDcaStrategy).toHaveBeenCalledWith({
      symbol: 'AAPL',
      startDate: '2023-01-01',
      endDate: TODAY,
      monthlyAmount: 100000,
      purchaseDay: 15,
      investmentInterval: 1,
      ...common,
    })
    expect(await screen.findByText('결과: dca')).toBeInTheDocument()
  })

  it('조건부: 총 예산 모드 기본값 요청', async () => {
    renderPage()
    await selectTab('조건부')
    await runBacktest()

    expect(backtestApi.runConditionalStrategy).toHaveBeenCalledWith({
      symbol: 'AAPL',
      startDate: '2023-01-01',
      endDate: TODAY,
      investmentMode: 'TOTAL_BUDGET',
      dropPercentage: 0.05,
      totalInvestment: 1000000,
      amountPerPurchase: 100000,
      ...common,
    })
    expect(await screen.findByText('결과: conditional')).toBeInTheDocument()
  })

  it('종목 비교: 기본 두 종목 요청', async () => {
    renderPage()
    await selectTab('종목 비교')
    await runBacktest()

    expect(backtestApi.compareSymbols).toHaveBeenCalledWith({
      symbols: ['AAPL', 'MSFT'],
      startDate: '2023-01-01',
      endDate: TODAY,
      investmentAmount: 1000000,
      ...common,
    })
    expect(await screen.findByText('결과: compare-symbols')).toBeInTheDocument()
  })

  it('전략 비교: 단순 · 적립식 기본값 요청', async () => {
    renderPage()
    await selectTab('전략 비교')
    await runBacktest()

    expect(backtestApi.compareStrategies).toHaveBeenCalledWith({
      symbol: 'AAPL',
      startDate: '2023-01-01',
      endDate: TODAY,
      investmentAmount: 1000000,
      strategies: [
        { strategyType: 'SIMPLE', name: 'SIMPLE', purchaseDate: '2023-01-01' },
        {
          strategyType: 'DCA',
          name: 'DCA',
          monthlyAmount: 100000,
          purchaseDay: 15,
          investmentInterval: 1,
          totalInvestmentLimit: 1000000,
        },
      ],
      ...common,
    })
    expect(await screen.findByText('결과: compare-strategies')).toBeInTheDocument()
  })

  it('단순: 10만원 아래면 알림으로 막는다', async () => {
    renderPage()
    const input = screen.getByPlaceholderText('300,000')
    await userEvent.clear(input)
    await userEvent.type(input, '50000')
    await runBacktest()

    expect(alertSpy).toHaveBeenCalledWith(
      '최소 10만원 이상 투자해주세요. (미국 주식 1주 구매를 위해 약 30만원 권장)'
    )
    expect(backtestApi.runSimulation).not.toHaveBeenCalled()
  })

  it('전략 비교: 전략이 하나면 알림으로 막는다', async () => {
    renderPage()
    await selectTab('전략 비교')
    await toggleStrategy('적립식')
    await runBacktest()

    expect(alertSpy).toHaveBeenCalledWith('최소 2개 이상의 전략을 선택해주세요.')
    expect(backtestApi.compareStrategies).not.toHaveBeenCalled()
  })

  it('전략 비교: 모달에서 조건부 매수를 더하면 세 전략을 보낸다', async () => {
    renderPage()
    await selectTab('전략 비교')
    await toggleStrategy('조건부 매수')
    expect(screen.getByRole('heading', { name: '전략 설정: 조건부 매수' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    await runBacktest()

    const request = vi.mocked(backtestApi.compareStrategies).mock.calls[0][0]
    expect(request.strategies).toHaveLength(3)
    expect(request.strategies[2]).toEqual({
      strategyType: 'CONDITIONAL_PURCHASE',
      name: 'CONDITIONAL_PURCHASE',
      totalInvestment: 1000000,
      dropPercentage: 0.05,
    })
  })

  it('비로그인: 사용자 없이 요청하고 로컬 히스토리에 저장한다', async () => {
    auth.isAuthenticated = false
    auth.user = null
    renderPage()
    await runBacktest()

    expect(vi.mocked(backtestApi.runSimulation).mock.calls[0][0]).not.toHaveProperty('userId')
    expect(saveLocalBacktestHistory).toHaveBeenCalledWith(
      'STRATEGY_SIMULATION',
      expect.objectContaining({ symbol: 'AAPL' }),
      'auto'
    )
  })

  it('히스토리 탭은 페이지 번호와 크기만 보내 이력을 조회한다', async () => {
    vi.mocked(backtestApi.getHistory).mockResolvedValue({ data: { content: [], totalPages: 0 } } as never)
    renderPage()
    await selectTab('히스토리')

    await vi.waitFor(() => expect(backtestApi.getHistory).toHaveBeenCalledWith(0, 20))
  })

  it('API 가 실패하면 모드별 실패 문구를 보인다', async () => {
    vi.mocked(backtestApi.runSimulation).mockRejectedValueOnce('network')
    renderPage()
    await runBacktest()

    expect(await screen.findByText('백테스트 실행에 실패했습니다.')).toBeInTheDocument()
  })

  it('탭을 바꿨다 돌아와도 입력값이 남는다', async () => {
    renderPage()
    const input = screen.getByPlaceholderText('300,000')
    await userEvent.clear(input)
    await userEvent.type(input, '500000')
    await selectTab('적립식')
    await selectTab('단순')
    await runBacktest()

    expect(backtestApi.runSimulation).toHaveBeenCalledWith(
      expect.objectContaining({ investmentAmount: 500000 })
    )
  })
})
