import { describe, it, expect } from 'vitest'
import { buildSimpleRequest, type SimpleValues } from './request'

const ctx = { userId: 'u@x.com', today: '2026-10-08' }

const values: SimpleValues = {
  purchaseDate: '2023-01-01',
  saleDate: '',
  initialInvestment: '300000',
  reinvestDividends: false,
  tradingFeeRate: '0',
  dividendTax: false,
  fxMode: 'auto',
  manualPurchaseFxRate: '1300',
  manualCurrentFxRate: '1350',
}

describe('buildSimpleRequest', () => {
  it('매도일이 비면 오늘로 채운다', () => {
    expect(buildSimpleRequest(values, 'AAPL', ctx)).toEqual({
      request: {
        symbol: 'AAPL',
        purchaseDate: '2023-01-01',
        saleDate: '2026-10-08',
        investmentAmount: 300000,
        reinvestDividends: false,
        tradingFeeRate: 0,
        dividendTaxRate: 0,
        userId: 'u@x.com',
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
