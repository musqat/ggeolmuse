import { describe, it, expect } from 'vitest'
import { buildStrategyComparisonRequest, type StrategyComparisonValues } from './request'

const ctx = { userId: 'u@x.com', today: '2026-10-08', now: new Date('2026-10-08T03:00:00Z') }

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
  it('고른 순서대로 카탈로그 변환을 거친 전략을 보낸다', () => {
    expect(buildStrategyComparisonRequest(values, ctx)).toEqual({
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
            monthlyAmount: 100000,
            purchaseDay: 15,
            investmentInterval: 1,
            totalInvestmentLimit: 1000000,
          },
        ],
        reinvestDividends: false,
        tradingFeeRate: 0,
        dividendTaxRate: 0,
        userId: 'u@x.com',
      },
    })
  })

  it('종료일이 비면 시작일 검사를 지나 오늘로 채운다', () => {
    expect(buildStrategyComparisonRequest({ ...values, endDate: '' }, ctx)).toMatchObject({
      request: { endDate: '2026-10-08' },
    })
  })

  it('저장한 파라미터를 쓴다', () => {
    const built = buildStrategyComparisonRequest(
      { ...values, strategyParameters: { DCA: { monthlyAmount: '200000', purchaseDay: '1', investmentInterval: '3' } } },
      ctx
    )
    expect(built).toMatchObject({
      request: { strategies: [{}, { monthlyAmount: 200000, purchaseDay: 1, investmentInterval: 3 }] },
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
    [{ strategyParameters: { DCA: { purchaseDay: '40' } } }, '적립식: 매수일이 유효하지 않습니다 (1-31).'],
  ])('%o 는 막는다', (patch, error) => {
    expect(buildStrategyComparisonRequest({ ...values, ...patch }, ctx)).toEqual({ error })
  })
})
