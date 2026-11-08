import { describe, it, expect, vi, beforeEach, afterEach, type MockInstance } from 'vitest'
import { fireEvent, render, screen, within } from '@testing-library/react'
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
  stockApi: { getAllSymbols: vi.fn(), getOHLCData: vi.fn(), getExchangeRatesBulk: vi.fn() },
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
// 오늘부터 1년 전(수요일)
const START = '2025-10-08'

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

const modes = () => within(screen.getByRole('radiogroup', { name: '백테스트 모드' }))
const selectMode = (name: string) => userEvent.click(modes().getByRole('radio', { name }))
const runBacktest = () => userEvent.click(screen.getByTestId('backtest-run'))
const summary = () => screen.getByTestId('backtest-summary')
const strategyChips = () => within(screen.getByRole('group', { name: '견줄 전략' }))

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-08T12:00:00+09:00'))
  vi.clearAllMocks()
  auth.isAuthenticated = true
  auth.user = { email: USER }
  alertSpy = vi.spyOn(notify, 'error').mockImplementation(() => {})
  vi.mocked(stockApi.getAllSymbols).mockResolvedValue({ data: [{ symbol: 'AAPL' }, { symbol: 'MSFT' }, { symbol: 'NVDA' }] } as never)
  vi.mocked(stockApi.getOHLCData).mockResolvedValue({ data: [] } as never)
  vi.mocked(stockApi.getExchangeRatesBulk).mockResolvedValue({ data: {} } as never)
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

