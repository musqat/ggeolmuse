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
})
