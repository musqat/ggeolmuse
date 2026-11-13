import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PortfolioAssetResult, PortfolioBacktestResponse, TradeReason } from '../../../../services/api'
import { PortfolioResult } from './PortfolioResult'

// 자산 추이는 받은 통화 · 일별 값 수 · 넣은 돈 합만 보임
vi.mock('../AssetChart', () => ({
  AssetChart: ({
    dailyValues,
    deposits,
    currency,
  }: {
    dailyValues?: unknown[]
    deposits: { krw: number }[]
    currency: string
  }) => (
    <div>
      자산 추이 {currency} {dailyValues?.length ?? 0}일 · 넣은 돈 {deposits.reduce((sum, d) => sum + d.krw, 0)}
    </div>
  ),
}))

const asset = (patch: Partial<PortfolioAssetResult>): PortfolioAssetResult => ({
  symbol: 'SPY',
  strategyType: 'LUMP_SUM',
  investedKrw: 6000000,
  boughtKrw: 6000000,
  valueKrw: 8650000,
  soldKrw: 0,
  dividendKrw: 410000,
  profitKrw: 3060000,
  returnPercent: 51,
  contributionPercent: 72,
  startWeightPercent: 60,
  endWeightPercent: 60.7,
  shares: 20.512345,
  realizedProfitKrw: 0,
  buyCount: 1,
  sellCount: 0,
  ...patch,
})

const result: PortfolioBacktestResponse = {
  startDate: '2020-01-02',
  endDate: '2024-12-31',
  method: 'LUMP_SUM',
  rebalance: 'NONE',
  periodEnd: 'HOLD',
  totalInvestedKrw: 10000000,
  totalAssetKrw: 14250000,
  stockValueKrw: 14100000,
  cashKrw: 150000,
  cashUsd: 108.3,
  totalReturnKrw: 4250000,
  totalReturnPercent: 42.5,
  realizedProfitKrw: 0,
  otherProfitKrw: -260000,
  currentFxRate: 1385.2,
  buyCount: 2,
  sellCount: 0,
  metricsKrw: { cagr: 7.33, mdd: -24.1 },
  metricsUsd: { cagr: 6.1, mdd: -26.8 },
  dailyValues: [
    { date: '2020-01-02', valueUsd: 7692.31, valueKrw: 10000000 },
    { date: '2024-12-31', valueUsd: 10287.4, valueKrw: 14250000 },
  ],
  assets: [
    asset({}),
    asset({
      symbol: 'QQQ',
      strategyType: 'DCA',
      investedKrw: 4000000,
      boughtKrw: 4000000,
      valueKrw: 5450000,
      soldKrw: 120000,
      dividendKrw: 0,
      profitKrw: 1450000,
      returnPercent: 36.25,
      contributionPercent: 34.12,
      startWeightPercent: 40,
      endWeightPercent: 38.25,
    }),
  ],
  transactions: [
    { symbol: 'SPY', reason: 'BUY', date: '2020-01-02', trigger: '한 번에 매수', shares: 20.512345, price: 324.87, amount: 6000000, fxRate: 1300 },
    { symbol: 'QQQ', reason: 'BUY', date: '2020-01-02', trigger: '한 번에 매수', shares: 10, price: 300, amount: 4000000, fxRate: 1300 },
    { symbol: 'SPY', reason: 'DIVIDEND_REINVESTMENT', date: '2021-03-19', trigger: '배당 재투자', shares: 0.1, price: 390, amount: 0, fxRate: 1130, cashUsed: 39 },
  ],
  tradeStats: null,
  capitalGainsTax: null,
  defaultFxDates: [],
}

const tax = {
  years: [{ year: 2023, realizedGainKrw: 9000000, deductionKrw: 2500000, taxKrw: 1330000, dueDate: '2024-05-01', paidKrw: 1000000 }],
  totalTaxKrw: 1330000,
  paidTaxKrw: 1000000,
  unpaidTaxKrw: 330000,
  cashKrw: 30000000,
  stockKrw: 0,
  afterTaxValueKrw: 29670000,
  afterTaxReturnPercent: 196.7,
}

