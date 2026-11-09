import { describe, it, expect } from 'vitest'
import { buildStrategyComparisonRequest, type StrategyComparisonValues } from './request'

const ctx = { today: '2026-10-08' }

const chip = (id: string, type: 'SIMPLE' | 'DCA' | 'CONDITIONAL_PURCHASE', params = {}, name?: string) => ({
  id,
  type,
  name: name ?? { SIMPLE: '단순 매수', DCA: '적립식', CONDITIONAL_PURCHASE: '조건부 매수' }[type],
  params,
})

const values: StrategyComparisonValues = {
  symbol: 'AAPL',
  startDate: '2023-01-01',
  endDate: '2026-10-08',
  investment: '1000000',
  chips: [chip('s1', 'SIMPLE'), chip('s2', 'DCA')],
  reinvestDividends: false,
  tradingFeeRate: '0',
  dividendTax: false,
  capitalGainsTax: false,
  periodEnd: 'HOLD',
  fxMode: 'auto',
  manualPurchaseFxRate: '1300',
  manualCurrentFxRate: '1350',
}

describe('buildStrategyComparisonRequest', () => {
  it('고른 순서대로 카탈로그 변환을 거친 전략을 보내고 원금은 요청 금액 하나로 보낸다', () => {
    expect(buildStrategyComparisonRequest(values, ctx)).toStrictEqual({
      request: {
        symbol: 'AAPL',
        startDate: '2023-01-01',
        endDate: '2026-10-08',
        investmentAmount: 1000000,
        strategies: [
          { strategyType: 'SIMPLE', name: 'SIMPLE', purchaseDate: '2023-01-01' },
          {
            strategyType: 'DCA',
            name: 'DCA',
            amountPerPurchase: 100000,
            frequency: 'MONTHLY',
            purchaseDay: 1,
            investmentInterval: 1,
          },
        ],
        reinvestDividends: false,
        tradingFeeRate: 0,
        dividendTaxRate: 0,
        capitalGainsTax: false,
        periodEnd: 'HOLD',
      },
    })
  })

  it('조건부 매수에 익절 · 손절이 있어도 배당 재투자는 고른 대로 보낸다', () => {
    const selling = buildStrategyComparisonRequest(
      {
        ...values,
        reinvestDividends: true,
        chips: [chip('s1', 'SIMPLE'), chip('s3', 'CONDITIONAL_PURCHASE', { takeProfit: '20' })],
      },
      ctx
    )
    expect(selling).toMatchObject({ request: { reinvestDividends: true, strategies: [{}, { takeProfitRate: 0.2 }] } })
  })

  it('종료일이 비면 오늘로 채운다', () => {
    expect(buildStrategyComparisonRequest({ ...values, endDate: '' }, ctx)).toMatchObject({
      request: { endDate: '2026-10-08' },
    })
  })

  it('칩마다 저장한 설정을 쓴다', () => {
    const built = buildStrategyComparisonRequest(
      {
        ...values,
        chips: [
          chip('s2', 'DCA', { amountPerPurchase: '200000', cadence: 'MONTHS', months: '3', firstPurchaseDate: '2023-01-15' }),
          chip('s3', 'CONDITIONAL_PURCHASE', { amountPerPurchase: '300000', dropPercentage: '3', maxPurchases: '2' }),
        ],
      },
      ctx
    )
    expect(built).toMatchObject({
      request: {
        strategies: [
          { amountPerPurchase: 200000, frequency: 'MONTHLY', firstPurchaseDate: '2023-01-15', purchaseDay: 15, investmentInterval: 3 },
          { amountPerPurchase: 300000, dropPercentage: 0.03, maxPurchases: 2 },
        ],
      },
    })
  })

  it('같은 종류 칩이 여럿이면 첫 칩은 종류 이름, 다음 칩은 칩 이름으로 보낸다', () => {
    const built = buildStrategyComparisonRequest(
      {
        ...values,
        chips: [chip('s2', 'DCA'), chip('s4', 'DCA', { cadence: 'WEEKLY' }, '적립식 2'), chip('s5', 'SIMPLE')],
      },
      ctx
    )
    expect(built).toMatchObject({
      request: {
        strategies: [
          { strategyType: 'DCA', name: 'DCA' },
          { strategyType: 'DCA', name: '적립식 2', frequency: 'WEEKLY' },
          { strategyType: 'SIMPLE', name: 'SIMPLE' },
        ],
      },
    })
  })

  it('수동 환율이면 두 환율을 숫자로 넣는다', () => {
    expect(buildStrategyComparisonRequest({ ...values, fxMode: 'manual' }, ctx)).toMatchObject({
      request: { purchaseFxRate: 1300, currentFxRate: 1350 },
    })
  })

  it.each([
    [{ chips: [chip('s1', 'SIMPLE')] }, '최소 2개 이상의 전략을 선택해주세요.'],
    [{ endDate: '2023-01-01' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
    [{ startDate: '2026-10-08', endDate: '' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
    [{ investment: '' }, '올바른 원금을 입력해주세요.'],
    [{ investment: '0' }, '올바른 원금을 입력해주세요.'],
    [{ chips: [chip('s1', 'SIMPLE'), chip('s2', 'DCA', { cadence: 'MONTHS', months: '0' })] }, '적립식: 개월 수는 1~12 사이의 정수로 입력해주세요.'],
    [{ chips: [chip('s1', 'SIMPLE'), chip('s4', 'DCA', { cadence: 'MONTHS', months: '0' }, '적립식 2')] }, '적립식 2: 개월 수는 1~12 사이의 정수로 입력해주세요.'],
    [{ investment: '50000' }, '적립식: 회당 금액이 원금보다 큽니다.'],
    [
      { endDate: '', chips: [chip('s1', 'SIMPLE'), chip('s2', 'DCA', { firstPurchaseDate: '2026-10-09' })] },
      '적립식: 첫 매수일은 시작일과 종료일 사이로 골라주세요.',
    ],
    [
      { endDate: '2025-06-30', chips: [chip('s1', 'SIMPLE'), chip('s2', 'DCA', { firstPurchaseDate: '2025-07-01' })] },
      '적립식: 첫 매수일은 시작일과 종료일 사이로 골라주세요.',
    ],
  ])('%o 는 막는다', (patch, error) => {
    expect(buildStrategyComparisonRequest({ ...values, ...patch }, ctx)).toEqual({ error })
  })
})
