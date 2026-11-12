import { describe, it, expect } from 'vitest'
import { buildPortfolioRequest, type PortfolioValues } from './request'
import { newRow, type PortfolioRow } from '../../shared/portfolioRows'

const ctx = { today: '2026-10-08' }

const row = (symbol: string, amount: string, weight = '', takeProfit = '', stopLoss = ''): PortfolioRow => ({
  ...newRow(symbol, amount, weight),
  takeProfit,
  stopLoss,
})

const withStrategy = (base: PortfolioRow, patch: Partial<PortfolioRow>): PortfolioRow => ({ ...base, ...patch })

const values: PortfolioValues = {
  startDate: '2023-01-01',
  endDate: '',
  rows: [row('SPY', '6000000', '60'), row(' qqq ', '4000000', '40')],
  allocation: 'AMOUNT',
  total: '10000000',
  method: 'LUMP_SUM',
  cadence: 'MONTHLY',
  months: '3',
  firstPurchaseDate: '',
  firstPickedStart: '',
  rebalance: 'NONE',
  reinvestDividends: true,
  tradingFeeRate: '0.25',
  dividendTax: true,
  capitalGainsTax: true,
  periodEnd: 'SELL_AT_END',
  fxMode: 'auto',
  manualPurchaseFxRate: '',
  manualCurrentFxRate: '',
}

const options = { reinvestDividends: true, tradingFeeRate: 0.0025, dividendTaxRate: 0.15, capitalGainsTax: true, periodEnd: 'SELL_AT_END' }