// 기간 끝에 두 종목을 판 결과(양도세 납부 한 번)
const sold: PortfolioBacktestResponse = {
  ...result,
  periodEnd: 'SELL_AT_END',
  realizedProfitKrw: 9000000,
  sellCount: 2,
  assets: [asset({ valueKrw: 0, soldKrw: 15000000 }), asset({ symbol: 'QQQ', valueKrw: 0, soldKrw: 5120000 })],
  transactions: [
    ...result.transactions,
    { reason: 'TAX_PAYMENT', date: '2024-05-01', trigger: '양도세 납부', shares: 0, price: 0, amount: 0, fxRate: 1400, taxPaid: 714.29 },
    { symbol: 'SPY', reason: 'PERIOD_END_SALE', date: '2024-12-31', trigger: '기간 끝 매도', shares: -20.6, price: 590, amount: 0, fxRate: 1450, proceeds: 12154, realizedProfit: 5300 },
    { symbol: 'QQQ', reason: 'PERIOD_END_SALE', date: '2024-12-31', trigger: '기간 끝 매도', shares: -10, price: 520, amount: 0, fxRate: 1450, proceeds: 5200, realizedProfit: 2200 },
  ],
  capitalGainsTax: tax,
}

const card = (label: string) => screen.getByText(label).parentElement as HTMLElement
const value = (label: string) => card(label).querySelector('p:nth-of-type(2)') as HTMLElement
const rows = () => within(screen.getByRole('table')).getAllByRole('row').slice(1)

