import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import type { TradeStats } from '../../../services/api'
import { TradeStatsCard } from './TradeStatsCard'

const stats: TradeStats = {
  buyCount: 8,
  sellCount: 7,
  cycleCount: 7,
  openCycle: true,
  winCount: 4,
  lossCount: 3,
  winRatePercent: 57.14,
  averageWinPercent: 12.35,
  averageLossPercent: 4.1,
  profitLossRatio: 3.01,
  averageHoldingDays: 45.3,
  cyclesPerYear: 1.85,
  trailingStopCount: 0,
  partialTakeProfitCount: 0,
}

const cell = (label: string) => screen.getByText(label).parentElement as HTMLElement

describe('TradeStatsCard', () => {
  it('제목 줄 오른쪽에 끝난 사이클 수와 진행 중을 보인다', () => {
    const { rerender } = render(<TradeStatsCard stats={stats} />)
    expect(screen.getByRole('heading', { name: '매매 통계' })).toBeInTheDocument()
    expect(screen.getByText('사이클 7번 · 진행 중')).toBeInTheDocument()

    rerender(<TradeStatsCard stats={{ ...stats, openCycle: false }} />)
    expect(screen.getByText('사이클 7번')).toBeInTheDocument()
  })

  it('승률 · 평균 익절 · 손절 · 손익비 · 연평균 거래와 평균 보유 기간을 칸마다 보인다', () => {
    render(<TradeStatsCard stats={stats} />)

    expect(cell('승률')).toHaveTextContent('57.14%4승 3패')
    expect(within(cell('평균 익절 · 손절')).getByText('+12.35%')).toHaveClass('text-gain')
    expect(within(cell('평균 익절 · 손절')).getByText('-4.10%')).toHaveClass('text-loss')
    expect(cell('손익비')).toHaveTextContent('3.01')
    expect(cell('연평균 거래')).toHaveTextContent('1.9번평균 보유 45.3일')
  })

  it('휴대폰은 두 칸 두 줄, 넓은 화면은 네 칸 한 줄로 둔다', () => {
    render(<TradeStatsCard stats={stats} />)

    expect(cell('승률').parentElement).toHaveClass('grid-cols-2', 'sm:grid-cols-4')
  })

  it('없는 값은 - 로 보인다', () => {
    render(
      <TradeStatsCard
        stats={{
          ...stats,
          lossCount: 0,
          winRatePercent: 100,
          averageLossPercent: null,
          profitLossRatio: null,
          cyclesPerYear: null,
        }}
      />,
    )

    expect(within(cell('평균 익절 · 손절')).getByText('-')).toHaveClass('text-tx-3')
    expect(cell('손익비')).toHaveTextContent('-')
    expect(cell('연평균 거래')).toHaveTextContent('연평균 거래-평균 보유 45.3일')
  })

  it('끝난 사이클이 없으면 칸 대신 안내 한 줄을 보인다', () => {
    render(
      <TradeStatsCard
        stats={{
          ...stats,
          cycleCount: 0,
          winCount: 0,
          lossCount: 0,
          winRatePercent: null,
          averageWinPercent: null,
          averageLossPercent: null,
          profitLossRatio: null,
          averageHoldingDays: null,
        }}
      />,
    )

    expect(screen.getByText('사이클 0번 · 진행 중')).toBeInTheDocument()
    expect(screen.getByText('기간 안에 전부 판 사이클이 없습니다')).toBeInTheDocument()
    expect(screen.queryByText('승률')).toBeNull()
  })

  it('트레일링 스탑 · 부분 익절로 판 거래가 있으면 카드 아래 한 줄에 횟수를 보인다', () => {
    const { rerender } = render(<TradeStatsCard stats={stats} />)
    expect(screen.queryByText(/트레일링 스탑|부분 익절/)).toBeNull()

    rerender(<TradeStatsCard stats={{ ...stats, trailingStopCount: 2, partialTakeProfitCount: 1 }} />)
    expect(screen.getByText('트레일링 스탑 2번 · 부분 익절 1번')).toBeInTheDocument()

    rerender(<TradeStatsCard stats={{ ...stats, partialTakeProfitCount: 3 }} />)
    expect(screen.getByText('부분 익절 3번')).toBeInTheDocument()
  })
})