describe('buildPortfolioRequest', () => {
  it('금액 방식 한 번에 사기는 줄마다 대문자 종목 · 금액만 싣고, 종료일이 비면 오늘로 채우며 적립식 칸은 보내지 않는다', () => {
    expect(buildPortfolioRequest(values, ctx)).toStrictEqual({
      request: {
        startDate: '2023-01-01',
        endDate: '2026-10-08',
        allocation: 'AMOUNT',
        assets: [
          { symbol: 'SPY', amount: 6000000 },
          { symbol: 'QQQ', amount: 4000000 },
        ],
        method: 'LUMP_SUM',
        rebalance: 'NONE',
        ...options,
      },
    })
  })

  it('리밸런싱 주기는 방식과 상관없이 고른 값 그대로 싣는다', () => {
    expect(buildPortfolioRequest({ ...values, rebalance: 'QUARTERLY' }, ctx)).toMatchObject({
      request: { method: 'LUMP_SUM', rebalance: 'QUARTERLY' },
    })
    expect(buildPortfolioRequest({ ...values, method: 'DCA', rebalance: 'YEARLY' }, ctx)).toMatchObject({
      request: { method: 'DCA', frequency: 'MONTHLY', rebalance: 'YEARLY' },
    })
  })

  it('비율 방식은 총액과 줄마다 비율만 싣고 소수 비율 합도 100 에서 0.01 까지 받는다', () => {
    const built = buildPortfolioRequest(
      { ...values, allocation: 'WEIGHT', rows: [row('SPY', '', '33.33'), row('QQQ', '', '33.33'), row('IAU', '9', '33.33')] },
      ctx,
    )

    expect(built).toMatchObject({
      request: {
        allocation: 'WEIGHT',
        totalAmount: 10000000,
        assets: [
          { symbol: 'SPY', weightPercent: 33.33 },
          { symbol: 'QQQ', weightPercent: 33.33 },
          { symbol: 'IAU', weightPercent: 33.33 },
        ],
      },
    })
    expect('request' in built && built.request.assets[2]).not.toHaveProperty('amount')
  })

  it('익절 · 손절은 적은 줄에만 퍼센트를 요청 비율로 바꿔 싣고(10% = 0.1), 비율 방식도 같다', () => {
    const rows = [row('SPY', '6000000', '60', '30', '10'), row('QQQ', '4000000', '40', '', '5.5'), row('IAU', '1000000', '0', ' ', '')]

    const built = buildPortfolioRequest({ ...values, rows }, ctx)
    expect('request' in built && built.request.assets).toStrictEqual([
      { symbol: 'SPY', amount: 6000000, takeProfitRate: 0.3, stopLossRate: 0.1 },
      { symbol: 'QQQ', amount: 4000000, stopLossRate: 0.055 },
      { symbol: 'IAU', amount: 1000000 },
    ])

    const weighted = buildPortfolioRequest({ ...values, allocation: 'WEIGHT', rows: rows.slice(0, 2) }, ctx)
    expect('request' in weighted && weighted.request.assets).toStrictEqual([
      { symbol: 'SPY', weightPercent: 60, takeProfitRate: 0.3, stopLossRate: 0.1 },
      { symbol: 'QQQ', weightPercent: 40, stopLossRate: 0.055 },
    ])
  })

  it('적립식은 적립식 요청과 같은 주기 칸을 싣고, 매주는 고른 첫 매수일을 보낸다', () => {
    expect(buildPortfolioRequest({ ...values, method: 'DCA' }, ctx)).toMatchObject({
      request: { method: 'DCA', frequency: 'MONTHLY', purchaseDay: 1, investmentInterval: 1 },
    })
    expect(buildPortfolioRequest({ ...values, method: 'DCA', cadence: 'MONTHS', months: '3' }, ctx)).toMatchObject({
      request: { frequency: 'MONTHLY', investmentInterval: 3 },
    })

    const weekly = buildPortfolioRequest(
      { ...values, method: 'DCA', cadence: 'WEEKLY', firstPurchaseDate: '2023-01-04', firstPickedStart: '2023-01-01' },
      ctx,
    )
    expect(weekly).toMatchObject({ request: { frequency: 'WEEKLY', firstPurchaseDate: '2023-01-04' } })
    expect('request' in weekly && weekly.request).not.toHaveProperty('purchaseDay')
  })

  it('방식대로인 줄은 전략을 싣지 않고, 고른 줄은 한 번에 · 적립식은 종류만 · 이동평균은 두 기간 · 밴드는 평균 기간과 위 · 아래 비율(2.5% = 0.025)을 싣는다', () => {
    const rows = [
      row('SPY', '4000000'),
      withStrategy(row('QQQ', '3000000'), { strategy: 'MA_CROSS', shortWindow: '5', longWindow: '30', bandWindow: '' }),
      withStrategy(row('IAU', '2000000', '', '20'), { strategy: 'MA_BAND', bandWindow: '120', upper: '1.5', lower: '0', shortWindow: '' }),
      withStrategy(row('TLT', '1000000'), { strategy: 'LUMP_SUM' }),
    ]

    const built = buildPortfolioRequest({ ...values, method: 'DCA', rows }, ctx)
    expect('request' in built && built.request.assets).toStrictEqual([
      { symbol: 'SPY', amount: 4000000 },
      { symbol: 'QQQ', amount: 3000000, strategy: { type: 'MA_CROSS', shortWindow: 5, longWindow: 30 } },
      { symbol: 'IAU', amount: 2000000, takeProfitRate: 0.2, strategy: { type: 'MA_BAND', bandWindow: 120, upperRate: 0.015, lowerRate: 0 } },
      { symbol: 'TLT', amount: 1000000, strategy: { type: 'LUMP_SUM' } },
    ])
  })

  it('적립식 칸은 방식과 상관없이 적립식 줄이 하나라도 있으면 싣고 보며, 없으면 적립식 방식이라도 싣지 않는다', () => {
    const dcaRow = withStrategy(row('QQQ', '1000000'), { strategy: 'DCA' })
    const lump = buildPortfolioRequest({ ...values, rows: [row('SPY', '6000000'), dcaRow] }, ctx)
    expect(lump).toMatchObject({
      request: {
        method: 'LUMP_SUM',
        assets: [{ symbol: 'SPY', amount: 6000000 }, { symbol: 'QQQ', amount: 1000000, strategy: { type: 'DCA' } }],
        frequency: 'MONTHLY',
        purchaseDay: 1,
        investmentInterval: 1,
      },
    })
    expect(buildPortfolioRequest({ ...values, rows: [dcaRow], cadence: 'MONTHS', months: '13' }, ctx)).toEqual({
      error: '개월 수는 1~12 사이의 정수로 입력해주세요.',
    })

    const signals = [
      withStrategy(row('SPY', '1'), { strategy: 'MA_CROSS' }),
      withStrategy(row('QQQ', '1'), { strategy: 'LUMP_SUM' }),
    ]
    const noDca = buildPortfolioRequest({ ...values, method: 'DCA', rows: signals, cadence: 'MONTHS', months: '' }, ctx)
    expect(noDca).toMatchObject({ request: { method: 'DCA' } })
    expect('request' in noDca && noDca.request).not.toHaveProperty('frequency')
  })

  it.each([
    ['이동평균 짧은 기간 1', { strategy: 'MA_CROSS', shortWindow: '1' }, '이동평균 기간은 2 ~ 250 사이 정수로, 짧은 기간을 긴 기간보다 짧게 입력해주세요.'],
    ['이동평균 짧은 기간 = 긴 기간', { strategy: 'MA_CROSS', shortWindow: '60' }, '이동평균 기간은 2 ~ 250 사이 정수로, 짧은 기간을 긴 기간보다 짧게 입력해주세요.'],
    ['밴드 평균 기간 251', { strategy: 'MA_BAND', bandWindow: '251' }, '밴드 기간은 2 ~ 250 사이 정수로, 위 · 아래 비율은 0 이상 100 미만으로 입력해주세요.'],
    ['밴드 빈 아래 비율', { strategy: 'MA_BAND', lower: '' }, '밴드 기간은 2 ~ 250 사이 정수로, 위 · 아래 비율은 0 이상 100 미만으로 입력해주세요.'],
  ] as [string, Partial<PortfolioRow>, string][])('%s: 그 줄 전략 칸이 맞지 않으면 막는다', (_name, patch, error) => {
    expect(buildPortfolioRequest({ ...values, rows: [row('SPY', '1'), withStrategy(row('QQQ', '1'), patch)] }, ctx)).toEqual({ error })
  })

  it('익절 · 손절 검사가 전략 칸 검사보다 먼저다', () => {
    const rows = [withStrategy(row('SPY', '1', '', '0'), { strategy: 'MA_CROSS', shortWindow: '1' })]
    expect(buildPortfolioRequest({ ...values, rows }, ctx)).toEqual({ error: '익절 비율은 0보다 커야 합니다.' })
  })

  it.each([
    ['줄이 없음', { rows: [] }, '종목은 1 ~ 10개, 같은 종목은 한 번만 넣어 주세요.'],
    ['빈 종목', { rows: [row('SPY', '1'), row(' ', '1')] }, '종목은 1 ~ 10개, 같은 종목은 한 번만 넣어 주세요.'],
    ['대문자로 같은 종목', { rows: [row('SPY', '1'), row('spy', '1')] }, '종목은 1 ~ 10개, 같은 종목은 한 번만 넣어 주세요.'],
    ['11줄', { rows: Array.from({ length: 11 }, (_, i) => row(`S${i}`, '1')) }, '종목은 1 ~ 10개, 같은 종목은 한 번만 넣어 주세요.'],
    ['0 원 줄', { rows: [row('SPY', '1'), row('QQQ', '0')] }, '종목마다 0보다 큰 금액을 넣어 주세요.'],
    ['빈 금액', { rows: [row('SPY', '')] }, '종목마다 0보다 큰 금액을 넣어 주세요.'],
    ['비율 방식 빈 총액', { allocation: 'WEIGHT', total: '' }, '총액은 0보다 크게 넣어 주세요.'],
    ['비율 합 90', { allocation: 'WEIGHT', rows: [row('SPY', '', '60'), row('QQQ', '', '30')] }, '비율은 줄마다 0보다 크게, 합이 100% 가 되게 넣어 주세요.'],
    ['0 비율 줄', { allocation: 'WEIGHT', rows: [row('SPY', '', '100'), row('QQQ', '', '0')] }, '비율은 줄마다 0보다 크게, 합이 100% 가 되게 넣어 주세요.'],
    ['1원이 안 되는 비율 줄', { allocation: 'WEIGHT', total: '100', rows: [row('SPY', '', '99.6'), row('QQQ', '', '0.4')] }, '비율은 줄마다 0보다 크게, 합이 100% 가 되게 넣어 주세요.'],
    ['0 익절 줄', { rows: [row('SPY', '1'), row('QQQ', '1', '', '0')] }, '익절 비율은 0보다 커야 합니다.'],
    ['100 손절 줄', { rows: [row('SPY', '1', '', '30', '100')] }, '손절 비율은 0보다 크고 100보다 작아야 합니다.'],
    ['비율 방식 0 손절 줄', { allocation: 'WEIGHT', rows: [row('SPY', '', '100', '', '0')] }, '손절 비율은 0보다 크고 100보다 작아야 합니다.'],
    ['개월 수 13', { method: 'DCA', cadence: 'MONTHS', months: '13' }, '개월 수는 1~12 사이의 정수로 입력해주세요.'],
    ['시작일 = 종료일', { endDate: '2023-01-01' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
    ['종료일 뒤 첫 매수일', { method: 'DCA', endDate: '2023-03-01', firstPurchaseDate: '2023-04-01', firstPickedStart: '2023-01-01' }, '첫 매수일은 시작일과 종료일 사이로 골라주세요.'],
  ] as [string, Partial<PortfolioValues>, string][])('%s: 막는다', (_name, patch, error) => {
    expect(buildPortfolioRequest({ ...values, ...patch }, ctx)).toEqual({ error })
  })

  it('한 번에 사기는 남은 적립식 칸이 맞지 않아도 보지 않는다', () => {
    expect(buildPortfolioRequest({ ...values, cadence: 'MONTHS', months: '', firstPurchaseDate: '2030-01-01' }, ctx)).toHaveProperty('request')
  })
})