describe('PortfolioResult', () => {
  it('지표 카드 넷(총 평가 · 총 수익률 · 연평균 · 최대 낙폭)과 합친 자산 추이를 원화로 보이고, 달러 기준으로 바꾸면 지표 · 자산 추이가 달러다', async () => {
    render(<PortfolioResult result={result} />)

    expect(value('총 평가')).toHaveTextContent('₩14,250,000')
    expect(card('총 평가')).toHaveTextContent('넣은 돈 ₩10,000,000')
    expect(screen.getByTestId('backtest-return-rate')).toHaveTextContent('+42.50%')
    expect(screen.getByTestId('backtest-return-rate')).toHaveClass('text-gain')
    expect(card('총 수익률')).toHaveTextContent('+₩4,250,000')
    expect(value('연평균 수익률')).toHaveTextContent('+7.33%')
    expect(card('연평균 수익률')).toHaveTextContent('시간가중 · 달러 기준 +6.10%')
    expect(value('최대 낙폭')).toHaveTextContent('-24.10%')
    expect(value('최대 낙폭')).toHaveClass('text-loss')
    expect(card('최대 낙폭')).toHaveTextContent('달러 기준 -26.80%')
    expect(card('총 평가').parentElement).toHaveClass('grid-cols-2', 'lg:grid-cols-4')
    expect(screen.getByText('자산 추이 KRW 2일 · 넣은 돈 10000000')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '달러 기준' }))
    expect(value('연평균 수익률')).toHaveTextContent('+6.10%')
    expect(card('최대 낙폭')).toHaveTextContent('원화 기준 -24.10%')
    expect(screen.getByText('자산 추이 USD 2일 · 넣은 돈 10000000')).toBeInTheDocument()
  })

  it('지표가 없으면 지표 기준 고르기 없이 연평균 · 최대 낙폭을 비워 두고, 1년 미만이면 연평균만 비워 둔다', () => {
    const { unmount } = render(<PortfolioResult result={{ ...result, metricsKrw: null, metricsUsd: null, dailyValues: [] }} />)

    expect(screen.queryByRole('group', { name: '지표 기준 통화' })).toBeNull()
    expect(value('연평균 수익률')).toHaveTextContent('—')
    expect(card('연평균 수익률')).toHaveTextContent('지표 없음')
    expect(value('최대 낙폭')).toHaveTextContent('—')
    expect(screen.getByText('자산 추이 KRW 0일 · 넣은 돈 10000000')).toBeInTheDocument()
    unmount()

    render(<PortfolioResult result={{ ...result, metricsKrw: { cagr: null, mdd: -3 }, metricsUsd: { cagr: null, mdd: -4 } }} />)
    expect(value('연평균 수익률')).toHaveTextContent('—')
    expect(card('연평균 수익률')).toHaveTextContent('1년 미만이면 내지 않음')
  })

  it('종목별 표는 요청 순서 줄마다 전략 · 원금 · 지금 가치(판 돈 · 배당) · 수익률 · 기여 · 비중 처음 → 지금, 맨 아래 현금 줄과 종목 밖 이익 줄을 둔다', () => {
    render(<PortfolioResult result={result} />)

    expect(screen.getByRole('heading', { name: '종목별' })).toBeInTheDocument()
    expect(screen.getByText('리밸런싱 없음 · 평가일 환율 ₩1,385.20')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getAllByRole('columnheader').map((th) => th.textContent)).toEqual([
      '종목',
      '전략',
      '원금',
      '지금 가치',
      '수익률',
      '기여',
      '비중 처음 → 지금',
    ])
    const [spy, qqq, cash, other] = rows()
    const cells = (row: HTMLElement) => within(row).getAllByRole('cell').map((cell) => cell.textContent)
    expect(within(spy).getByRole('rowheader')).toHaveTextContent('SPY')
    expect(cells(spy)).toEqual(['한 번에', '₩6,000,000', '₩8,650,000배당 ₩410,000', '+51.00%', '+₩3,060,000 · 72.00%', '60.00% → 60.70%'])
    expect(cells(qqq)).toEqual(['적립식', '₩4,000,000', '₩5,450,000판 돈 ₩120,000', '+36.25%', '+₩1,450,000 · 34.12%', '40.00% → 38.25%'])
    expect(within(qqq).getByText('+36.25%')).toHaveClass('text-gain')
    expect(within(cash).getByRole('rowheader')).toHaveTextContent('현금')
    expect(cells(cash)).toEqual(['-', '-', '₩150,000', '-', '-', '- → 1.05%'])
    expect(within(other).getByRole('rowheader')).toHaveTextContent('현금 환율 차 · 양도세')
    expect(cells(other)).toEqual(['-', '-', '-', '-', '-₩260,000', '-'])
    expect(within(other).getByText('-₩260,000')).toHaveClass('text-loss')
  })

  it('종목별 표 제목 줄 오른쪽에 리밸런싱 주기를 평가일 환율 앞에 보인다', () => {
    const { rerender } = render(<PortfolioResult result={{ ...result, rebalance: 'YEARLY' }} />)
    expect(screen.getByText('매년 리밸런싱 · 평가일 환율 ₩1,385.20')).toBeInTheDocument()

    rerender(<PortfolioResult result={{ ...result, rebalance: 'QUARTERLY' }} />)
    expect(screen.getByText('분기마다 리밸런싱 · 평가일 환율 ₩1,385.20')).toBeInTheDocument()

    rerender(<PortfolioResult result={{ ...result, rebalance: 'MONTHLY' }} />)
    expect(screen.getByText('매달 리밸런싱 · 평가일 환율 ₩1,385.20')).toBeInTheDocument()
  })

  it('판 돈으로 다시 산 줄은 원금 칸에 처음 넣은 돈만 보이고 수익률은 원금 대비다', () => {
    render(
      <PortfolioResult
        result={{
          ...result,
          assets: [
            asset({
              symbol: 'QQQ',
              strategyType: 'MA_CROSS',
              investedKrw: 1300000,
              boughtKrw: 2300000,
              valueKrw: 780000,
              soldKrw: 1000000,
              dividendKrw: 0,
              profitKrw: -520000,
              returnPercent: -40,
              contributionPercent: -12.24,
              buyCount: 2,
              sellCount: 1,
            }),
          ],
        }}
      />
    )

    const cells = within(rows()[0]).getAllByRole('cell').map((cell) => cell.textContent)
    expect(cells.slice(0, 4)).toEqual(['이동평균 교차', '₩1,300,000', '₩780,000판 돈 ₩1,000,000', '-40.00%'])
    expect(within(rows()[0]).getByText('-40.00%')).toHaveClass('text-loss')
  })

  it('종목 밖 이익이 0 이면 그 줄이 없고, 원금 · 총 수익이 0 인 줄은 수익률 · 기여를 - 로 둔다', () => {
    render(
      <PortfolioResult
        result={{
          ...result,
          otherProfitKrw: 0,
          totalAssetKrw: 0,
          cashKrw: 0,
          assets: [asset({ investedKrw: 0, boughtKrw: 0, valueKrw: 0, dividendKrw: 0, profitKrw: 0, returnPercent: null, contributionPercent: null, endWeightPercent: 0 })],
        }}
      />
    )

    expect(rows()).toHaveLength(2)
    const cells = within(rows()[0]).getAllByRole('cell').map((cell) => cell.textContent)
    expect(cells).toEqual(['한 번에', '₩0', '₩0', '-', '₩0 · -', '60.00% → 0.00%'])
    expect(within(rows()[1]).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['-', '-', '₩0', '-', '-', '- → 0.00%'])
  })

  it('이동평균 교차 · 밴드 종목은 전략 이름을 보이고, 기간 안 매수 신호가 없어 사지 않았으면 그 아래 매수 신호 없음', () => {
    render(
      <PortfolioResult
        result={{
          ...result,
          assets: [
            asset({ strategyType: 'MA_CROSS', investedKrw: 0, boughtKrw: 0, valueKrw: 0, dividendKrw: 0, profitKrw: 0, returnPercent: null, buyCount: 0 }),
            asset({ symbol: 'QQQ', strategyType: 'MA_BAND', buyCount: 3, sellCount: 2 }),
            asset({ symbol: 'IAU', strategyType: 'DCA', investedKrw: 0, boughtKrw: 0, buyCount: 0 }),
          ],
        }}
      />
    )

    const strategy = (row: HTMLElement) => within(row).getAllByRole('cell')[0].textContent
    expect(rows().slice(0, 3).map(strategy)).toEqual(['이동평균 교차매수 신호 없음', '밴드', '적립식'])
    expect(within(rows()[0]).getByText('매수 신호 없음')).toHaveClass('text-xs')
  })

  it('판 것이 없으면 실현 손익 카드 · 매매 내역 · 예상 양도세가 없다', () => {
    render(<PortfolioResult result={{ ...result, capitalGainsTax: tax }} />)

    expect(screen.queryByText('실현 손익')).toBeNull()
    expect(screen.queryByText('매매 내역')).toBeNull()
    expect(screen.queryByText('예상 양도세')).toBeNull()
  })

  it('판 것이 있으면 실현 손익 · 낸 양도세 · 매수 · 매도 카드와 매매 내역 · 예상 양도세 접는 칸을 둔다', async () => {
    render(<PortfolioResult result={sold} />)

    expect(value('실현 손익')).toHaveTextContent('+₩9,000,000')
    expect(value('실현 손익')).toHaveClass('text-gain')
    expect(card('실현 손익')).toHaveTextContent('판 돈 ₩20,120,000')
    expect(value('낸 양도세')).toHaveTextContent('₩1,000,000')
    expect(card('낸 양도세')).toHaveTextContent('남은 양도세 ₩330,000')
    expect(value('매수 · 매도')).toHaveTextContent('2번 · 2번')
    expect(screen.getByText('기간 끝 2번')).toBeInTheDocument()
    expect(screen.getByText('배당 재투자 1번 · 양도세 납부 1번')).toBeInTheDocument()
    expect(screen.getByText('· 거래 6건')).toBeInTheDocument()
    expect(screen.getByText('예상 양도세')).toBeInTheDocument()

    await userEvent.click(screen.getByText('매매 내역'))
    expect(screen.getByRole('combobox', { name: '종목' })).toBeInTheDocument()
    expect(within(screen.getAllByRole('table')[1]).getAllByRole('row')).toHaveLength(7)
  })

  it('매수 · 매도 카드 아래 줄에 매도를 전략 · 익절 · 손절 · 리밸런싱 · 기간 끝 · 양도세 납부용 차례로 이유마다 세고 0 인 이유는 뺀다', () => {
    const sell = (reason: TradeReason) => ({ symbol: 'SPY', reason, date: '2023-06-01', trigger: reason, shares: -1, price: 400, amount: 0, fxRate: 1300 })
    const { rerender } = render(
      <PortfolioResult
        result={{
          ...sold,
          sellCount: 7,
          transactions: [
            ...result.transactions,
            sell('TAX_SALE'),
            sell('PERIOD_END_SALE'),
            sell('REBALANCE_SELL'),
            sell('STOP_LOSS'),
            sell('TAKE_PROFIT'),
            sell('TAKE_PROFIT'),
            sell('STRATEGY_SELL'),
          ],
        }}
      />
    )
    expect(screen.getByText('전략 1번 · 익절 2번 · 손절 1번 · 리밸런싱 1번 · 기간 끝 1번 · 납부용 1번')).toBeInTheDocument()
    expect(screen.getByText('배당 재투자 1번')).toBeInTheDocument()

    rerender(<PortfolioResult result={{ ...sold, sellCount: 1, transactions: [result.transactions[0], sell('TAKE_PROFIT')] }} />)
    expect(screen.getByText('익절 1번')).toBeInTheDocument()
    expect(screen.queryByText(/배당 재투자/)).toBeNull()
  })

  it('양도세를 끄면 낸 양도세 카드는 - 와 끔 안내이고 예상 양도세 칸이 없다', () => {
    const transactions = sold.transactions.filter((tx) => tx.reason !== 'TAX_PAYMENT')
    render(<PortfolioResult result={{ ...sold, capitalGainsTax: null, transactions }} />)

    expect(value('낸 양도세')).toHaveTextContent('-')
    expect(card('낸 양도세')).toHaveTextContent('양도세 계산 끔')
    expect(card('매수 · 매도')).not.toHaveTextContent('양도세 납부')
    expect(screen.queryByText('예상 양도세')).toBeNull()
  })

  it('연말 정리만 팔아도 매매 카드 · 매매 내역 · 예상 양도세를 두고 매수 · 매도 카드 아래 줄에 연말 정리 수를 센다', () => {
    const harvest = (reason: TradeReason, shares: number) => ({ symbol: 'SPY', reason, date: '2023-12-29', trigger: reason, shares, price: 400, amount: 0, fxRate: 1300 })
    render(
      <PortfolioResult
        result={{
          ...result,
          capitalGainsTax: tax,
          transactions: [...result.transactions, harvest('HARVEST_GAIN_SELL', -1), harvest('HARVEST_GAIN_BUY', 1)],
        }}
      />
    )

    expect(value('매수 · 매도')).toHaveTextContent('2번 · 0번')
    expect(screen.getByText('배당 재투자 1번 · 연말 정리 1번')).toBeInTheDocument()
    expect(screen.getByText('매매 내역')).toBeInTheDocument()
    expect(screen.getByText('예상 양도세')).toBeInTheDocument()
  })

  it('기본 환율로 계산한 날이 있으면 맨 위에 알린다', () => {
    render(<PortfolioResult result={{ ...result, defaultFxDates: ['2013-12-31'] }} />)

    expect(screen.getByRole('note')).toHaveTextContent('2013-12-31')
  })
})
