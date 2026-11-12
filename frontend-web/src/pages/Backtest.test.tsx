import { describe, it, expect, vi, beforeEach, afterEach, type MockInstance } from 'vitest'
import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import Backtest from './Backtest'
import { backtestApi, stockApi } from '../services/api'
import { saveLocalBacktestHistory } from '../utils/localBacktestHistory'
import { dismissToast, notify, readToasts } from '@/components/common/notify'
import { Notifications } from '@/components/common/Notifications'

vi.mock('../services/api', () => ({
  backtestApi: {
    runSimulation: vi.fn(),
    runDcaStrategy: vi.fn(),
    runConditionalStrategy: vi.fn(),
    runMaCrossStrategy: vi.fn(),
    runMaBandStrategy: vi.fn(),
    runPortfolio: vi.fn(),
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
  DcaConditionalResult: ({
    result,
    budget,
    windows,
    band,
  }: {
    result: { mode: string };
    budget?: number;
    windows?: { short: number; long: number };
    band?: { window: number; upperRate: number; lowerRate: number };
  }) => (
    <>
      <div>결과: {result.mode}</div>
      {budget !== undefined && <div>예산: {budget}</div>}
      {windows && <div>이동평균 기간: {`${windows.short} · ${windows.long}`}</div>}
      {band && <div>밴드: {`${band.window} · ${band.upperRate} · ${band.lowerRate}`}</div>}
    </>
  ),
}))
vi.mock('@components/backtest/results/portfolio/PortfolioResult', () => ({
  PortfolioResult: ({ result }: { result: { mode: string; totalReturnPercent?: number } }) => (
    <div>
      결과: {result.mode} {result.totalReturnPercent}
    </div>
  ),
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
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Backtest />
        <Notifications />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

const modes = () => within(screen.getByRole('radiogroup', { name: '백테스트 모드' }))
const groupTabs = () => within(screen.getByRole('tablist', { name: '모드 묶음' }))
const groupTab = (name: string) => groupTabs().getByRole('tab', { name })
const modePanel = () => within(screen.getByRole('tabpanel'))
// 모드 칩마다 그 칩이 있는 묶음 탭
const GROUP_OF: Record<string, string> = {
  단순: '기본 매매',
  적립식: '기본 매매',
  조건부: '기본 매매',
  포트폴리오: '기본 매매',
  '이동평균 교차': '차트 매매',
  '이동평균 밴드': '차트 매매',
  '종목 비교': '비교 매매',
  '전략 비교': '비교 매매',
}
const selectGroup = (name: string) => userEvent.click(groupTab(name))
const selectMode = async (name: string) => {
  await selectGroup(GROUP_OF[name])
  await userEvent.click(modes().getByRole('radio', { name }))
}
const runBacktest = () => userEvent.click(screen.getByTestId('backtest-run'))
const summary = () => screen.getByTestId('backtest-summary')
const strategyChips = () => within(screen.getByRole('group', { name: '견줄 전략' }))

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-08T12:00:00+09:00'))
  vi.clearAllMocks()
  localStorage.clear()
  auth.isAuthenticated = true
  auth.user = { email: USER }
  alertSpy = vi.spyOn(notify, 'error').mockImplementation(() => {})
  vi.mocked(stockApi.getAllSymbols).mockResolvedValue({ data: [{ symbol: 'AAPL' }, { symbol: 'MSFT' }, { symbol: 'NVDA' }] } as never)
  vi.mocked(stockApi.getOHLCData).mockResolvedValue({ data: [] } as never)
  vi.mocked(stockApi.getExchangeRatesBulk).mockResolvedValue({ data: {} } as never)
  vi.mocked(backtestApi.runSimulation).mockResolvedValue({ data: {} } as never)
  vi.mocked(backtestApi.runDcaStrategy).mockResolvedValue({ data: {} } as never)
  vi.mocked(backtestApi.runConditionalStrategy).mockResolvedValue({ data: {} } as never)
  vi.mocked(backtestApi.runMaCrossStrategy).mockResolvedValue({ data: {} } as never)
  vi.mocked(backtestApi.runMaBandStrategy).mockResolvedValue({ data: {} } as never)
  vi.mocked(backtestApi.runPortfolio).mockResolvedValue({ data: {} } as never)
  vi.mocked(backtestApi.compareSymbols).mockResolvedValue({ data: {} } as never)
  vi.mocked(backtestApi.compareStrategies).mockResolvedValue({ data: {} } as never)
})

afterEach(() => {
  alertSpy.mockRestore()
  act(() => readToasts().forEach((toast) => dismissToast(toast.id)))
  vi.useRealTimers()
})

const common = { reinvestDividends: false, tradingFeeRate: 0, dividendTaxRate: 0.15, capitalGainsTax: true, periodEnd: 'HOLD' }
const detailToggle = () => screen.getByRole('button', { name: /세부 설정/ })
const reinvestRadio = () => within(screen.getByRole('radiogroup', { name: '배당' })).getByRole('radio', { name: '재투자' })
const amountInput = () => screen.getByRole('textbox', { name: '금액(원)' })
const trailingStopInput = () => screen.getByRole('textbox', { name: '고점 대비 하락 비율(퍼센트)' })
const trailingSellInput = () => screen.getByRole('textbox', { name: '고점 대비 하락 때 팔 비율(보유 대비 퍼센트)' })
const addStep = () => userEvent.click(screen.getByRole('button', { name: '＋ 단계 더하기' }))
const stepGainInput = (n: number) => screen.getByRole('textbox', { name: `${n}단계 수익률(평균 단가 대비 퍼센트)` })
const stepSellInput = (n: number) => screen.getByRole('textbox', { name: `${n}단계 팔 비율(보유 대비 퍼센트)` })

describe('Backtest 페이지 모드 · 기록', () => {
  it('모드는 묶음 탭 셋 아래 고른 탭의 모드 칩 하나를 고르고 지난 기록은 모드 대신 제목 옆 링크로 따로 본다', () => {
    renderPage()

    expect(groupTabs().getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['기본 매매', '차트 매매', '비교 매매'])
    expect(groupTab('기본 매매')).toHaveAttribute('aria-selected', 'true')
    expect(groupTab('차트 매매')).toHaveAttribute('aria-selected', 'false')
    expect(screen.getByRole('tabpanel', { name: '기본 매매' })).toContainElement(screen.getByRole('radiogroup', { name: '백테스트 모드' }))
    expect(modes().getAllByRole('radio').map((radio) => radio.textContent)).toEqual(['단순', '적립식', '조건부', '포트폴리오'])
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

  it('차트 매매 · 비교 매매 탭은 그 묶음의 모드 칩만 보이고 처음에는 첫 모드를 고른다', async () => {
    renderPage()
    await selectGroup('차트 매매')

    expect(groupTab('차트 매매')).toHaveAttribute('aria-selected', 'true')
    expect(groupTab('기본 매매')).toHaveAttribute('aria-selected', 'false')
    expect(modes().getAllByRole('radio').map((radio) => radio.textContent)).toEqual(['이동평균 교차', '이동평균 밴드'])
    expect(modes().getByRole('radio', { name: '이동평균 교차' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('textbox', { name: '짧은 기간(일)' })).toBeInTheDocument()

    await selectGroup('비교 매매')
    expect(modes().getAllByRole('radio').map((radio) => radio.textContent)).toEqual(['종목 비교', '전략 비교'])
    expect(modes().getByRole('radio', { name: '종목 비교' })).toHaveAttribute('aria-checked', 'true')
  })

  it('탭을 바꾸면 그 묶음에서 마지막으로 고른 모드를 다시 고른다', async () => {
    renderPage()
    await selectMode('조건부')
    await selectMode('이동평균 밴드')
    await selectGroup('기본 매매')

    expect(modes().getByRole('radio', { name: '조건부' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('textbox', { name: '최대 횟수(회)' })).toBeInTheDocument()
    await selectGroup('차트 매매')
    expect(modes().getByRole('radio', { name: '이동평균 밴드' })).toHaveAttribute('aria-checked', 'true')
    await selectGroup('비교 매매')
    expect(modes().getByRole('radio', { name: '종목 비교' })).toHaveAttribute('aria-checked', 'true')
  })

  it('고른 탭을 다시 누르면 모드와 결과를 그대로 둔다', async () => {
    renderPage()
    await selectMode('적립식')
    await runBacktest()
    expect(await screen.findByText('결과: dca')).toBeInTheDocument()

    await selectGroup('기본 매매')
    expect(modes().getByRole('radio', { name: '적립식' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByText('결과: dca')).toBeInTheDocument()
  })

  it('좌우 화살표 · Home · End 로 묶음 탭을 옮기면 그 탭의 모드로 바뀌고 포커스가 따라간다', async () => {
    renderPage()
    groupTab('기본 매매').focus()

    await userEvent.keyboard('{ArrowRight}')
    expect(groupTab('차트 매매')).toHaveAttribute('aria-selected', 'true')
    expect(groupTab('차트 매매')).toHaveFocus()
    expect(modes().getByRole('radio', { name: '이동평균 교차' })).toHaveAttribute('aria-checked', 'true')
    await userEvent.keyboard('{ArrowRight}{ArrowRight}')
    expect(groupTab('기본 매매')).toHaveFocus()
    expect(modes().getByRole('radio', { name: '단순' })).toHaveAttribute('aria-checked', 'true')
    await userEvent.keyboard('{ArrowLeft}')
    expect(groupTab('비교 매매')).toHaveFocus()
    expect(modes().getByRole('radio', { name: '종목 비교' })).toHaveAttribute('aria-checked', 'true')
    await userEvent.keyboard('{Home}')
    expect(groupTab('기본 매매')).toHaveFocus()
    await userEvent.keyboard('{End}')
    expect(groupTab('비교 매매')).toHaveFocus()
    expect(groupTabs().getAllByRole('tab').map((tab) => tab.tabIndex)).toEqual([-1, -1, 0])
  })

  it('모드 칩 아래에 고른 모드의 한 줄 설명을 보인다', async () => {
    renderPage()
    expect(modePanel().getByText('시작일에 한 번에 사서 끝까지 보유')).toBeInTheDocument()

    for (const [name, about] of [
      ['적립식', '정한 주기마다 같은 금액으로 매수'],
      ['조건부', '전날보다 정한 % 내리거나 오른 날 매수'],
      ['포트폴리오', '여러 종목을 비중대로 한 계좌에 담아 운용'],
      ['이동평균 교차', '최근 20일 평균 가격이 60일 평균보다 높아지면 매수, 낮아지면 매도'],
      ['이동평균 밴드', '주가가 200일 평균 가격보다 2.5% 넘게 오르면 매수, 2.5% 넘게 내려가면 매도'],
      ['종목 비교', '같은 방식으로 여러 종목의 결과 비교'],
      ['전략 비교', '한 종목에 여러 방식의 결과 비교'],
    ]) {
      await selectMode(name)
      expect(modePanel().getByText(about)).toBeInTheDocument()
    }
  })

  it('이동평균 교차 · 밴드 설명은 그 모드 칸의 기간 · 비율을 따르고 빈 칸은 ? 로 보인다', async () => {
    renderPage()
    await selectMode('이동평균 교차')
    fireEvent.change(screen.getByRole('textbox', { name: '짧은 기간(일)' }), { target: { value: '5' } })
    fireEvent.change(screen.getByRole('textbox', { name: '긴 기간(일)' }), { target: { value: '' } })
    expect(modePanel().getByText('최근 5일 평균 가격이 ?일 평균보다 높아지면 매수, 낮아지면 매도')).toBeInTheDocument()

    await selectMode('이동평균 밴드')
    fireEvent.change(screen.getByRole('textbox', { name: '평균 기간(일)' }), { target: { value: '100' } })
    fireEvent.change(screen.getByRole('textbox', { name: '위 비율(평균 대비 퍼센트)' }), { target: { value: '3' } })
    fireEvent.change(screen.getByRole('textbox', { name: '아래 비율(평균 대비 퍼센트)' }), { target: { value: '' } })
    expect(modePanel().getByText('주가가 100일 평균 가격보다 3% 넘게 오르면 매수, ?% 넘게 내려가면 매도')).toBeInTheDocument()
  })

  it('모드 칩 묶음은 고른 모드의 한 줄 설명을 접근성 설명으로 가리킨다', async () => {
    renderPage()
    const group = () => screen.getByRole('radiogroup', { name: '백테스트 모드' })
    expect(group()).toHaveAccessibleDescription('시작일에 한 번에 사서 끝까지 보유')

    await selectMode('이동평균 교차')
    expect(group()).toHaveAccessibleDescription('최근 20일 평균 가격이 60일 평균보다 높아지면 매수, 낮아지면 매도')
  })

  it('설정 초기화 · 되돌리기 · 직전 설정으로 모드가 바뀌면 그 모드의 묶음 탭을 함께 고른다', async () => {
    renderPage()
    await selectMode('이동평균 밴드')
    await runBacktest()

    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    expect(groupTab('기본 매매')).toHaveAttribute('aria-selected', 'true')
    expect(modes().getByRole('radio', { name: '단순' })).toHaveAttribute('aria-checked', 'true')
    await userEvent.click(screen.getByRole('button', { name: '되돌리기' }))
    expect(groupTab('차트 매매')).toHaveAttribute('aria-selected', 'true')
    expect(modes().getByRole('radio', { name: '이동평균 밴드' })).toHaveAttribute('aria-checked', 'true')

    await selectMode('전략 비교')
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))
    expect(groupTab('차트 매매')).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tabpanel', { name: '차트 매매' })).toBeInTheDocument()
    expect(modes().getByRole('radio', { name: '이동평균 밴드' })).toHaveAttribute('aria-checked', 'true')
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
    expect(screen.queryByText(/예산:/)).toBeNull()
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
    expect(screen.getByText('예산: 1000000')).toBeInTheDocument()
  })

  it('조건부: 상승을 고르면 상승 방향으로 요청한다', async () => {
    renderPage()
    await selectMode('조건부')
    await userEvent.click(screen.getByRole('radio', { name: '상승' }))
    await runBacktest()

    expect(vi.mocked(backtestApi.runConditionalStrategy).mock.calls[0][0]).toMatchObject({ direction: 'RISE', dropPercentage: 0.05 })
  })

  it('조건부: 익절을 적으면 배당 재투자를 막고 비율로 요청하며, 다른 모드는 고른 재투자 그대로', async () => {
    renderPage()
    await selectMode('조건부')
    await userEvent.click(detailToggle())
    await userEvent.click(reinvestRadio())
    expect(reinvestRadio()).toHaveAttribute('aria-checked', 'true')

    await userEvent.type(screen.getByRole('textbox', { name: '익절(평균 단가 대비 퍼센트)' }), '20')

    expect(reinvestRadio()).toHaveAttribute('aria-disabled', 'true')
    expect(reinvestRadio()).toHaveAttribute('aria-checked', 'false')
    await runBacktest()
    expect(vi.mocked(backtestApi.runConditionalStrategy).mock.calls[0][0]).toMatchObject({
      takeProfitRate: 0.2,
      reinvestDividends: false,
    })
    await selectMode('적립식')
    await runBacktest()
    expect(vi.mocked(backtestApi.runDcaStrategy).mock.calls[0][0]).toMatchObject({ reinvestDividends: true })
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
    expect(await screen.findByText('예산: 5000000')).toBeInTheDocument()
  })

  it('이동평균 교차: 기본값은 원금 100만원 · 20일 · 60일로 요청하고 결과는 그 기간과 함께 전략 결과로 그린다', async () => {
    renderPage()
    await selectMode('이동평균 교차')
    await runBacktest()

    expect(backtestApi.runMaCrossStrategy).toHaveBeenCalledWith({
      symbol: 'AAPL',
      startDate: START,
      endDate: TODAY,
      investmentAmount: 1000000,
      shortWindow: 20,
      longWindow: 60,
      ...common,
    })
    expect(await screen.findByText('결과: ma-cross')).toBeInTheDocument()
    expect(screen.getByText('이동평균 기간: 20 · 60')).toBeInTheDocument()
    expect(screen.queryByText(/예산:/)).toBeNull()
  })

  it('이동평균 교차: 바꾼 원금 · 기간과 마지막 거래일에 팔기로 요청한다', async () => {
    renderPage()
    await selectMode('이동평균 교차')
    fireEvent.change(screen.getByRole('textbox', { name: '원금(원)' }), { target: { value: '3,000,000' } })
    fireEvent.change(screen.getByRole('textbox', { name: '짧은 기간(일)' }), { target: { value: '5' } })
    fireEvent.change(screen.getByRole('textbox', { name: '긴 기간(일)' }), { target: { value: '120' } })
    await userEvent.click(detailToggle())
    await userEvent.click(screen.getByRole('radio', { name: '마지막 거래일에 팔기' }))
    await runBacktest()

    expect(vi.mocked(backtestApi.runMaCrossStrategy).mock.calls[0][0]).toMatchObject({
      investmentAmount: 3000000,
      shortWindow: 5,
      longWindow: 120,
      periodEnd: 'SELL_AT_END',
    })
    expect(await screen.findByText('결과: ma-cross')).toBeInTheDocument()
    expect(screen.getByText('이동평균 기간: 5 · 120')).toBeInTheDocument()
  })

  it('이동평균 교차: 배당 재투자를 막고 false 로 요청하며, 다른 모드는 고른 재투자 그대로', async () => {
    renderPage()
    await selectMode('적립식')
    await userEvent.click(detailToggle())
    await userEvent.click(reinvestRadio())
    await selectMode('이동평균 교차')

    expect(reinvestRadio()).toHaveAttribute('aria-disabled', 'true')
    expect(reinvestRadio()).toHaveAttribute('aria-checked', 'false')
    expect(reinvestRadio()).toHaveAccessibleDescription('이동평균 교차는 배당을 현금으로')
    await runBacktest()
    expect(vi.mocked(backtestApi.runMaCrossStrategy).mock.calls[0][0]).toMatchObject({ reinvestDividends: false })
    await selectMode('적립식')
    await runBacktest()
    expect(vi.mocked(backtestApi.runDcaStrategy).mock.calls[0][0]).toMatchObject({ reinvestDividends: true })
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
    await userEvent.click(detailToggle())
    await userEvent.click(screen.getByRole('button', { name: '배당세 15%' }))
    expect(summary()).toHaveTextContent('시작일에 30만원 매수 · 배당세 끔')
  })

  it('적립식 · 조건부 요약은 회차 · 한도를 보이고 종가가 없는 오늘 회차는 세지 않는다', async () => {
    renderPage()
    await selectMode('적립식')
    expect(summary()).toHaveTextContent('매달 8일 10만원 · 12회 ≈ 120만원')

    await selectMode('조건부')
    expect(summary()).toHaveTextContent('전날보다 5% 내릴 때마다 10만원 · 예산으로 최대 10회 ≈ 100만원')
    expect(screen.getByRole('textbox', { name: '최대 횟수(회)' })).toHaveAttribute('placeholder', '예산 한도까지')
    await userEvent.click(screen.getByRole('button', { name: '예산 비우기' }))
    expect(screen.getByRole('textbox', { name: '최대 횟수(회)' })).toHaveAttribute('placeholder', '제한 없음')
    expect(summary()).toHaveTextContent('횟수 제한 없음 · 많아야 약 261회')
  })

  it('이동평균 밴드: 기본값은 원금 100만원 · 200일선 · 위 · 아래 2.5% 로 요청하고 결과는 그 설정과 함께 전략 결과로 그린다', async () => {
    renderPage()
    await selectMode('이동평균 밴드')
    await runBacktest()

    expect(backtestApi.runMaBandStrategy).toHaveBeenCalledWith({
      symbol: 'AAPL',
      startDate: START,
      endDate: TODAY,
      investmentAmount: 1000000,
      bandWindow: 200,
      upperRate: 0.025,
      lowerRate: 0.025,
      ...common,
    })
    expect(await screen.findByText('결과: ma-band')).toBeInTheDocument()
    expect(screen.getByText('밴드: 200 · 0.025 · 0.025')).toBeInTheDocument()
  })

  it('이동평균 밴드: 바꾼 원금 · 평균 기간 · 비율로 요청하고, 배당 재투자를 막고 false 로 보낸다', async () => {
    renderPage()
    await selectMode('적립식')
    await userEvent.click(detailToggle())
    await userEvent.click(reinvestRadio())
    await selectMode('이동평균 밴드')
    fireEvent.change(screen.getByRole('textbox', { name: '원금(원)' }), { target: { value: '3,000,000' } })
    fireEvent.change(screen.getByRole('textbox', { name: '평균 기간(일)' }), { target: { value: '120' } })
    fireEvent.change(screen.getByRole('textbox', { name: '위 비율(평균 대비 퍼센트)' }), { target: { value: '3' } })
    fireEvent.change(screen.getByRole('textbox', { name: '아래 비율(평균 대비 퍼센트)' }), { target: { value: '5' } })

    expect(reinvestRadio()).toHaveAttribute('aria-disabled', 'true')
    expect(reinvestRadio()).toHaveAccessibleDescription('밴드는 배당을 현금으로')
    await runBacktest()

    expect(vi.mocked(backtestApi.runMaBandStrategy).mock.calls[0][0]).toMatchObject({
      investmentAmount: 3000000,
      bandWindow: 120,
      upperRate: 0.03,
      lowerRate: 0.05,
      reinvestDividends: false,
    })
    expect(await screen.findByText('밴드: 120 · 0.03 · 0.05')).toBeInTheDocument()
  })

  it('이동평균 밴드 요약은 원금과 밴드 규칙을 보이고, 비율이 100 이상이면 그 칸으로 가며 요청하지 않는다', async () => {
    renderPage()
    await selectMode('이동평균 밴드')
    expect(summary()).toHaveTextContent('원금 100만원 · 200일선 +2.5% 위로 오르면 가진 돈 전부로 사고, -2.5% 아래로 내려가면 전부 팜')

    fireEvent.change(screen.getByRole('textbox', { name: '아래 비율(평균 대비 퍼센트)' }), { target: { value: '100' } })
    expect(screen.getByText('0 이상 100 미만')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('고칠 칸 1개')
    await runBacktest()

    expect(screen.getByRole('textbox', { name: '아래 비율(평균 대비 퍼센트)' })).toHaveFocus()
    expect(backtestApi.runMaBandStrategy).not.toHaveBeenCalled()
  })

  it('이동평균 밴드 원금 · 밴드 칸도 직전 설정으로 되살리고 설정 초기화로 기본값으로 돌린다', async () => {
    renderPage()
    await selectMode('이동평균 밴드')
    fireEvent.change(screen.getByRole('textbox', { name: '평균 기간(일)' }), { target: { value: '150' } })
    fireEvent.change(screen.getByRole('textbox', { name: '위 비율(평균 대비 퍼센트)' }), { target: { value: '4' } })
    await runBacktest()

    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    await selectMode('이동평균 밴드')
    expect(screen.getByRole('textbox', { name: '평균 기간(일)' })).toHaveValue('200')
    expect(screen.getByRole('textbox', { name: '위 비율(평균 대비 퍼센트)' })).toHaveValue('2.5')
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))

    expect(modes().getByRole('radio', { name: '이동평균 밴드' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('textbox', { name: '평균 기간(일)' })).toHaveValue('150')
    expect(screen.getByRole('textbox', { name: '위 비율(평균 대비 퍼센트)' })).toHaveValue('4')
    expect(screen.getByRole('textbox', { name: '아래 비율(평균 대비 퍼센트)' })).toHaveValue('2.5')
  })

  it('이동평균 교차 요약은 원금과 교차 규칙을 보이고, 짧은 기간이 긴 기간 이상이면 그 칸으로 가며 요청하지 않는다', async () => {
    renderPage()
    await selectMode('이동평균 교차')
    expect(summary()).toHaveTextContent('원금 100만원 · 20일 선이 60일 선을 넘으면 가진 돈 전부로 사고, 아래로 내려가면 전부 팜')

    fireEvent.change(screen.getByRole('textbox', { name: '짧은 기간(일)' }), { target: { value: '60' } })
    expect(screen.getByText('긴 기간보다 짧게')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('고칠 칸 1개')
    await runBacktest()

    expect(screen.getByRole('textbox', { name: '짧은 기간(일)' })).toHaveFocus()
    expect(backtestApi.runMaCrossStrategy).not.toHaveBeenCalled()
    expect(alertSpy).not.toHaveBeenCalled()
  })

  it('이동평균 교차: 신호 종목으로 S&P 500 을 고르면 ^GSPC 로 요청하고 요약에 신호를 적으며, 매매 종목과 같게로 돌리면 신호 종목을 보내지 않는다', async () => {
    renderPage()
    await selectMode('이동평균 교차')
    expect(screen.getByRole('radio', { name: '매매 종목과 같게' })).toHaveAttribute('aria-checked', 'true')
    await userEvent.click(screen.getByRole('radio', { name: 'S&P 500' }))

    expect(summary()).toHaveTextContent('원금 100만원 · 신호 ^GSPC · 20일 선이 60일 선을 넘으면')
    await runBacktest()
    expect(vi.mocked(backtestApi.runMaCrossStrategy).mock.calls[0][0]).toMatchObject({ symbol: 'AAPL', signalSymbol: '^GSPC' })

    await userEvent.click(screen.getByRole('radio', { name: '매매 종목과 같게' }))
    await runBacktest()
    expect(vi.mocked(backtestApi.runMaCrossStrategy).mock.calls[1][0]).not.toHaveProperty('signalSymbol')
  })

  it('이동평균 밴드: 신호를 다른 종목으로 고르면 비었을 때 그 칸으로 가며 요청하지 않고, 목록에서 고른 종목으로 요청한다', async () => {
    renderPage()
    await selectMode('이동평균 밴드')
    await userEvent.click(screen.getByRole('radio', { name: '다른 종목' }))

    expect(screen.getByText('신호 종목 비어 있음')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('고칠 칸 1개')
    await runBacktest()
    expect(screen.getByRole('combobox', { name: '신호 종목' })).toHaveFocus()
    expect(backtestApi.runMaBandStrategy).not.toHaveBeenCalled()

    await userEvent.type(screen.getByRole('combobox', { name: '신호 종목' }), 'MS')
    await userEvent.click(screen.getByRole('option', { name: /MSFT/ }))
    expect(summary()).toHaveTextContent('원금 100만원 · 신호 MSFT · 200일선')
    await runBacktest()
    expect(vi.mocked(backtestApi.runMaBandStrategy).mock.calls[0][0]).toMatchObject({ symbol: 'AAPL', signalSymbol: 'MSFT' })
  })

  it('신호 종목 고르기도 직전 설정으로 되살리고 설정 초기화로 매매 종목과 같게로 돌린다', async () => {
    renderPage()
    await selectMode('이동평균 교차')
    await userEvent.click(screen.getByRole('radio', { name: '나스닥 100' }))
    await runBacktest()

    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    await selectMode('이동평균 교차')
    expect(screen.getByRole('radio', { name: '매매 종목과 같게' })).toHaveAttribute('aria-checked', 'true')
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))

    expect(screen.getByRole('radio', { name: '나스닥 100' })).toHaveAttribute('aria-checked', 'true')
    expect(summary()).toHaveTextContent('신호 ^NDX')
  })

  it('조건부: 트레일링 스탑을 적으면 요약에 적고 배당 재투자를 막으며 하락 · 팔 비율로 요청한다', async () => {
    renderPage()
    await selectMode('조건부')
    await userEvent.type(trailingStopInput(), '10')
    await userEvent.type(trailingSellInput(), '50')

    expect(summary()).toHaveTextContent('고점 대비 하락 매도 -10%(보유의 50%씩)')
    await userEvent.click(detailToggle())
    expect(reinvestRadio()).toHaveAttribute('aria-disabled', 'true')
    await runBacktest()
    expect(vi.mocked(backtestApi.runConditionalStrategy).mock.calls[0][0]).toMatchObject({
      trailingStopRate: 0.1,
      trailingSellRate: 0.5,
      reinvestDividends: false,
    })
  })

  it('이동평균 밴드: 트레일링 하락을 지우고 팔 비율만 남기면 그 칸으로 가며 요청하지 않고, 하락을 다시 적으면 두 비율로 요청한다', async () => {
    renderPage()
    await selectMode('이동평균 밴드')
    await userEvent.type(trailingStopInput(), '15')
    await userEvent.type(trailingSellInput(), '50')
    await userEvent.clear(trailingStopInput())

    expect(screen.getByText('하락 비율과 함께')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('고칠 칸 1개')
    await runBacktest()
    expect(trailingSellInput()).toHaveFocus()
    expect(backtestApi.runMaBandStrategy).not.toHaveBeenCalled()

    await userEvent.type(trailingStopInput(), '15')
    expect(summary()).toHaveTextContent('-2.5% 아래로 내려가면 전부 팜 · 고점 대비 하락 매도 -15%(보유의 50%씩)')
    await runBacktest()
    expect(vi.mocked(backtestApi.runMaBandStrategy).mock.calls[0][0]).toMatchObject({
      trailingStopRate: 0.15,
      trailingSellRate: 0.5,
    })
  })

  it('이동평균 교차: 트레일링 스탑을 적으면 요약에 적고 하락 비율로 요청하며, 직전 설정으로 되살리고 설정 초기화로 비운다', async () => {
    renderPage()
    await selectMode('이동평균 교차')
    await userEvent.type(trailingStopInput(), '8')

    expect(summary()).toHaveTextContent('아래로 내려가면 전부 팜 · 고점 대비 하락 매도 -8%(전량)')
    await runBacktest()
    expect(vi.mocked(backtestApi.runMaCrossStrategy).mock.calls[0][0]).toMatchObject({ trailingStopRate: 0.08 })
    expect(vi.mocked(backtestApi.runMaCrossStrategy).mock.calls[0][0]).not.toHaveProperty('trailingSellRate')

    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    await selectMode('이동평균 교차')
    expect(trailingStopInput()).toHaveValue('')
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))
    expect(trailingStopInput()).toHaveValue('8')
  })

  it('조건부: 부분 익절 단계를 더해 적으면 요약에 적고 배당 재투자를 막으며 단계 비율로 요청한다', async () => {
    renderPage()
    await selectMode('조건부')
    await addStep()
    await userEvent.type(stepGainInput(1), '20')
    await userEvent.type(stepSellInput(1), '25')
    await addStep()
    await userEvent.type(stepGainInput(2), '50')
    await userEvent.type(stepSellInput(2), '100')

    expect(summary()).toHaveTextContent('부분 익절 +20%에 보유의 25%, +50%에 전량(평균 단가 기준)')
    await userEvent.click(detailToggle())
    expect(reinvestRadio()).toHaveAttribute('aria-disabled', 'true')
    await runBacktest()
    expect(vi.mocked(backtestApi.runConditionalStrategy).mock.calls[0][0]).toMatchObject({
      partialTakeProfits: [
        { gainRate: 0.2, sellRate: 0.25 },
        { gainRate: 0.5, sellRate: 1 },
      ],
      reinvestDividends: false,
    })
  })

  it('이동평균 밴드: 부분 익절 수익률이 앞 단계보다 작으면 그 칸으로 가며 요청하지 않고, 앞 단계를 지우면 남은 단계로 요청한다', async () => {
    renderPage()
    await selectMode('이동평균 밴드')
    await addStep()
    await userEvent.type(stepGainInput(1), '30')
    await userEvent.type(stepSellInput(1), '50')
    await addStep()
    await userEvent.type(stepGainInput(2), '10')
    await userEvent.type(stepSellInput(2), '50')

    expect(screen.getByText('2단계 수익률 앞 단계보다 크게')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('고칠 칸 1개')
    await runBacktest()
    expect(stepGainInput(2)).toHaveFocus()
    expect(backtestApi.runMaBandStrategy).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: '1단계 지우기' }))
    await runBacktest()
    expect(vi.mocked(backtestApi.runMaBandStrategy).mock.calls[0][0]).toMatchObject({
      partialTakeProfits: [{ gainRate: 0.1, sellRate: 0.5 }],
    })
  })

  it('이동평균 교차: 부분 익절 단계를 요약에 적고 요청하며, 직전 설정으로 되살리고 설정 초기화로 비운다', async () => {
    renderPage()
    await selectMode('이동평균 교차')
    await addStep()
    await userEvent.type(stepGainInput(1), '15')
    await userEvent.type(stepSellInput(1), '30')

    expect(summary()).toHaveTextContent('아래로 내려가면 전부 팜 · 부분 익절 +15%에 보유의 30%(평균 단가 기준)')
    await runBacktest()
    expect(vi.mocked(backtestApi.runMaCrossStrategy).mock.calls[0][0]).toMatchObject({
      partialTakeProfits: [{ gainRate: 0.15, sellRate: 0.3 }],
    })

    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    await selectMode('이동평균 교차')
    expect(screen.queryByRole('textbox', { name: '1단계 수익률(평균 단가 대비 퍼센트)' })).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))
    expect(stepGainInput(1)).toHaveValue('15')
    expect(stepSellInput(1)).toHaveValue('30')
  })

  it('주식 수 추정에 쓸 시작일 환율이 없으면 칸 · 요약에 기본 환율 기준을 적는다', async () => {
    vi.mocked(stockApi.getOHLCData).mockResolvedValue({ data: [{ symbol: 'AAPL', date: START, closePrice: 200, adjustedClose: 200 }] } as never)
    renderPage()
    await userEvent.click(screen.getByRole('radio', { name: '주식 수' }))

    expect(await screen.findByText('≈ 27만원(시작일 값 · 기본 환율 기준)')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('시작일에 1주 매수 ≈ 27만원(시작일 값 · 기본 환율 기준)')
    await selectMode('적립식')
    await userEvent.click(screen.getByRole('radio', { name: '주식 수' }))
    expect(await screen.findByText('≈ 27만원(시작일 값 · 기본 환율 기준)')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('매달 8일 1주 · 12회 ≈ 324만원(시작일 값 · 기본 환율 기준)')
    await selectMode('조건부')
    await userEvent.click(screen.getByRole('radio', { name: '주식 수' }))
    expect(await screen.findByText('≈ 27만원(시작일 값 · 기본 환율 기준)')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('(시작일 값 · 기본 환율 기준)')
  })

  it('주식 수가 1,000,000 을 넘으면 계산 상한 안내로 막고 요청하지 않는다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('radio', { name: '주식 수' }))
    fireEvent.change(screen.getByRole('textbox', { name: '주식 수(주)' }), { target: { value: '1000001' } })

    expect(screen.getByRole('textbox', { name: '주식 수(주)' })).toHaveAccessibleDescription(
      '한 번에 1,000,000주까지 계산 · 더 크면 금액으로'
    )
    await runBacktest()
    expect(screen.getByRole('textbox', { name: '주식 수(주)' })).toHaveFocus()
    expect(backtestApi.runSimulation).not.toHaveBeenCalled()
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

describe('Backtest 페이지 포트폴리오', () => {
  const rowSymbol = (n: number) => screen.getByRole('combobox', { name: `${n}번 종목` })
  const rowAmount = (n: number) => screen.getByRole('textbox', { name: `${n}번 금액(원)` })
  const rowWeight = (n: number) => screen.getByRole('textbox', { name: `${n}번 비율(퍼센트)` })
  const rowTakeProfit = (n: number) => screen.getByRole('textbox', { name: `${n}번 익절(평균 단가 대비 퍼센트)` })
  const rowStopLoss = (n: number) => screen.getByRole('textbox', { name: `${n}번 손절(평균 단가 대비 퍼센트)` })
  const rowStrategy = (n: number) => screen.getByRole('combobox', { name: `${n}번 전략` })
  const rowField = (n: number, name: string) => screen.getByRole('textbox', { name: `${n}번 ${name}` })
  const addRow = () => userEvent.click(screen.getByRole('button', { name: '＋ 종목 더하기' }))
  const method = (name: string) => within(screen.getByRole('radiogroup', { name: '방식' })).getByRole('radio', { name })
  const rebalance = (name: string) => within(screen.getByRole('radiogroup', { name: '리밸런싱' })).getByRole('radio', { name })
  const pickSymbol = async (input: HTMLElement, text: string, name: RegExp) => {
    await userEvent.clear(input)
    await userEvent.type(input, text)
    await userEvent.click(await screen.findByRole('option', { name }))
  }

  it('처음 줄 하나(지금 종목 · ₩10,000,000)를 한 번에 사기로 요청하고 요약에 종목 수 · 합계를 적는다', async () => {
    renderPage()
    await selectMode('포트폴리오')

    expect(screen.queryByRole('combobox', { name: '종목' })).toBeNull()
    expect(rowSymbol(1)).toHaveValue('AAPL')
    expect(rowAmount(1)).toHaveValue('10,000,000')
    expect(method('한 번에 사기')).toHaveAttribute('aria-checked', 'true')
    expect(summary()).toHaveTextContent('AAPL · 2025.10.08 → 2026.10.08')
    expect(summary()).toHaveTextContent('종목 1개 · 한 번에 1,000만원')
    await runBacktest()

    expect(backtestApi.runPortfolio).toHaveBeenCalledWith({
      startDate: START,
      endDate: TODAY,
      allocation: 'AMOUNT',
      assets: [{ symbol: 'AAPL', amount: 10000000 }],
      method: 'LUMP_SUM',
      rebalance: 'NONE',
      ...common,
    })
    expect(await screen.findByText('결과: portfolio')).toBeInTheDocument()
  })

  it('결과는 포트폴리오 결과 화면으로 그리고, 모드를 바꾸면 지운다', async () => {
    vi.mocked(backtestApi.runPortfolio).mockResolvedValue({ data: { totalReturnPercent: 12.5 } } as never)
    renderPage()
    await selectMode('포트폴리오')
    await runBacktest()

    expect(await screen.findByText('결과: portfolio 12.5')).toBeInTheDocument()
    await selectMode('단순')
    expect(screen.queryByText(/결과: portfolio/)).toBeNull()
  })

  it('처음 줄은 고치기 전까지 보던 종목을 따라가고, 고친 뒤에는 그대로 둔다', async () => {
    renderPage()
    await pickSymbol(screen.getByRole('combobox', { name: '종목' }), 'nv', /NVDA/)
    await selectMode('포트폴리오')
    expect(rowSymbol(1)).toHaveValue('NVDA')

    fireEvent.change(rowAmount(1), { target: { value: '5,000,000' } })
    await selectMode('단순')
    await pickSymbol(screen.getByRole('combobox', { name: '종목' }), 'ms', /MSFT/)
    await selectMode('포트폴리오')

    expect(rowSymbol(1)).toHaveValue('NVDA')
    expect(rowAmount(1)).toHaveValue('5,000,000')
  })

  it('종목을 더해 비율로 나누고 적립식을 고르면 총액 · 비율 · 주기로 요청하고 요약에 회당 합계 · 회차를 적는다', async () => {
    renderPage()
    await selectMode('포트폴리오')
    await addRow()
    await pickSymbol(rowSymbol(2), 'ms', /MSFT/)
    await userEvent.click(screen.getByRole('radio', { name: '비율' }))
    fireEvent.change(rowWeight(1), { target: { value: '60' } })
    fireEvent.change(rowWeight(2), { target: { value: '40' } })
    await userEvent.click(method('적립식'))

    expect(screen.getByRole('textbox', { name: '회당 총액(원)' })).toHaveValue('10,000,000')
    expect(screen.getByRole('radiogroup', { name: '언제' })).toBeInTheDocument()
    expect(summary()).toHaveTextContent('AAPL, MSFT · 2025.10.08')
    expect(summary()).toHaveTextContent('종목 2개 · 매달 8일 1,000만원 · 12회')
    await runBacktest()

    expect(backtestApi.runPortfolio).toHaveBeenCalledWith({
      startDate: START,
      endDate: TODAY,
      allocation: 'WEIGHT',
      totalAmount: 10000000,
      assets: [
        { symbol: 'AAPL', weightPercent: 60 },
        { symbol: 'MSFT', weightPercent: 40 },
      ],
      method: 'DCA',
      frequency: 'MONTHLY',
      purchaseDay: 8,
      investmentInterval: 1,
      rebalance: 'NONE',
      ...common,
    })
  })

  it('리밸런싱은 처음에 없음, 주기를 고르면 요약에 적고 그 주기로 요청한다', async () => {
    renderPage()
    await selectMode('포트폴리오')

    expect(within(screen.getByRole('radiogroup', { name: '리밸런싱' })).getAllByRole('radio').map((radio) => radio.textContent)).toEqual([
      '없음',
      '매년',
      '분기마다',
      '매달',
    ])
    expect(rebalance('없음')).toHaveAttribute('aria-checked', 'true')
    expect(summary()).not.toHaveTextContent('리밸런싱')
    await userEvent.click(rebalance('분기마다'))

    expect(summary()).toHaveTextContent('종목 1개 · 한 번에 1,000만원 · 분기마다 처음 비중으로 리밸런싱')
    await runBacktest()
    expect(backtestApi.runPortfolio).toHaveBeenCalledWith(expect.objectContaining({ method: 'LUMP_SUM', rebalance: 'QUARTERLY' }))
  })

  it('줄마다 익절 · 손절을 적으면 요약에 적고 그 줄에만 비율로 싣고, 범위 밖이면 고칠 칸으로 세며, 배당 재투자를 잠그지 않고 직전 설정으로 되살린다', async () => {
    renderPage()
    await selectMode('포트폴리오')
    await addRow()
    await pickSymbol(rowSymbol(2), 'ms', /MSFT/)
    fireEvent.change(rowAmount(2), { target: { value: '3,000,000' } })

    expect(rowTakeProfit(1)).toHaveValue('')
    expect(summary()).not.toHaveTextContent('익절')
    fireEvent.change(rowTakeProfit(1), { target: { value: '30' } })
    fireEvent.change(rowStopLoss(1), { target: { value: '10' } })
    expect(summary()).toHaveTextContent('종목 2개 · 한 번에 1,300만원 · 1개 종목에 익절 · 손절(평균 단가 기준 전량)')
    await userEvent.click(detailToggle())
    await userEvent.click(reinvestRadio())
    expect(reinvestRadio()).not.toHaveAttribute('aria-disabled')
    expect(reinvestRadio()).toHaveAttribute('aria-checked', 'true')

    fireEvent.change(rowStopLoss(2), { target: { value: '100' } })
    expect(screen.getByText('2번 줄 손절 100 아래')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('고칠 칸 1개')
    await runBacktest()
    expect(rowStopLoss(2)).toHaveFocus()
    expect(backtestApi.runPortfolio).not.toHaveBeenCalled()

    fireEvent.change(rowStopLoss(2), { target: { value: '' } })
    await runBacktest()
    expect(backtestApi.runPortfolio).toHaveBeenCalledWith(
      expect.objectContaining({
        assets: [
          { symbol: 'AAPL', amount: 10000000, takeProfitRate: 0.3, stopLossRate: 0.1 },
          { symbol: 'MSFT', amount: 3000000 },
        ],
        reinvestDividends: true,
      })
    )

    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    await selectMode('포트폴리오')
    expect(rowStopLoss(1)).toHaveValue('')
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))
    expect(rowTakeProfit(1)).toHaveValue('30')
    expect(rowStopLoss(1)).toHaveValue('10')
    expect(rowTakeProfit(2)).toHaveValue('')
  })

  it('줄마다 전략을 고르면 그 줄에 이동평균 · 밴드 칸을 보이고 그 줄에만 전략을 싣고, 적립식 줄이 있으면 한 번에 사기라도 주기 칸을 보이며(합계는 회당이 아님) 직전 설정으로 되살린다', async () => {
    renderPage()
    await selectMode('포트폴리오')
    await addRow()
    await pickSymbol(rowSymbol(2), 'ms', /MSFT/)
    fireEvent.change(rowAmount(2), { target: { value: '3,000,000' } })
    await addRow()
    await pickSymbol(rowSymbol(3), 'nv', /NVDA/)
    fireEvent.change(rowAmount(3), { target: { value: '2,000,000' } })

    expect(rowStrategy(1)).toHaveValue('LUMP_SUM')
    expect(screen.queryByRole('radiogroup', { name: '언제' })).toBeNull()
    await userEvent.selectOptions(rowStrategy(2), '이동평균 교차')
    fireEvent.change(rowField(2, '짧은 기간(일)'), { target: { value: '5' } })
    await userEvent.selectOptions(rowStrategy(3), '밴드')
    fireEvent.change(rowField(3, '위 비율(평균 대비 퍼센트)'), { target: { value: '1.5' } })
    await userEvent.selectOptions(rowStrategy(1), '적립식')
    expect(method('한 번에 사기')).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('radiogroup', { name: '언제' })).toBeInTheDocument()
    expect(screen.getByTestId('portfolio-sum')).toHaveTextContent(/^합계 ₩15,000,000$/)

    fireEvent.change(rowField(2, '긴 기간(일)'), { target: { value: '5' } })
    expect(screen.getByText('2번 줄 짧은 기간 긴 기간보다 짧게')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('고칠 칸 1개')
    await runBacktest()
    expect(rowField(2, '짧은 기간(일)')).toHaveFocus()
    expect(backtestApi.runPortfolio).not.toHaveBeenCalled()

    fireEvent.change(rowField(2, '긴 기간(일)'), { target: { value: '30' } })
    await runBacktest()
    expect(backtestApi.runPortfolio).toHaveBeenCalledWith(
      expect.objectContaining({
        assets: [
          { symbol: 'AAPL', amount: 10000000, strategy: { type: 'DCA' } },
          { symbol: 'MSFT', amount: 3000000, strategy: { type: 'MA_CROSS', shortWindow: 5, longWindow: 30 } },
          { symbol: 'NVDA', amount: 2000000, strategy: { type: 'MA_BAND', bandWindow: 200, upperRate: 0.015, lowerRate: 0.025 } },
        ],
        method: 'LUMP_SUM',
        frequency: 'MONTHLY',
        purchaseDay: 8,
        investmentInterval: 1,
      })
    )

    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    await selectMode('포트폴리오')
    expect(rowStrategy(1)).toHaveValue('LUMP_SUM')
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))
    expect(rowStrategy(1)).toHaveValue('DCA')
    expect(rowStrategy(2)).toHaveValue('MA_CROSS')
    expect(rowField(2, '긴 기간(일)')).toHaveValue('30')
    expect(rowStrategy(3)).toHaveValue('MA_BAND')
    expect(rowField(3, '위 비율(평균 대비 퍼센트)')).toHaveValue('1.5')
  })

  it('줄 전략이 섞이면 요약에 전략마다 줄 수와 금액을 적고, 리밸런싱은 이동평균 교차 · 밴드 줄을 빼고 나눈다고 적는다', async () => {
    renderPage()
    await selectMode('포트폴리오')
    await addRow()
    await pickSymbol(rowSymbol(2), 'ms', /MSFT/)
    fireEvent.change(rowAmount(2), { target: { value: '3,000,000' } })
    await userEvent.selectOptions(rowStrategy(2), '이동평균 교차')

    expect(summary()).toHaveTextContent('종목 2개 · 한 번에 1개 1,000만원 · 이동평균 교차 1개 첫 매수 신호에 300만원')
    await userEvent.click(rebalance('매년'))
    expect(summary()).toHaveTextContent('· 매년 처음 비중으로 리밸런싱(이동평균 교차 · 밴드 빼고)')
    await userEvent.selectOptions(rowStrategy(1), '적립식')
    expect(summary()).toHaveTextContent('종목 2개 · 적립식 1개 매달 8일 1,000만원 · 12회')
    expect(summary()).toHaveTextContent('· 이동평균 교차 1개 첫 매수 신호에 300만원 · 매년 처음 비중으로 리밸런싱(이동평균 교차 · 밴드 빼고)')
  })

  it('빈 줄이 있으면 그 줄 종목 칸으로 가며 요청하지 않고, 비율 합이 100 이 아니면 알림으로 막는다', async () => {
    renderPage()
    await selectMode('포트폴리오')
    await addRow()

    expect(screen.getByText('2번 줄 종목 비어 있음')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('고칠 칸 2개')
    rowAmount(1).focus()
    await runBacktest()
    expect(rowSymbol(2)).toHaveFocus()
    expect(backtestApi.runPortfolio).not.toHaveBeenCalled()

    await pickSymbol(rowSymbol(2), 'ms', /MSFT/)
    await userEvent.click(screen.getByRole('radio', { name: '비율' }))
    fireEvent.change(rowWeight(2), { target: { value: '30' } })
    expect(screen.getByTestId('portfolio-sum')).toHaveTextContent('합계 130%')
    expect(summary()).not.toHaveTextContent('고칠 칸')
    await runBacktest()

    expect(alertSpy).toHaveBeenCalledWith('비율은 줄마다 0보다 크게, 합이 100% 가 되게 넣어 주세요.')
    expect(backtestApi.runPortfolio).not.toHaveBeenCalled()
  })

  it('줄 금액 · 총액에 원 미만이 있으면 칸 아래 오류를 보이고 고칠 칸으로 센다', async () => {
    renderPage()
    await selectMode('포트폴리오')
    fireEvent.change(rowAmount(1), { target: { value: '1,000,000.5' } })

    expect(screen.getByText('1번 줄 금액 원 단위로')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('고칠 칸 1개')

    await userEvent.click(screen.getByRole('radio', { name: '비율' }))
    fireEvent.change(screen.getByRole('textbox', { name: '총액(원)' }), { target: { value: '10,000,000.5' } })
    expect(screen.getByText('원 단위로')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('고칠 칸 1개')
    await runBacktest()
    expect(backtestApi.runPortfolio).not.toHaveBeenCalled()
  })

  it('포트폴리오 줄 · 방식 · 리밸런싱도 직전 설정으로 되살리고 설정 초기화로 처음 줄 하나로 돌린다', async () => {
    renderPage()
    await selectMode('포트폴리오')
    await addRow()
    await pickSymbol(rowSymbol(2), 'ms', /MSFT/)
    fireEvent.change(rowAmount(2), { target: { value: '3,000,000' } })
    await userEvent.click(method('적립식'))
    await userEvent.click(rebalance('매년'))
    await runBacktest()

    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    await selectMode('포트폴리오')
    expect(rowSymbol(1)).toHaveValue('AAPL')
    expect(screen.queryByRole('combobox', { name: '2번 종목' })).toBeNull()
    expect(method('한 번에 사기')).toHaveAttribute('aria-checked', 'true')
    expect(rebalance('없음')).toHaveAttribute('aria-checked', 'true')
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))

    expect(modes().getByRole('radio', { name: '포트폴리오' })).toHaveAttribute('aria-checked', 'true')
    expect(rowSymbol(2)).toHaveValue('MSFT')
    expect(rowAmount(2)).toHaveValue('3,000,000')
    expect(method('적립식')).toHaveAttribute('aria-checked', 'true')
    expect(rebalance('매년')).toHaveAttribute('aria-checked', 'true')
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

  it('＋ 이동평균 교차로 칩을 더하면 기간 설정이 열리고, 바꾼 기간과 교차 규칙 요약 줄로 보낸다', async () => {
    renderPage()
    await selectMode('전략 비교')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 이동평균 교차' }))

    expect(strategyChips().getByRole('button', { name: '이동평균 교차' })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('이동평균 교차 기간')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('이동평균 교차: 20일 선이 60일 선을 넘으면 가진 돈 전부로 사고, 아래로 내려가면 전부 팜')
    fireEvent.change(screen.getByRole('textbox', { name: '짧은 기간(일)' }), { target: { value: '5' } })
    await runBacktest()

    const request = vi.mocked(backtestApi.compareStrategies).mock.calls[0][0]
    expect(request.investmentAmount).toBe(1000000)
    expect(request.strategies[2]).toEqual({ strategyType: 'MA_CROSS', name: 'MA_CROSS', shortWindow: 5, longWindow: 60 })
  })

  it('이동평균 교차 칩의 기간이 맞지 않으면 그 칩을 고칠 칩으로 두고 요청하지 않는다', async () => {
    renderPage()
    await selectMode('전략 비교')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 이동평균 교차' }))
    fireEvent.change(screen.getByRole('textbox', { name: '긴 기간(일)' }), { target: { value: '10' } })

    expect(
      strategyChips().getByRole('button', {
        name: '이동평균 교차 (고칠 것: 이동평균 기간은 2 ~ 250 사이 정수로, 짧은 기간을 긴 기간보다 짧게 입력해주세요.)',
      })
    ).toBeInTheDocument()
    expect(screen.getByText('긴 기간보다 짧게')).toBeInTheDocument()
    await runBacktest()

    expect(backtestApi.compareStrategies).not.toHaveBeenCalled()
  })

  it('이동평균 교차 칩이 있으면 배당 칸에 재투자 안 함을 안내하고 재투자는 막지 않는다', async () => {
    renderPage()
    await selectMode('전략 비교')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 이동평균 교차' }))
    await userEvent.click(detailToggle())

    expect(reinvestRadio()).not.toHaveAttribute('aria-disabled')
    expect(reinvestRadio()).toHaveAccessibleDescription('이동평균 교차는 재투자 안 함')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 조건부 매수' }))
    await userEvent.type(screen.getByRole('textbox', { name: '익절(평균 단가 대비 퍼센트)' }), '20')
    expect(reinvestRadio()).toHaveAccessibleDescription('익절 · 손절이 있는 조건부 매수 · 이동평균 교차는 재투자 안 함')
  })

  it('＋ 밴드로 칩을 더하면 밴드 설정이 열리고, 바꾼 칸과 밴드 규칙 요약 줄로 보내며 배당 칸에 재투자 안 함을 안내한다', async () => {
    renderPage()
    await selectMode('전략 비교')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 밴드' }))

    expect(strategyChips().getByRole('button', { name: '밴드' })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('밴드 설정')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('밴드: 200일선 +2.5% 위로 오르면 가진 돈 전부로 사고, -2.5% 아래로 내려가면 전부 팜')
    fireEvent.change(screen.getByRole('textbox', { name: '평균 기간(일)' }), { target: { value: '100' } })
    fireEvent.change(screen.getByRole('textbox', { name: '아래 비율(평균 대비 퍼센트)' }), { target: { value: '5' } })
    await userEvent.click(detailToggle())
    expect(reinvestRadio()).toHaveAccessibleDescription('밴드는 재투자 안 함')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 이동평균 교차' }))
    expect(reinvestRadio()).toHaveAccessibleDescription('이동평균 교차 · 밴드는 재투자 안 함')
    await runBacktest()

    const request = vi.mocked(backtestApi.compareStrategies).mock.calls[0][0]
    expect(request.strategies[2]).toEqual({
      strategyType: 'MA_BAND',
      name: 'MA_BAND',
      bandWindow: 100,
      upperRate: 0.025,
      lowerRate: 0.05,
    })
  })

  it('밴드 칩의 비율이 맞지 않으면 그 칩을 고칠 칩으로 두고 요청하지 않는다', async () => {
    renderPage()
    await selectMode('전략 비교')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 밴드' }))
    fireEvent.change(screen.getByRole('textbox', { name: '위 비율(평균 대비 퍼센트)' }), { target: { value: '100' } })

    expect(
      strategyChips().getByRole('button', {
        name: '밴드 (고칠 것: 밴드 기간은 2 ~ 250 사이 정수로, 위 · 아래 비율은 0 이상 100 미만으로 입력해주세요.)',
      })
    ).toBeInTheDocument()
    expect(screen.getByText('0 이상 100 미만')).toBeInTheDocument()
    await runBacktest()

    expect(backtestApi.compareStrategies).not.toHaveBeenCalled()
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

  it('칩이 하나뿐이면 실행을 눌렀을 때 첫 ＋ 단추로 간다', async () => {
    renderPage()
    await selectMode('전략 비교')
    await userEvent.click(strategyChips().getByRole('button', { name: '적립식 빼기' }))
    await runBacktest()

    expect(strategyChips().getByRole('button', { name: '＋ 적립식' })).toHaveFocus()
    expect(backtestApi.compareStrategies).not.toHaveBeenCalled()
  })

  it('단순 매수 칩을 더해도 열린 칩의 설정은 그대로 두고 단순 매수 칩의 빼기 단추로 간다', async () => {
    renderPage()
    await selectMode('전략 비교')
    await userEvent.click(strategyChips().getByRole('button', { name: '단순 매수 빼기' }))
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 단순 매수' }))

    expect(strategyChips().getByRole('button', { name: '적립식' })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('radiogroup', { name: '적립식 주기' })).toBeInTheDocument()
    await vi.waitFor(() => expect(strategyChips().getByRole('button', { name: '단순 매수 빼기' })).toHaveFocus())
  })
})

describe('Backtest 페이지 세부 설정', () => {
  it('세부 설정은 모드끼리 같이 쓰고 접힌 채 시작한다', async () => {
    renderPage()
    expect(detailToggle()).toHaveAttribute('aria-expanded', 'false')
    await userEvent.click(detailToggle())
    await userEvent.click(screen.getByRole('button', { name: '배당세 15%' }))
    await selectMode('종목 비교')
    await runBacktest()

    expect(vi.mocked(backtestApi.compareSymbols).mock.calls[0][0]).toMatchObject({ dividendTaxRate: 0 })
  })

  it('마지막 거래일에 팔기 · 양도세 끔은 요약에 보이고 요청에 실린다', async () => {
    renderPage()
    await userEvent.click(detailToggle())
    await userEvent.click(within(screen.getByRole('radiogroup', { name: '기간이 끝나면' })).getByRole('radio', { name: '마지막 거래일에 팔기' }))
    await userEvent.click(screen.getByRole('button', { name: '양도세 22%(연 250만원 공제)' }))

    expect(summary()).toHaveTextContent('시작일에 30만원 매수 · 마지막 거래일에 팔기 · 양도세 끔')
    await runBacktest()
    expect(vi.mocked(backtestApi.runSimulation).mock.calls[0][0]).toMatchObject({ capitalGainsTax: false, periodEnd: 'SELL_AT_END' })
    expect(await screen.findByText('결과: simple')).toBeInTheDocument()

    await selectMode('적립식')
    await userEvent.click(within(screen.getByRole('radiogroup', { name: '기간이 끝나면' })).getByRole('radio', { name: '끝까지 보유' }))
    await runBacktest()
    expect(vi.mocked(backtestApi.runDcaStrategy).mock.calls[0][0]).toMatchObject({ capitalGainsTax: false, periodEnd: 'HOLD' })
    expect(await screen.findByText('결과: dca')).toBeInTheDocument()
  })

  it('2014년 전이 들어가면 기간 칸에 기본 환율 안내, 단순은 직접 입력을 누르면 매수 환율 칸으로 간다', async () => {
    renderPage()
    const ago = screen.getByRole('textbox', { name: '오늘부터 몇' })
    await userEvent.clear(ago)
    await userEvent.type(ago, '13')

    expect(screen.getByText('2014년 이전은 환율 기록이 없어 그 기간은 기본 환율로 계산')).toBeInTheDocument()
    expect(summary()).not.toHaveTextContent('고칠 칸')
    await userEvent.click(screen.getByRole('button', { name: '직접 입력' }))

    await vi.waitFor(() => expect(screen.getByRole('textbox', { name: '매수 환율(원)' })).toHaveFocus())
    expect(within(screen.getByRole('radiogroup', { name: '환율' })).getByRole('radio', { name: '직접 입력' })).toHaveAttribute('aria-checked', 'true')
    fireEvent.change(screen.getByRole('textbox', { name: '매수 환율(원)' }), { target: { value: '1,130' } })
    expect(screen.queryByText('2014년 이전은 환율 기록이 없어 그 기간은 기본 환율로 계산')).toBeNull()
    await runBacktest()
    expect(vi.mocked(backtestApi.runSimulation).mock.calls[0][0]).toMatchObject({ purchaseDate: '2013-10-08', purchaseFxRate: 1130 })
    expect(vi.mocked(backtestApi.runSimulation).mock.calls[0][0]).not.toHaveProperty('currentFxRate')
  })

  it('기간 칸에 오류가 있으면 2014년 전 안내 대신 오류를 보인다', async () => {
    renderPage()
    const ago = screen.getByRole('textbox', { name: '오늘부터 몇' })
    await userEvent.clear(ago)
    await userEvent.type(ago, '13')
    await userEvent.clear(ago)

    expect(screen.getByText('몇 주 · 달 · 년 전인지 1 이상')).toBeInTheDocument()
    expect(screen.queryByText('2014년 이전은 환율 기록이 없어 그 기간은 기본 환율로 계산')).toBeNull()
    expect(summary()).toHaveTextContent('고칠 칸 1개')
  })

  it('적립식은 2014년 전 안내를 첫 매수일로 보고 직접 입력 단추가 없다', async () => {
    renderPage()
    await selectMode('적립식')
    const ago = screen.getByRole('textbox', { name: '오늘부터 몇' })
    await userEvent.clear(ago)
    await userEvent.type(ago, '13')

    expect(screen.getByText('2014년 이전은 환율 기록이 없어 그 기간은 기본 환율로 계산')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '직접 입력' })).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: '첫 매수일 2013. 10. 08.' }))
    const calendar = within(screen.getByRole('dialog'))
    await userEvent.selectOptions(calendar.getByLabelText('연도 선택'), '2014')
    await userEvent.selectOptions(calendar.getByLabelText('월 선택'), '0')
    await userEvent.click(calendar.getAllByRole('button').find((button) => button.textContent === '2')!)
    expect(screen.queryByText('2014년 이전은 환율 기록이 없어 그 기간은 기본 환율로 계산')).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: '시작일에 맞추기' }))
    expect(screen.getByText('2014년 이전은 환율 기록이 없어 그 기간은 기본 환율로 계산')).toBeInTheDocument()
  })

  it('접힌 세부 설정 안에 고칠 칸이 있으면 실행을 누를 때 열고 그 칸으로 간다', async () => {
    renderPage()
    await userEvent.click(detailToggle())
    const fee = screen.getByRole('textbox', { name: '수수료(퍼센트)' })
    await userEvent.clear(fee)
    await userEvent.type(fee, '100')
    await userEvent.click(detailToggle())

    expect(summary()).toHaveTextContent('고칠 칸 1개')
    expect(detailToggle()).toHaveTextContent('고칠 칸 있음')
    await runBacktest()

    await vi.waitFor(() => expect(screen.getByRole('textbox', { name: '수수료(퍼센트)' })).toHaveFocus())
    expect(backtestApi.runSimulation).not.toHaveBeenCalled()
  })
})

describe('Backtest 페이지 직전 설정 · 설정 초기화', () => {
  it('실행한 설정을 저장하고 직전 설정으로 모드 · 값을 되살리며, 되돌리기로 그 전 값으로 간다', async () => {
    renderPage()
    await selectMode('적립식')
    await userEvent.click(screen.getByRole('radio', { name: '매주' }))
    await runBacktest()

    await selectMode('단순')
    await userEvent.clear(amountInput())
    await userEvent.type(amountInput(), '700000')
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))

    expect(modes().getByRole('radio', { name: '적립식' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('radio', { name: '매주' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByText('직전 설정을 불러옴')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '되돌리기' }))

    expect(modes().getByRole('radio', { name: '단순' })).toHaveAttribute('aria-checked', 'true')
    expect(amountInput()).toHaveValue('700,000')
    expect(screen.queryByText('직전 설정을 불러옴')).toBeNull()
  })

  it('실행한 적이 없으면 알림만, 저장한 설정이 깨졌으면 쓸 수 없다고 알린다', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))
    expect(screen.getByText('아직 실행한 설정이 없음')).toBeInTheDocument()

    localStorage.setItem('backtest-last-settings', '{"v":0}')
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))
    expect(alertSpy).toHaveBeenCalledWith('저장한 설정을 쓸 수 없음')
  })

  it('설정 초기화는 모드 · 값 · 세부 설정을 처음으로 돌리고, 알림이 떠 있는 동안 다시 눌러도 되돌리기는 처음 값으로 간다', async () => {
    renderPage()
    await selectMode('조건부')
    await userEvent.click(screen.getByRole('radio', { name: '상승' }))
    await userEvent.click(detailToggle())
    await userEvent.click(screen.getByRole('button', { name: '배당세 15%' }))

    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    expect(modes().getByRole('radio', { name: '단순' })).toHaveAttribute('aria-checked', 'true')
    expect(summary()).not.toHaveTextContent('배당세 끔')
    expect(screen.getByRole('button', { name: '되돌리기' })).toHaveFocus()
    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    expect(screen.getAllByText('설정을 처음으로 돌림')).toHaveLength(1)
    await userEvent.click(screen.getByRole('button', { name: '되돌리기' }))

    expect(modes().getByRole('radio', { name: '조건부' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('radio', { name: '상승' })).toHaveAttribute('aria-checked', 'true')
    expect(summary()).toHaveTextContent('배당세 끔')
    expect(screen.getByRole('button', { name: '설정 초기화' })).toHaveFocus()
  })

  it('다시 연 페이지에서도 전략 비교 칩을 되살리고, 새 칩은 되살린 칩과 다른 칩으로 만든다', async () => {
    const first = renderPage()
    await selectMode('전략 비교')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 적립식' }))
    await runBacktest()
    first.unmount()

    renderPage()
    await selectMode('전략 비교')
    expect(strategyChips().queryByRole('button', { name: '적립식 2' })).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))
    expect(strategyChips().getByRole('button', { name: '적립식 2' })).toBeInTheDocument()
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 적립식' }))

    expect(strategyChips().getByRole('button', { name: '적립식 3' })).toHaveAttribute('aria-expanded', 'true')
    expect(strategyChips().getByRole('button', { name: '적립식 2' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('옛 이름 이동평균 칩이 든 직전 설정은 칩을 이동평균 교차 이름으로 되살리고 첫 칩은 종류 이름으로 요청한다', async () => {
    renderPage()
    await selectMode('전략 비교')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 이동평균 교차' }))
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 이동평균 교차' }))
    await runBacktest()
    // 옛 칩 이름(이동평균 · 이동평균 2)으로 저장한 직전 설정
    const saved = localStorage.getItem('backtest-last-settings') ?? ''
    localStorage.setItem('backtest-last-settings', saved.replaceAll('"name":"이동평균 교차', '"name":"이동평균'))

    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))
    expect(strategyChips().getByRole('button', { name: '이동평균 교차' })).toBeInTheDocument()
    expect(strategyChips().getByRole('button', { name: '이동평균 교차 2' })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('이동평균 교차 2 기간')).toBeInTheDocument()
    expect(summary()).toHaveTextContent('이동평균 교차 2: 20일 선이 60일 선을 넘으면')
    await userEvent.click(strategyChips().getByRole('button', { name: '＋ 이동평균 교차' }))
    expect(strategyChips().getByRole('button', { name: '이동평균 교차 3' })).toHaveAttribute('aria-expanded', 'true')

    vi.mocked(backtestApi.compareStrategies).mockClear()
    await runBacktest()
    const request = vi.mocked(backtestApi.compareStrategies).mock.calls[0][0]
    expect(request.strategies.map((strategy) => strategy.name)).toEqual(['SIMPLE', 'DCA', 'MA_CROSS', '이동평균 교차 2', '이동평균 교차 3'])
  })

  it('이동평균 교차 원금 · 기간도 직전 설정으로 되살리고 설정 초기화로 기본값으로 돌린다', async () => {
    renderPage()
    await selectMode('이동평균 교차')
    fireEvent.change(screen.getByRole('textbox', { name: '원금(원)' }), { target: { value: '2,000,000' } })
    fireEvent.change(screen.getByRole('textbox', { name: '짧은 기간(일)' }), { target: { value: '10' } })
    await runBacktest()

    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    await selectMode('이동평균 교차')
    expect(screen.getByRole('textbox', { name: '원금(원)' })).toHaveValue('1,000,000')
    expect(screen.getByRole('textbox', { name: '짧은 기간(일)' })).toHaveValue('20')
    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))

    expect(modes().getByRole('radio', { name: '이동평균 교차' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('textbox', { name: '원금(원)' })).toHaveValue('2,000,000')
    expect(screen.getByRole('textbox', { name: '짧은 기간(일)' })).toHaveValue('10')
    expect(screen.getByRole('textbox', { name: '긴 기간(일)' })).toHaveValue('60')
  })

  it('모드를 바꾸지 않는 되살리기는 결과를 두고, 모드가 바뀌면 결과를 지운다', async () => {
    renderPage()
    await runBacktest()
    expect(await screen.findByText('결과: simple')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '직전 설정' }))
    expect(screen.getByText('결과: simple')).toBeInTheDocument()
    await selectMode('적립식')
    await runBacktest()
    expect(await screen.findByText('결과: dca')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '설정 초기화' }))
    expect(screen.queryByText('결과: dca')).toBeNull()
  })
})