describe('Backtest 페이지 모드 · 기록', () => {
  it('모드는 하나만 고르는 묶음이고 지난 기록은 모드 대신 제목 옆 링크로 따로 본다', () => {
    renderPage()

    expect(modes().getAllByRole('radio').map((radio) => radio.textContent)).toEqual(['단순', '적립식', '조건부', '종목 비교', '전략 비교'])
    expect(modes().getByRole('radio', { name: '단순' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.queryByText('히스토리')).toBeNull()
    expect(screen.getByRole('link', { name: '지난 백테스트 기록 →' })).toHaveAttribute('href', '/backtest/history')
  })

  it('화살표로 모드를 옮기면 그 모드의 칸으로 바뀐다', async () => {
    renderPage()

    modes().getByRole('radio', { name: '단순' }).focus()
    await userEvent.keyboard('{ArrowRight}')

    expect(modes().getByRole('radio', { name: '적립식' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('radiogroup', { name: '언제' })).toBeInTheDocument()
  })
})

describe('Backtest 페이지 실행', () => {
  it('단순: 기본값은 오늘부터 1년 전 · 30만원이고 매도일을 오늘로 보낸다', async () => {
    renderPage()
    await runBacktest()

    expect(backtestApi.runSimulation).toHaveBeenCalledWith({
      symbol: 'AAPL',
      purchaseDate: START,
      saleDate: TODAY,
      investmentAmount: 300000,
      ...common,
    })
    expect(await screen.findByText('결과: simple')).toBeInTheDocument()
    expect(saveLocalBacktestHistory).not.toHaveBeenCalled()
  })

  it('적립식: 기본값은 회당 10만원 · 매달 시작일의 날짜로 요청', async () => {
    renderPage()
    await selectMode('적립식')
    await runBacktest()

    expect(backtestApi.runDcaStrategy).toHaveBeenCalledWith({
      symbol: 'AAPL',
      startDate: START,
      endDate: TODAY,
      amountPerPurchase: 100000,
      frequency: 'MONTHLY',
      purchaseDay: 8,
      investmentInterval: 1,
      ...common,
    })
    expect(await screen.findByText('결과: dca')).toBeInTheDocument()
  })

  it('적립식: 매주를 고르면 투자일 · 개월 수 없이 첫 매수일과 주기로 요청', async () => {
    renderPage()
    await selectMode('적립식')
    await userEvent.click(screen.getByRole('radio', { name: '매주' }))
    await runBacktest()

    expect(backtestApi.runDcaStrategy).toHaveBeenCalledWith({
      symbol: 'AAPL',
      startDate: START,
      endDate: TODAY,
      amountPerPurchase: 100000,
      frequency: 'WEEKLY',
      firstPurchaseDate: START,
      ...common,
    })
  })

  it('조건부: 기본값은 회당 10만원 · 예산 100만원 · 최대 횟수 비움으로 요청', async () => {
    renderPage()
    await selectMode('조건부')
    await runBacktest()

    expect(backtestApi.runConditionalStrategy).toHaveBeenCalledWith({
      symbol: 'AAPL',
      startDate: START,
      endDate: TODAY,
      direction: 'DROP',
      dropPercentage: 0.05,
      amountPerPurchase: 100000,
      totalInvestment: 1000000,
      ...common,
    })
    expect(await screen.findByText('결과: conditional')).toBeInTheDocument()
  })

  it('조건부: 상승을 고르면 상승 방향으로 요청한다', async () => {
    renderPage()
    await selectMode('조건부')
    await userEvent.click(screen.getByRole('radio', { name: '상승' }))
    await runBacktest()

    expect(vi.mocked(backtestApi.runConditionalStrategy).mock.calls[0][0]).toMatchObject({ direction: 'RISE', dropPercentage: 0.05 })
  })

  it('조건부: 익절을 적으면 배당 재투자 체크를 잠그고 비율로 요청한다', async () => {
    renderPage()
    await selectMode('조건부')
    const reinvest = screen.getByRole('checkbox', { name: /배당금 재투자/ })
    await userEvent.click(reinvest)
    expect(reinvest).toBeChecked()

    await userEvent.type(screen.getByRole('textbox', { name: '익절(평균 단가 대비 퍼센트)' }), '20')

    expect(reinvest).toBeDisabled()
    expect(reinvest).not.toBeChecked()
    await runBacktest()
    expect(vi.mocked(backtestApi.runConditionalStrategy).mock.calls[0][0]).toMatchObject({
      takeProfitRate: 0.2,
      reinvestDividends: false,
    })
  })

  it('단순: 주식 수를 고르면 투자금 대신 주식 수로 요청한다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('radio', { name: '주식 수' }))
    const shares = screen.getByRole('textbox', { name: '주식 수(주)' })
    await userEvent.clear(shares)
    await userEvent.type(shares, '3')
    await runBacktest()

    expect(backtestApi.runSimulation).toHaveBeenCalledWith({
      symbol: 'AAPL',
      purchaseDate: START,
      saleDate: TODAY,
      sharesPerPurchase: 3,
      ...common,
    })
    expect(vi.mocked(backtestApi.runSimulation).mock.calls[0][0]).not.toHaveProperty('investmentAmount')
  })

  it('적립식 · 조건부: 주식 수를 고르면 회당 금액 대신 회당 주식 수로 요청한다', async () => {
    renderPage()
    await selectMode('적립식')
    await userEvent.click(screen.getByRole('radio', { name: '주식 수' }))
    await runBacktest()

    expect(backtestApi.runDcaStrategy).toHaveBeenCalledWith({
      symbol: 'AAPL',
      startDate: START,
      endDate: TODAY,
      sharesPerPurchase: 1,
      frequency: 'MONTHLY',
      purchaseDay: 8,
      investmentInterval: 1,
      ...common,
    })
    expect(vi.mocked(backtestApi.runDcaStrategy).mock.calls[0][0]).not.toHaveProperty('amountPerPurchase')

    await selectMode('조건부')
    await userEvent.click(screen.getByRole('radio', { name: '주식 수' }))
    await runBacktest()

    expect(backtestApi.runConditionalStrategy).toHaveBeenCalledWith({
      symbol: 'AAPL',
      startDate: START,
      endDate: TODAY,
      direction: 'DROP',
      dropPercentage: 0.05,
      sharesPerPurchase: 1,
      totalInvestment: 1000000,
      ...common,
    })
    expect(vi.mocked(backtestApi.runConditionalStrategy).mock.calls[0][0]).not.toHaveProperty('amountPerPurchase')
  })

  it('적립식: 예산 칸은 주식 수 단위에서만 열리고 적은 예산을 한도로 보낸다', async () => {
    renderPage()
    await selectMode('적립식')
    expect(screen.queryByRole('textbox', { name: '예산(원)' })).toBeNull()

    await userEvent.click(screen.getByRole('radio', { name: '주식 수' }))
    fireEvent.change(screen.getByRole('textbox', { name: '예산(원)' }), { target: { value: '5,000,000' } })
    await runBacktest()

    expect(vi.mocked(backtestApi.runDcaStrategy).mock.calls[0][0]).toMatchObject({ sharesPerPurchase: 1, totalInvestmentLimit: 5000000 })
  })

  it('종목 비교: 기본 두 종목 요청', async () => {
    renderPage()
    await selectMode('종목 비교')
    await runBacktest()

    expect(backtestApi.compareSymbols).toHaveBeenCalledWith({
      symbols: ['AAPL', 'MSFT'],
      startDate: START,
      endDate: TODAY,
      investmentAmount: 1000000,
      ...common,
    })
    expect(await screen.findByText('결과: compare-symbols')).toBeInTheDocument()
  })

  it('전략 비교: 단순 · 적립식 기본값 요청', async () => {
    renderPage()
    await selectMode('전략 비교')
    await runBacktest()

    expect(backtestApi.compareStrategies).toHaveBeenCalledWith({
      symbol: 'AAPL',
      startDate: START,
      endDate: TODAY,
      investmentAmount: 1000000,
      strategies: [
        { strategyType: 'SIMPLE', name: 'SIMPLE', purchaseDate: START },
        {
          strategyType: 'DCA',
          name: 'DCA',
          amountPerPurchase: 100000,
          frequency: 'MONTHLY',
          purchaseDay: 8,
          investmentInterval: 1,
        },
      ],
      ...common,
    })
    expect(await screen.findByText('결과: compare-strategies')).toBeInTheDocument()
  })

  it('단순: 10만원 아래면 알림으로 막는다', async () => {
    renderPage()
    const input = screen.getByRole('textbox', { name: '금액(원)' })
    await userEvent.clear(input)
    await userEvent.type(input, '50000')
    await runBacktest()

    expect(alertSpy).toHaveBeenCalledWith(
      '최소 10만원 이상 투자해주세요. (미국 주식 1주 구매를 위해 약 30만원 권장)'
    )
    expect(backtestApi.runSimulation).not.toHaveBeenCalled()
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

  it('API 가 실패하면 모드별 실패 문구를 보인다', async () => {
    vi.mocked(backtestApi.runSimulation).mockRejectedValueOnce('network')
    renderPage()
    await runBacktest()

    expect(await screen.findByText('백테스트 실행에 실패했습니다.')).toBeInTheDocument()
  })

  it('모드를 바꿨다 돌아와도 입력값이 남고 기간은 모드끼리 같이 쓴다', async () => {
    renderPage()
    const input = screen.getByRole('textbox', { name: '금액(원)' })
    await userEvent.clear(input)
    await userEvent.type(input, '500000')
    await userEvent.click(screen.getByRole('radio', { name: '달' }))
    await selectMode('적립식')
    await selectMode('단순')
    await runBacktest()

    expect(backtestApi.runSimulation).toHaveBeenCalledWith(
      expect.objectContaining({ investmentAmount: 500000, purchaseDate: '2026-09-08' })
    )
  })

  it('금액 · 주식 수 단위는 모드마다 따로 남는다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('radio', { name: '주식 수' }))
    await selectMode('적립식')

    expect(screen.getByRole('radio', { name: '금액' })).toHaveAttribute('aria-checked', 'true')
    await selectMode('단순')
    expect(screen.getByRole('radio', { name: '주식 수' })).toHaveAttribute('aria-checked', 'true')
    await runBacktest()

    expect(vi.mocked(backtestApi.runSimulation).mock.calls[0][0]).toMatchObject({ sharesPerPurchase: 1 })
  })
})

describe('Backtest 페이지 칸 오류 · 요약', () => {
  it('요약 두 줄: 종목 · 기간 / 사는 방법 · 바꾼 설정', async () => {
    renderPage()

    expect(summary()).toHaveTextContent('AAPL · 2025.10.08 → 2026.10.08')
    expect(summary()).toHaveTextContent('시작일에 30만원 매수')
    await userEvent.click(screen.getByRole('checkbox', { name: /배당 원천징수/ }))
    expect(summary()).toHaveTextContent('시작일에 30만원 매수 · 배당세 15%')
  })

  it('적립식 · 조건부 요약은 회차 · 한도를 보인다', async () => {
    renderPage()
    await selectMode('적립식')
    expect(summary()).toHaveTextContent('매달 8일 10만원 · 13회 ≈ 130만원')

    await selectMode('조건부')
    expect(summary()).toHaveTextContent('전날보다 5% 내릴 때마다 10만원 · 예산으로 최대 10회 ≈ 100만원')
    expect(screen.getByRole('textbox', { name: '최대 횟수(회)' })).toHaveAttribute('placeholder', '예산 한도까지')
    await userEvent.click(screen.getByRole('button', { name: '예산 비우기' }))
    expect(screen.getByRole('textbox', { name: '최대 횟수(회)' })).toHaveAttribute('placeholder', '제한 없음')
    expect(summary()).toHaveTextContent('횟수 제한 없음 · 많아야 약 262회')
  })

  it('고칠 칸이 있으면 실행 단추를 aria-disabled 로 두고 누르면 그 칸으로 가며 요청하지 않는다', async () => {
    renderPage()
    await userEvent.clear(screen.getByRole('textbox', { name: '금액(원)' }))

    expect(screen.getByText('금액 비어 있음')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('고칠 칸 1개')
    expect(screen.getByTestId('backtest-run')).toHaveAttribute('aria-disabled', 'true')
    await runBacktest()

    expect(screen.getByRole('textbox', { name: '금액(원)' })).toHaveFocus()
    expect(backtestApi.runSimulation).not.toHaveBeenCalled()
    expect(alertSpy).not.toHaveBeenCalled()
  })

  it('종목 비교로 오면 보던 종목을 맨 앞에 담는다', async () => {
    renderPage()
    const symbol = screen.getByRole('combobox', { name: '종목' })
    await userEvent.clear(symbol)
    await userEvent.type(symbol, 'nv')
    await userEvent.click(await screen.findByRole('option', { name: /NVDA/ }))
    await selectMode('종목 비교')

    expect(screen.getByText('3 / 10')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('NVDA, AAPL, MSFT · 2025.10.08')
  })
})

describe('Backtest 페이지 전략 비교 칩', () => {
  it('＋ 조건부 매수로 칩을 더하면 설정이 아래에 열리고 세 전략을 보낸다', async () => {
    renderPage()
    await selectMode('전략 비교')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 조건부 매수' }))

    expect(strategyChips().getByRole('button', { name: '조건부 매수' })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('조건부 매수 조건')).toBeInTheDocument()
    await runBacktest()

    const request = vi.mocked(backtestApi.compareStrategies).mock.calls[0][0]
    expect(request.strategies).toHaveLength(3)
    expect(request.strategies[2]).toEqual({
      strategyType: 'CONDITIONAL_PURCHASE',
      name: 'CONDITIONAL_PURCHASE',
      amountPerPurchase: 100000,
      direction: 'DROP',
      dropPercentage: 0.05,
    })
  })

  it('적립식 칩 설정에서 매주를 고르면 그 칩만 바꾸고 요약 줄도 바뀐다', async () => {
    renderPage()
    await selectMode('전략 비교')
    await userEvent.click(within(screen.getByRole('radiogroup', { name: '적립식 주기' })).getByRole('radio', { name: '매주' }))

    expect(summary()).toHaveTextContent('적립식: 매주 수요일 10만원')
    await runBacktest()

    const request = vi.mocked(backtestApi.compareStrategies).mock.calls[0][0]
    expect(request.strategies.map((s) => s.strategyType)).toEqual(['SIMPLE', 'DCA'])
    expect(request.strategies[1]).toEqual({
      strategyType: 'DCA',
      name: 'DCA',
      amountPerPurchase: 100000,
      frequency: 'WEEKLY',
      firstPurchaseDate: START,
    })
  })

  it('같은 종류 칩을 더하면 번호를 단 이름으로 따로 보낸다', async () => {
    renderPage()
    await selectMode('전략 비교')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 적립식' }))

    expect(strategyChips().getByRole('button', { name: '적립식 2' })).toHaveAttribute('aria-expanded', 'true')
    await vi.waitFor(() => expect(strategyChips().getByRole('button', { name: '적립식 2' })).toHaveFocus())
    await runBacktest()

    const names = vi.mocked(backtestApi.compareStrategies).mock.calls[0][0].strategies.map((s) => s.name)
    expect(names).toEqual(['SIMPLE', 'DCA', '적립식 2'])
  })

  it('칩이 하나뿐이거나 고칠 칩이 있으면 실행을 막고 요청하지 않는다', async () => {
    renderPage()
    await selectMode('전략 비교')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 조건부 매수' }))
    await userEvent.clear(screen.getByRole('textbox', { name: '전날 종가보다 몇 % 내리면' }))

    expect(strategyChips().getByRole('button', { name: '조건부 매수 (고칠 것: 하락률은 0~100 사이여야 합니다.)' })).toBeInTheDocument()
    expect(screen.getByText('고칠 칩 1개(! 표시)는 눌러서 고치기')).toBeInTheDocument()
    await userEvent.click(strategyChips().getByRole('button', { name: '단순 매수 빼기' }))
    await vi.waitFor(() => expect(strategyChips().getByRole('button', { name: '적립식' })).toHaveFocus())
    await userEvent.click(strategyChips().getByRole('button', { name: '적립식 빼기' }))
    await runBacktest()

    expect(screen.getByText('전략을 둘 이상')).toBeInTheDocument()
    expect(screen.getByTestId('backtest-run')).toHaveAttribute('aria-disabled', 'true')
    expect(backtestApi.compareStrategies).not.toHaveBeenCalled()
  })
})
