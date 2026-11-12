import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BacktestHistoryPanel } from './BacktestHistoryPanel'

function renderPanel(overrides = {}) {
  render(
    <BacktestHistoryPanel
      historyData={[]}
      historyLoading={false}
      isAuthenticated={false}
      historyPage={0}
      setHistoryPage={vi.fn()}
      historyTotalPages={0}
      {...overrides}
    />
  )
}

describe('BacktestHistoryPanel', () => {
  it('불러오는 중이면 로딩 문구를 보여준다', () => {
    renderPanel({ historyLoading: true })

    expect(screen.getByText('로딩 중...')).toBeInTheDocument()
  })

  it('내역이 없으면 비어 있다고 알려준다', () => {
    renderPanel()

    expect(screen.getByText('아직 백테스트 히스토리가 없습니다.')).toBeInTheDocument()
  })

  it('로그인 안 했으면 이 기기에 저장된다고 안내한다', () => {
    renderPanel()

    expect(screen.getByText(/이 기기에 저장됩니다/)).toBeInTheDocument()
  })

  it('단순 실행은 단순으로, 기간 끝은 평가일로 보여준다', () => {
    const simple = {
      backtestId: '1',
      userId: 'u',
      backtestType: 'COMPARISON' as const,
      requestParams: JSON.stringify({ symbol: 'AAPL', purchaseDate: '2023-01-01', saleDate: '2026-10-23', investmentAmount: 300000 }),
      fxRateMode: 'auto' as const,
      createdAt: '2026-10-23T09:00:00',
    }
    renderPanel({ historyData: [simple], historyTotalPages: 1 })

    expect(screen.getByText('단순')).toBeInTheDocument()
    expect(screen.getByText('(2023-01-01 ~ 2026-10-23)')).toBeInTheDocument()
  })

  it('조건부 기록은 옛 칸(투자 모드 · 총 예산)이든 새 칸(회당 금액 · 예산 · 최대 횟수)이든 조건부 매수로 보인다', () => {
    const record = (backtestId: string, params: object) => ({
      backtestId,
      userId: 'u',
      backtestType: 'STRATEGY_SIMULATION' as const,
      requestParams: JSON.stringify({ symbol: 'AAPL', startDate: '2023-01-01', endDate: '2026-10-23', ...params }),
      fxRateMode: 'auto' as const,
      createdAt: '2026-10-23T09:00:00',
    })
    const old = record('1', { investmentMode: 'TOTAL_BUDGET', totalInvestment: 1000000, amountPerPurchase: 100000, dropPercentage: 0.05 })
    const current = record('2', { amountPerPurchase: 100000, maxPurchases: 5, dropPercentage: 0.05 })
    renderPanel({ historyData: [old, current], historyTotalPages: 1 })

    expect(screen.getAllByText('조건부 매수')).toHaveLength(2)
  })

  it('적립식 기록은 옛 칸(월 투자금 · 투자일)이든 새 칸(회당 금액 · 주기 · 첫 매수일)이든 적립식으로 보인다', () => {
    const record = (backtestId: string, params: object) => ({
      backtestId,
      userId: 'u',
      backtestType: 'STRATEGY_SIMULATION' as const,
      requestParams: JSON.stringify({ symbol: 'AAPL', startDate: '2023-01-01', endDate: '2026-10-23', ...params }),
      fxRateMode: 'auto' as const,
      createdAt: '2026-10-23T09:00:00',
    })
    const old = record('1', { monthlyAmount: 100000, purchaseDay: 15, investmentInterval: 1 })
    const weekly = record('2', { amountPerPurchase: 100000, frequency: 'WEEKLY', firstPurchaseDate: '2023-01-04' })
    const monthly = record('3', { amountPerPurchase: 100000, frequency: 'MONTHLY', purchaseDay: 31, totalInvestmentLimit: 1000000 })
    renderPanel({ historyData: [old, weekly, monthly], historyTotalPages: 1 })

    expect(screen.getAllByText('적립식')).toHaveLength(3)
    expect(screen.queryByText('조건부 매수')).toBeNull()
  })

  it('이동평균 기록은 짧은 · 긴 기간 칸으로 이동평균으로 보인다', () => {
    const maCross = {
      backtestId: '1',
      userId: 'u',
      backtestType: 'STRATEGY_SIMULATION' as const,
      requestParams: JSON.stringify({
        symbol: 'AAPL',
        startDate: '2023-01-01',
        endDate: '2026-10-23',
        investmentAmount: 1000000,
        shortWindow: 20,
        longWindow: 60,
      }),
      fxRateMode: 'auto' as const,
      createdAt: '2026-10-23T09:00:00',
    }
    renderPanel({ historyData: [maCross], historyTotalPages: 1 })

    expect(screen.getByText('이동평균')).toBeInTheDocument()
    expect(screen.queryByText('단순')).toBeNull()
  })

  it('밴드 기록은 평균 기간 칸으로 이동평균보다 먼저 이동평균 밴드로 보인다', () => {
    const band = (id: string, params: object) => ({
      backtestId: id,
      userId: 'u',
      backtestType: 'STRATEGY_SIMULATION' as const,
      requestParams: JSON.stringify({ symbol: 'SPY', startDate: '2015-01-01', endDate: '2024-12-31', investmentAmount: 1000000, ...params }),
      fxRateMode: 'auto' as const,
      createdAt: '2026-11-12T09:00:00',
    })
    renderPanel({
      historyData: [band('1', { bandWindow: 200, upperRate: 0.025, lowerRate: 0.025 }), band('2', { bandWindow: 100, shortWindow: 20 })],
      historyTotalPages: 1,
    })

    expect(screen.getAllByText('이동평균 밴드')).toHaveLength(2)
    expect(screen.queryByText('이동평균')).toBeNull()
  })

  it('포트폴리오 기록은 종목 줄로 다른 전략보다 먼저 포트폴리오로 보이고, 종목 칸에 줄 종목을 잇는다', () => {
    const portfolio = {
      backtestId: '1',
      userId: 'u',
      backtestType: 'STRATEGY_SIMULATION' as const,
      requestParams: JSON.stringify({
        startDate: '2020-01-01',
        endDate: '2024-12-31',
        assets: [{ symbol: 'SPY', amount: 6000000 }, { symbol: 'QQQ', amount: 4000000 }, { amount: 1 }],
        method: 'DCA',
        frequency: 'MONTHLY',
      }),
      fxRateMode: 'auto' as const,
      createdAt: '2026-11-13T09:00:00',
    }
    renderPanel({ historyData: [portfolio], historyTotalPages: 1 })

    expect(screen.getByText('포트폴리오')).toBeInTheDocument()
    expect(screen.queryByText('적립식')).toBeNull()
    expect(screen.getByText('SPY,QQQ')).toBeInTheDocument()
    expect(screen.getByText('(2020-01-01 ~ 2024-12-31)')).toBeInTheDocument()
  })
})
