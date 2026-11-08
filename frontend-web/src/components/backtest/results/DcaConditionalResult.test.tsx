import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DcaConditionalResult } from './DcaConditionalResult'

// 차트는 기간만 보임(결과에 시작일이 없으면 넘겨받은 시작일)
vi.mock('@components/charts/backtest/AccumulationChart', () => ({
  AccumulationChart: ({ startDate, endDate }: { startDate: string; endDate?: string }) => (
    <div>
      <div>차트 시작 {startDate}</div>
      <div>차트 끝 {endDate ?? '오늘'}</div>
    </div>
  ),
}))

const result = {
  symbol: 'AAPL',
  startDate: '2023-01-01',
  endDate: '2024-01-01',
  totalInvested: 1200000,
  currentValueKrw: 1350000,
  totalAssetKrw: 1350000,
  remainingCashKrw: 0,
  totalReturnKrw: 150000,
  totalReturnPercent: 12.5,
  fxReturn: 20000,
  fxReturnPercent: 1.5,
  totalShares: 8,
  averagePrice: 150,
  averageFxRate: 1320,
  currentPrice: 170,
  currentFxRate: 1350,
  totalTransactions: 12,
  transactions: [{ date: '2023-01-15', shares: 1, price: 140, amount: 100000, fxRate: 1300 }],
}

const props = {
  symbol: 'AAPL',
  startDate: '2022-06-01',
}

const open = (title: string) => userEvent.click(screen.getByText(title))
const priceTab = () => userEvent.click(screen.getByRole('tab', { name: '주가 추이' }))

