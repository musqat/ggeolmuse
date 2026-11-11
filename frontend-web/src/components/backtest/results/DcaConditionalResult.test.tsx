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

// 이동평균 차트는 받은 종목 · 점 수 · 기간만 보임
vi.mock('@components/charts/backtest/SignalChart', () => ({
  SignalChart: ({ chart, windows }: { chart?: { symbol: string; points: unknown[] } | null; windows?: { short: number; long: number } }) => (
    <div>
      이동평균 차트 {chart ? `${chart.symbol} ${chart.points.length}일` : '없음'} · {windows ? `${windows.short} · ${windows.long}` : '기간 모름'}
    </div>
  ),
}))

// 밴드 차트는 받은 종목 · 점 수 · 거래 수 · 밴드 설정만 보임
vi.mock('@components/charts/backtest/BandChart', () => ({
  BandChart: ({
    chart,
    transactions,
    band,
  }: {
    chart?: { symbol: string; points: unknown[] } | null;
    transactions: unknown[];
    band?: { window: number; upperRate: number; lowerRate: number };
  }) => (
    <div>
      밴드 차트 {chart ? `${chart.symbol} ${chart.points.length}일` : '없음'} · 거래 {transactions.length}건 ·{' '}
      {band ? `${band.window}일 ${band.upperRate} · ${band.lowerRate}` : '설정 모름'}
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
  kind: 'conditional' as const,
  symbol: 'AAPL',
  startDate: '2022-06-01',
}

const open = (title: string) => userEvent.click(screen.getByText(title))
const card = (label: string) => screen.getByText(label).parentElement as HTMLElement
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

  it('적립식 매수 0회는 조건 대신 기간 안에 산 회차가 없다고 알린다', () => {
    render(<DcaConditionalResult result={{ ...result, transactions: [], totalInvested: 0 }} {...props} kind="dca" />)

    expect(screen.getByText('매수 0회: 기간 안에 산 회차가 없습니다.')).toBeInTheDocument()
    expect(screen.queryByText(/조건이 맞아/)).toBeNull()
  })

  it('이동평균 결과는 주가 추이 앞에 이동평균 탭을 두고, 고르면 결과의 이동평균 값과 실행한 기간으로 그린다', async () => {
    const signalChart = {
      symbol: 'AAPL',
      points: [
        { date: '2023-01-03', close: 125.07, shortAverage: 124.1, longAverage: 128.3 },
        { date: '2023-01-04', close: 126.36, shortAverage: 124.8, longAverage: 128.1 },
      ],
      crosses: [],
    }
    render(
      <DcaConditionalResult result={{ ...result, signalChart }} {...props} kind="ma-cross" windows={{ short: 5, long: 120 }} />
    )

    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['자산 추이', '이동평균', '주가 추이'])
    expect(screen.queryByText(/이동평균 차트/)).toBeNull()

    await userEvent.click(screen.getByRole('tab', { name: '이동평균' }))

    expect(screen.getByText('이동평균 차트 AAPL 2일 · 5 · 120')).toBeVisible()
  })

  it('적립식 · 조건부 결과에는 이동평균 탭이 없다', () => {
    const { rerender } = render(<DcaConditionalResult result={result} {...props} />)

    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['자산 추이', '주가 추이'])

    rerender(<DcaConditionalResult result={result} {...props} kind="dca" />)
    expect(screen.queryByRole('tab', { name: '이동평균' })).toBeNull()
  })

  it('밴드 결과는 주가 추이 앞에 밴드 탭을 두고, 고르면 결과의 밴드 값 · 거래와 실행한 밴드 설정으로 그린다', async () => {
    const signalChart = {
      symbol: 'SPY',
      points: [
        { date: '2023-01-03', close: 380.8, average: 390.2, upperBand: 399.96, lowerBand: 380.45 },
        { date: '2023-01-04', close: 383.8, average: 390.1, upperBand: 399.85, lowerBand: 380.35 },
      ],
      crosses: [],
    }
    render(
      <DcaConditionalResult
        result={{ ...result, symbol: 'SPY', signalChart }}
        {...props}
        kind="ma-band"
        band={{ window: 200, upperRate: 0.025, lowerRate: 0.025 }}
      />
    )

    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['자산 추이', '밴드', '주가 추이'])
    expect(screen.queryByText(/사고파는 것은/)).toBeNull()

    await userEvent.click(screen.getByRole('tab', { name: '밴드' }))

    expect(screen.getByText('밴드 차트 SPY 2일 · 거래 1건 · 200일 0.025 · 0.025')).toBeVisible()
  })

  it('신호 종목을 따로 본 결과는 위에 신호 · 사고판 종목을 한 줄로 알리고 이동평균 · 밴드 탭 이름에 신호 종목을 적는다', () => {
    const points = [
      { date: '2023-01-03', close: 3824.14, average: 3900.2 },
      { date: '2023-01-04', close: 3852.97, average: 3901.1 },
    ]
    const { rerender } = render(
      <DcaConditionalResult
        result={{ ...result, symbol: 'SPY', signalChart: { symbol: '^GSPC', points, crosses: [] } }}
        {...props}
        kind="ma-cross"
      />
    )

    expect(screen.getByRole('note')).toHaveTextContent('신호는 S&P 500(^GSPC), 사고파는 것은 SPY')
    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['자산 추이', '이동평균 · ^GSPC', '주가 추이'])

    rerender(
      <DcaConditionalResult
        result={{ ...result, symbol: 'TQQQ', signalChart: { symbol: 'QQQ', points, crosses: [] } }}
        {...props}
        kind="ma-band"
      />
    )
    expect(screen.getByRole('note')).toHaveTextContent('신호는 QQQ, 사고파는 것은 TQQQ')
    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['자산 추이', '밴드 · QQQ', '주가 추이'])
  })

  it('밴드 매수 0회는 밴드 위로 올라선 날이 없다고 알리고, 수익률 기준은 원금, 미매수가 없으면 세지 않는다', () => {
    const { rerender } = render(
      <DcaConditionalResult result={{ ...result, transactions: [], totalInvested: 0 }} {...props} kind="ma-band" />
    )

    expect(screen.getByText('매수 0회: 기간 안에 밴드 위로 올라선 날이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: '이동평균' })).toBeNull()

    const transactions = [
      { date: '2023-03-01', shares: 6, price: 150, amount: 1000000, fxRate: 1300, trigger: '밴드 위 매수' },
      { date: '2023-08-01', shares: -6, price: 170, amount: 0, fxRate: 1300, trigger: '밴드 아래 매도', proceeds: 1020, realizedProfit: 120 },
    ]
    rerender(<DcaConditionalResult result={{ ...result, transactions }} {...props} kind="ma-band" />)
    expect(screen.getByText('· 매수 1회 · 매도 1회')).toBeInTheDocument()
    expect(screen.getAllByText(/원금 기준/).length).toBeGreaterThan(0)
  })

  it('이동평균 매수 0회는 짧은 선이 긴 선을 위로 넘은 날이 없다고 알린다', () => {
    render(<DcaConditionalResult result={{ ...result, transactions: [], totalInvested: 0 }} {...props} kind="ma-cross" />)

    expect(screen.getByText('매수 0회: 기간 안에 짧은 선이 긴 선을 아래에서 위로 넘은 날이 없습니다.')).toBeInTheDocument()
  })

  it('이동평균 매매 내역 제목 줄은 미매수 회차가 없으면 미매수를 세지 않는다', () => {
    const transactions = [
      { date: '2023-03-01', shares: 6, price: 150, amount: 1000000, fxRate: 1300, trigger: '골든 크로스' },
      { date: '2023-08-01', shares: -6, price: 170, amount: 0, fxRate: 1300, trigger: '데드 크로스', proceeds: 1020, realizedProfit: 120 },
    ]
    const { rerender } = render(<DcaConditionalResult result={{ ...result, transactions }} {...props} kind="ma-cross" />)

    expect(screen.getByText('매매 내역')).toBeInTheDocument()
    expect(screen.getByText('· 매수 1회 · 매도 1회')).toBeInTheDocument()

    rerender(
      <DcaConditionalResult
        result={{ ...result, transactions, skippedRounds: [{ date: '2023-10-02', reason: 'NO_PRICE' }] }}
        {...props}
        kind="ma-cross"
      />
    )
    expect(screen.getByText('· 매수 1회 · 매도 1회 · 미매수 1회')).toBeInTheDocument()
  })

  it('양도세 납부 줄은 매매 내역 제목 줄과 상세의 매수 · 매도 횟수에 세지 않는다', async () => {
    const transactions = [
      { date: '2023-03-01', shares: 6, price: 150, amount: 1000000, fxRate: 1300, trigger: '골든 크로스' },
      { date: '2023-08-01', shares: -6, price: 170, amount: 0, fxRate: 1300, trigger: '데드 크로스', proceeds: 1020, realizedProfit: 120 },
      { date: '2024-05-01', actualDate: '2024-05-01', shares: 0, price: 0, amount: 0, fee: 0, fxRate: 1350, trigger: '양도세 납부', taxPaid: 52.3 },
    ]
    render(<DcaConditionalResult result={{ ...result, transactions }} {...props} kind="ma-cross" />)

    expect(screen.getByText('· 매수 1회 · 매도 1회')).toBeInTheDocument()
    await open('상세')
    expect(screen.getByText('매수 횟수').nextSibling).toHaveTextContent('1회')
    expect(screen.getByText('매도 횟수').nextSibling).toHaveTextContent('1회')
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

  it('매수 내역 제목 줄의 미매수 횟수에 예산 부족 회차를 더한다', () => {
    const skippedSpan = { firstDate: '2023-05-03', rounds: 4, reason: 'OVER_BUDGET' as const }
    render(
      <DcaConditionalResult
        result={{ ...result, skippedRounds: [{ date: '2022-12-15', reason: 'NO_PRICE' }], skippedSpan }}
        {...props}
      />
    )

    expect(screen.getByText('· 매수 1회 · 미매수 5회')).toBeInTheDocument()
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

  it('두 수익률 이름에 누적 매수액 · 시간가중 기준을 적고, 상세의 투자금도 누적 매수액으로 부른다', async () => {
    render(<DcaConditionalResult result={result} {...props} />)

    expect(card('총 수익률')).toHaveTextContent('총 수익률 · 누적 매수액 기준')
    expect(card('총 수익률')).toHaveTextContent('누적 매수액 ₩1,200,000 대비 +₩150,000')
    expect(card('연평균 수익률')).toHaveTextContent('연평균 수익률 · 시간가중 기준')
    await open('상세')
    expect(screen.getByText('누적 매수액', { selector: 'span' }).nextElementSibling).toHaveTextContent('₩1,200,000')
  })

  it('이동평균은 수익률 기준 · 상세 · 세후 수익률의 투자금을 원금으로 부른다', async () => {
    const tax = {
      years: [],
      totalTaxKrw: 0,
      paidTaxKrw: 0,
      unpaidTaxKrw: 0,
      cashKrw: 0,
      stockKrw: 1350000,
      afterTaxValueKrw: 1350000,
      afterTaxReturnPercent: 12.5,
    }
    render(<DcaConditionalResult result={{ ...result, capitalGainsTax: tax }} {...props} kind="ma-cross" />)

    expect(card('총 수익률')).toHaveTextContent('총 수익률 · 원금 기준')
    expect(card('총 수익률')).toHaveTextContent('원금 ₩1,200,000 대비 +₩150,000')
    expect(screen.queryByText(/누적 매수액/)).toBeNull()
    await open('상세')
    expect(screen.getByText('원금', { selector: 'span' }).nextElementSibling).toHaveTextContent('₩1,200,000')
    await open('예상 양도세')
    expect(screen.getByText('세후 수익률 · 원금 기준')).toBeInTheDocument()
  })

  it('예산이 남으면 쓰지 않은 예산 카드를 수익률 · 평가 가치와 따로 보인다', () => {
    render(<DcaConditionalResult result={result} budget={1500000} {...props} />)

    expect(card('쓰지 않은 예산')).toHaveTextContent('₩300,000')
    expect(card('쓰지 않은 예산')).toHaveTextContent('예산 ₩1,500,000 가운데 · 수익률 · 평가 가치에 넣지 않음')
    expect(card('평가 가치')).toHaveTextContent('₩1,350,000')
  })

  it('예산이 없거나 다 썼으면 쓰지 않은 예산 카드는 없다', () => {
    const { rerender } = render(<DcaConditionalResult result={result} {...props} />)
    expect(screen.queryByText('쓰지 않은 예산')).toBeNull()

    rerender(<DcaConditionalResult result={result} budget={1200000} {...props} />)
    expect(screen.queryByText('쓰지 않은 예산')).toBeNull()
  })

  it('매수 0회면 예산 전체가 쓰지 않은 예산', () => {
    render(<DcaConditionalResult result={{ ...result, transactions: [], totalInvested: 0 }} budget={1000000} {...props} />)

    expect(card('쓰지 않은 예산')).toHaveTextContent('₩1,000,000')
  })

  it('양도세를 요청했으면 누적 매수액 기준 세후 수익률과 연도별 양도세를 상세 앞 칸에 보이고, 요청하지 않았으면 칸이 없다', async () => {
    const tax = {
      years: [{ year: 2023, realizedGainKrw: 3000000, deductionKrw: 2500000, taxKrw: 110000, dueDate: null, paidKrw: 0 }],
      totalTaxKrw: 110000,
      paidTaxKrw: 0,
      unpaidTaxKrw: 110000,
      cashKrw: 0,
      stockKrw: 1350000,
      afterTaxValueKrw: 1240000,
      afterTaxReturnPercent: 3.33,
    }
    const { rerender } = render(<DcaConditionalResult result={result} {...props} />)
    expect(screen.queryByText('예상 양도세')).toBeNull()

    rerender(<DcaConditionalResult result={{ ...result, capitalGainsTax: tax }} {...props} />)
    expect(screen.getByText('· ₩110,000 · 낸 ₩0 · 세후 수익률 +3.33%')).toBeInTheDocument()
    await open('예상 양도세')
    expect(screen.getByText('세후 총 평가').nextSibling).toHaveTextContent('₩1,240,000')
    expect(screen.getByText('세후 수익률 · 누적 매수액 기준')).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /2023년/ })).toHaveTextContent('₩110,000')
  })

  it('매매 통계가 있으면 지표 카드 아래 · 차트와 예상 양도세 위에 카드를 두고, 없으면 두지 않는다', () => {
    const tradeStats = {
      buyCount: 2,
      sellCount: 1,
      cycleCount: 1,
      openCycle: true,
      winCount: 1,
      lossCount: 0,
      winRatePercent: 100,
      averageWinPercent: 20,
      averageLossPercent: null,
      profitLossRatio: null,
      averageHoldingDays: 3,
      cyclesPerYear: 1.2,
      trailingStopCount: 0,
      partialTakeProfitCount: 0,
    }
    const tax = {
      years: [],
      totalTaxKrw: 0,
      paidTaxKrw: 0,
      unpaidTaxKrw: 0,
      cashKrw: 0,
      stockKrw: 1350000,
      afterTaxValueKrw: 1350000,
      afterTaxReturnPercent: 12.5,
    }
    const after = (first: Node, second: Node) =>
      (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0
    const { rerender } = render(<DcaConditionalResult result={{ ...result, capitalGainsTax: tax }} {...props} />)
    expect(screen.queryByRole('heading', { name: '매매 통계' })).toBeNull()

    rerender(<DcaConditionalResult result={{ ...result, capitalGainsTax: tax, tradeStats }} {...props} />)
    const heading = screen.getByRole('heading', { name: '매매 통계' })
    expect(screen.getByText('사이클 1번 · 진행 중')).toBeInTheDocument()
    expect(after(card('평가 가치'), heading)).toBe(true)
    expect(after(heading, screen.getByRole('tab', { name: '자산 추이' }))).toBe(true)
    expect(after(heading, screen.getByText('예상 양도세'))).toBe(true)
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

  it('다음 거래일이 다음 달이나 기간 끝 뒤라 앞 거래일에 산 번수는 따로 알린다', () => {
    const transactions = [
      { date: '2023-09-30', actualDate: '2023-09-29', shares: 1, price: 140, amount: 100000, fxRate: 1300 },
      { date: '2023-10-31', actualDate: '2023-10-31', shares: 1, price: 145, amount: 100000, fxRate: 1300 },
    ]
    render(<DcaConditionalResult result={{ ...result, transactions }} {...props} kind="dca" />)

    expect(
      screen.getByText('투자일이 휴장일이고 다음 거래일이 다음 달이나 기간 끝 뒤면 앞 거래일 종가로 삽니다(이번 결과 1번).')
    ).toBeInTheDocument()
    expect(screen.queryByText(/^투자일이 휴장일이면/)).toBeNull()
  })

  it('환율은 소수 둘째 자리까지, 하락은 부호를 통화 앞에 둔다', async () => {
    render(<DcaConditionalResult result={{ ...result, currentFxRate: 1325.5, fxReturn: -30.02 }} {...props} />)
    await open('상세')

    expect(screen.getByText('₩1,325.50')).toBeInTheDocument()
    expect(screen.getByText('-₩30.02')).toBeInTheDocument()
  })
})
