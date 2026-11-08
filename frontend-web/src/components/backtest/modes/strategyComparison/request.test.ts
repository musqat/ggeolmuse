import { describe, it, expect } from 'vitest'
import { buildStrategyComparisonRequest, type StrategyComparisonValues } from './request'

const ctx = { today: '2026-10-08' }

const values: StrategyComparisonValues = {
  symbol: 'AAPL',
  startDate: '2023-01-01',
  endDate: '2026-10-08',
  investment: '1000000',
  selectedStrategies: ['SIMPLE', 'DCA'],
  strategyParameters: {},
  reinvestDividends: false,
  tradingFeeRate: '0',
  dividendTax: false,
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
      },
    })
  })

  it('조건부 매수에 익절 · 손절이 있어도 배당 재투자는 고른 대로 보낸다', () => {
    const selling = buildStrategyComparisonRequest(
      {
        ...values,
        reinvestDividends: true,
        selectedStrategies: ['SIMPLE', 'CONDITIONAL_PURCHASE'],
        strategyParameters: { CONDITIONAL_PURCHASE: { takeProfit: '20' } },
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
        selectedStrategies: ['DCA', 'CONDITIONAL_PURCHASE'],
        strategyParameters: {
          DCA: { amountPerPurchase: '200000', cadence: 'MONTHS', months: '3', firstPurchaseDate: '2023-01-15' },
          CONDITIONAL_PURCHASE: { amountPerPurchase: '300000', dropPercentage: '3', maxPurchases: '2' },
        },
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

  it('수동 환율이면 두 환율을 숫자로 넣는다', () => {
    expect(buildStrategyComparisonRequest({ ...values, fxMode: 'manual' }, ctx)).toMatchObject({
      request: { purchaseFxRate: 1300, currentFxRate: 1350 },
    })
  })

  it.each([
    [{ selectedStrategies: ['SIMPLE' as const] }, '최소 2개 이상의 전략을 선택해주세요.'],
    [{ endDate: '2023-01-01' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
    [{ startDate: '2026-10-08', endDate: '' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
    [{ investment: '' }, '올바른 원금을 입력해주세요.'],
    [{ investment: '0' }, '올바른 원금을 입력해주세요.'],
    [{ strategyParameters: { DCA: { cadence: 'MONTHS', months: '0' } } }, '적립식: 개월 수는 1~12 사이의 정수로 입력해주세요.'],
    [{ investment: '50000' }, '적립식: 회당 금액이 원금보다 큽니다.'],
    [
      { endDate: '', strategyParameters: { DCA: { firstPurchaseDate: '2026-10-09' } } },
      '적립식: 첫 매수일은 시작일과 종료일 사이로 골라주세요.',
    ],
    [
      { endDate: '2025-06-30', strategyParameters: { DCA: { firstPurchaseDate: '2025-07-01' } } },
      '적립식: 첫 매수일은 시작일과 종료일 사이로 골라주세요.',
    ],
  ])('%o 는 막는다', (patch, error) => {
    expect(buildStrategyComparisonRequest({ ...values, ...patch }, ctx)).toEqual({ error })
  })
})