describe('DcaConditionalResult', () => {
  it('자산 추이 · 주가 추이를 한 카드의 탭으로 묶어 자산 추이를 먼저 보이고, 주가 차트는 탭을 고를 때 그린다', async () => {
    render(<DcaConditionalResult result={result} {...props} />)

    expect(screen.getByRole('tab', { name: '자산 추이' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.queryByRole('tab', { name: '매수일별 수익률' })).toBeNull()
    expect(screen.queryByText('주가 · 수익률 추이')).toBeNull()
    expect(screen.queryByText(/차트 시작/)).toBeNull()

    await priceTab()

    expect(screen.getByRole('tab', { name: '주가 추이' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByText('차트 시작 2023-01-01')).toBeVisible()
  })

  it('한 번 연 주가 추이 탭은 숨겨 둔 채 두고, 화살표로 탭을 옮긴다', async () => {
    render(<DcaConditionalResult result={result} {...props} />)
    await priceTab()
    await userEvent.click(screen.getByRole('tab', { name: '자산 추이' }))

    expect(screen.getByText('차트 시작 2023-01-01')).not.toBeVisible()

    await userEvent.keyboard('{ArrowRight}')

    expect(screen.getByRole('tab', { name: '주가 추이' })).toHaveFocus()
    expect(screen.getByText('차트 시작 2023-01-01')).toBeVisible()
  })

  it('매수가 없어도 주가 추이 탭에서 결과 기간의 주가를 보인다', async () => {
    render(<DcaConditionalResult result={{ ...result, transactions: [] }} {...props} />)

    await priceTab()

    expect(screen.getByText('차트 시작 2023-01-01')).toBeVisible()
  })

  it('매수 0회면 조건이 맞아 산 날이 없다고 한 줄로 알린다', () => {
    render(<DcaConditionalResult result={{ ...result, transactions: [], totalInvested: 0 }} {...props} />)

    expect(screen.getByText('매수 0회: 기간 안에 조건이 맞아 산 날이 없습니다.')).toBeInTheDocument()
  })

  it('예산 부족 구간을 처음 날짜 · 회차 수 한 줄로 알린다', () => {
    const skippedSpan = { firstDate: '2023-05-03', rounds: 4, reason: 'OVER_BUDGET' as const }
    render(<DcaConditionalResult result={{ ...result, skippedSpan }} {...props} />)

    expect(screen.getByText('예산이 모자라 2023-05-03부터 4회를 사지 않았습니다.')).toBeInTheDocument()
    expect(screen.queryByText(/^매수 0회:/)).toBeNull()
  })

  it('매수 0회가 예산 부족 때문이면 그 구간을 같은 줄에 보인다', () => {
    const skippedSpan = { firstDate: '2023-01-15', rounds: 3, reason: 'OVER_BUDGET' as const }
    render(<DcaConditionalResult result={{ ...result, transactions: [], skippedSpan }} {...props} />)

    expect(screen.getByText('매수 0회: 예산이 모자라 2023-01-15부터 3회를 사지 않았습니다.')).toBeInTheDocument()
  })

  it('배당 재투자만 있으면 매수 0회로 본다', () => {
    const transactions = [{ date: '2023-05-12', shares: 0.01, price: 170, amount: 0, fxRate: 1300, trigger: '배당 재투자' }]
    render(<DcaConditionalResult result={{ ...result, transactions }} {...props} />)

    expect(screen.getByText(/^매수 0회:/)).toBeInTheDocument()
  })

  it('매수 내역은 맨 아래 접는 칸이고 제목 줄에 매수 · 미매수 횟수를 보인다', () => {
    render(<DcaConditionalResult result={{ ...result, skippedRounds: [{ date: '2022-12-15', reason: 'NO_PRICE' }] }} {...props} />)

    const detail = screen.getByText('상세')
    const history = screen.getByText('매수 내역')
    expect(detail.compareDocumentPosition(history) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(history.closest('details')).not.toHaveAttribute('open')
    expect(screen.getByText('· 매수 1회 · 미매수 1회')).toBeInTheDocument()
  })

  it('매도가 있으면 매매 내역 · 매도 횟수 · 실현 손익 · 남은 현금을 보이고, 전량 매도면 평균 매수가는 비운다', async () => {
    const transactions = [
      ...result.transactions,
      { date: '2023-06-01', shares: -1, price: 160, amount: 0, fxRate: 1300, trigger: '14.3%익절', proceeds: 160, realizedProfit: 20 },
    ]
    render(
      <DcaConditionalResult
        result={{ ...result, transactions, totalShares: 0, averagePrice: null, cashBalance: 160, realizedProfit: 20 }}
        {...props}
      />
    )

    expect(screen.getByText('매매 내역')).toBeInTheDocument()
    expect(screen.getByText('· 매수 1회 · 매도 1회 · 미매수 0회')).toBeInTheDocument()
    await open('상세')
    expect(screen.getByText('매도 횟수').nextElementSibling).toHaveTextContent('1회')
    expect(screen.getByText('실현 손익').nextElementSibling).toHaveTextContent('+$20.00')
    expect(screen.getByText('남은 현금').nextElementSibling).toHaveTextContent('$160.00')
    expect(screen.getByText('평균 매수가').nextElementSibling).toHaveTextContent('-')
  })

  it('매도가 없으면 매도 칸을 보이지 않는다', async () => {
    render(<DcaConditionalResult result={result} {...props} />)

    await open('상세')
    expect(screen.queryByText('매도 횟수')).not.toBeInTheDocument()
    expect(screen.queryByText('남은 현금')).not.toBeInTheDocument()
  })

  it('배당 재투자는 매수 횟수에 넣지 않는다', async () => {
    const transactions = [
      ...result.transactions,
      { date: '2023-05-12', shares: 0.01, price: 170, amount: 0, fxRate: 1300, trigger: '배당 재투자' },
    ]
    render(<DcaConditionalResult result={{ ...result, transactions }} {...props} />)

    expect(screen.getByText('· 매수 1회 · 미매수 0회')).toBeInTheDocument()
    await open('상세')
    expect(screen.getByText('매수 횟수').nextElementSibling).toHaveTextContent('1회')
  })

  it('결과의 시작일부터 평가일까지 차트를 그린다', async () => {
    render(<DcaConditionalResult result={result} {...props} />)
    await priceTab()

    expect(screen.getByText('차트 시작 2023-01-01')).toBeInTheDocument()
    expect(screen.getByText('차트 끝 2024-01-01')).toBeInTheDocument()
  })

  it('결과에 시작일이 없으면 넘겨받은 시작일부터 그린다', async () => {
    const { startDate: _omit, ...withoutStart } = result
    void _omit
    render(<DcaConditionalResult result={withoutStart} {...props} />)
    await priceTab()

    expect(screen.getByText('차트 시작 2022-06-01')).toBeInTheDocument()
  })

  it('평가일 기준 라벨을 쓴다', async () => {
    render(<DcaConditionalResult result={result} {...props} />)
    await open('상세')

    expect(screen.getAllByText('평가 가치').length).toBeGreaterThan(0)
    expect(screen.getByText('평가일 가격')).toBeInTheDocument()
    expect(screen.getByText('평가일 환율')).toBeInTheDocument()
    expect(screen.queryByText('현재 가격')).toBeNull()
  })

  it('보유 주식수 · 달러 주식 평가액은 소수 둘째 자리까지 보여준다', async () => {
    render(<DcaConditionalResult result={{ ...result, totalShares: 8.123456, currentValue: 1234.5678 }} {...props} />)
    await open('상세')

    expect(screen.getByText('8.12 주')).toBeInTheDocument()
    expect(screen.getByText('$1,234.57')).toBeInTheDocument()
  })

  it('위 카드 평가 가치는 배당 현금까지 넣은 총자산, 투자 정보는 주식 평가액만 보여준다', async () => {
    render(<DcaConditionalResult result={{ ...result, currentValueKrw: 1300000 }} {...props} />)
    await open('상세')

    expect(screen.getByText('주식: ₩1,300,000, 현금: ₩50,000')).toBeInTheDocument()
    expect(screen.getByText('주식 평가액').nextElementSibling).toHaveTextContent('₩1,300,000')
  })

  it('투자일이 휴장일이라 다른 날 산 번수를 알린다', () => {
    const transactions = [
      { date: '2023-01-15', actualDate: '2023-01-17', shares: 1, price: 140, amount: 100000, fxRate: 1300 },
      { date: '2023-02-15', actualDate: '2023-02-15', shares: 1, price: 145, amount: 100000, fxRate: 1300 },
    ]
    render(<DcaConditionalResult result={{ ...result, transactions }} {...props} />)

    expect(screen.getByText('투자일이 휴장일이면 다음 거래일 종가로 삽니다(이번 결과 1번).')).toBeInTheDocument()
  })

  it('환율은 소수 둘째 자리까지, 하락은 부호를 통화 앞에 둔다', async () => {
    render(<DcaConditionalResult result={{ ...result, currentFxRate: 1325.5, fxReturn: -30.02 }} {...props} />)
    await open('상세')

    expect(screen.getByText('₩1,325.50')).toBeInTheDocument()
    expect(screen.getByText('-₩30.02')).toBeInTheDocument()
  })
})
