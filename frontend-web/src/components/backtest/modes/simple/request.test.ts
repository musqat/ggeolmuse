import { describe, it, expect } from 'vitest'
import { buildSimpleRequest, type SimpleValues } from './request'

const ctx = { today: '2026-10-08' }

const values: SimpleValues = {
  purchaseDate: '2023-01-01',
  saleDate: '',
  initialInvestment: '300000',
  unit: 'AMOUNT',
  shares: '1',
  reinvestDividends: false,
  tradingFeeRate: '0',
  dividendTax: false,
  capitalGainsTax: false,
  periodEnd: 'HOLD',
  fxMode: 'auto',
  manualPurchaseFxRate: '1300',
  manualCurrentFxRate: '1350',
  taxSale: 'NONE',
  yearEndHarvest: false,
}

describe('buildSimpleRequest', () => {
  it('양도세 · 연말 정리를 둘 다 켰을 때만 yearEndHarvest 를 싣는다', () => {
    expect(buildSimpleRequest({ ...values, capitalGainsTax: true, yearEndHarvest: true }, 'AAPL', ctx)).toMatchObject({
      request: { capitalGainsTax: true, yearEndHarvest: true },
    })
    expect(buildSimpleRequest({ ...values, capitalGainsTax: false, yearEndHarvest: true }, 'AAPL', ctx)).not.toHaveProperty('request.yearEndHarvest')
    expect(buildSimpleRequest({ ...values, capitalGainsTax: true, yearEndHarvest: false }, 'AAPL', ctx)).not.toHaveProperty('request.yearEndHarvest')
  })
  it('매도일이 비면 오늘로 채운다', () => {
    expect(buildSimpleRequest(values, 'AAPL', ctx)).toStrictEqual({
      request: {
        symbol: 'AAPL',
        purchaseDate: '2023-01-01',
        saleDate: '2026-10-08',
        investmentAmount: 300000,
        reinvestDividends: false,
        tradingFeeRate: 0,
        dividendTaxRate: 0,
        capitalGainsTax: false,
        periodEnd: 'HOLD',
      },
    })
  })

  it('수수료는 퍼센트를 비율로, 배당세는 15% 로 바꾼다', () => {
    const built = buildSimpleRequest(
      { ...values, tradingFeeRate: '0.25', dividendTax: true, reinvestDividends: true },
      'AAPL',
      ctx
    )
    expect(built).toMatchObject({
      request: { tradingFeeRate: 0.0025, dividendTaxRate: 0.15, reinvestDividends: true },
    })
  })

  it('수동 환율이면 두 환율을 숫자로 넣는다', () => {
    expect(buildSimpleRequest({ ...values, fxMode: 'manual' }, 'AAPL', ctx)).toMatchObject({
      request: { purchaseFxRate: 1300, currentFxRate: 1350 },
    })
  })

  it.each([
    ['abc', '올바른 투자 금액을 입력해주세요.'],
    ['0', '올바른 투자 금액을 입력해주세요.'],
    ['99999', '최소 10만원 이상 투자해주세요. (미국 주식 1주 구매를 위해 약 30만원 권장)'],
  ])('투자금 %s 는 막는다', (initialInvestment, error) => {
    expect(buildSimpleRequest({ ...values, initialInvestment }, 'AAPL', ctx)).toEqual({ error })
  })

  it('주식 수 단위면 투자 금액 대신 주식 수를 보내고 최소 투자금은 보지 않는다', () => {
    expect(buildSimpleRequest({ ...values, unit: 'SHARES', shares: '3', initialInvestment: '' }, 'AAPL', ctx)).toStrictEqual({
      request: {
        symbol: 'AAPL',
        purchaseDate: '2023-01-01',
        saleDate: '2026-10-08',
        sharesPerPurchase: 3,
        reinvestDividends: false,
        tradingFeeRate: 0,
        dividendTaxRate: 0,
        capitalGainsTax: false,
        periodEnd: 'HOLD',
      },
    })
  })

  it.each(['0', '1.5', '', '1000001'])('주식 수 %o 는 막는다', (shares) => {
    expect(buildSimpleRequest({ ...values, unit: 'SHARES', shares }, 'AAPL', ctx)).toEqual({
      error: '주식 수는 1 ~ 1,000,000 사이의 정수로 입력해주세요.',
    })
  })

  it.each(['2026-10-08', '2026-10-09'])('매수일 %s(오늘 · 내일)는 막는다', (purchaseDate) => {
    expect(buildSimpleRequest({ ...values, purchaseDate }, 'AAPL', ctx)).toEqual({
      error: '매수일은 과거 날짜여야 합니다.',
    })
  })

  it('어제 매수일은 통과한다', () => {
    expect(buildSimpleRequest({ ...values, purchaseDate: '2026-10-07' }, 'AAPL', ctx)).toMatchObject({
      request: { purchaseDate: '2026-10-07', saleDate: '2026-10-08' },
    })
  })

  it('매도일이 매수일보다 앞서면 막는다', () => {
    expect(buildSimpleRequest({ ...values, saleDate: '2022-12-31' }, 'AAPL', ctx)).toEqual({
      error: '시작일은 종료일보다 빠른 날짜여야 합니다.',
    })
  })
})
