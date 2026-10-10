import { describe, it, expect } from 'vitest'
import { buildConditionalRequest, type ConditionalValues } from './request'

const ctx = { userId: 'u@x.com', today: '2026-10-08' }

const values: ConditionalValues = {
  startDate: '2023-01-01',
  endDate: '',
  investmentMode: 'TOTAL_BUDGET',
  totalInvestment: '1000000',
  amountPerPurchase: '100000',
  maxPurchases: '20',
  dropPercentage: '5',
  reinvestDividends: false,
  tradingFeeRate: '0',
  dividendTax: false,
  fxMode: 'auto',
  manualPurchaseFxRate: '1300',
  manualCurrentFxRate: '1350',
}

const base = {
  symbol: 'AAPL',
  startDate: '2023-01-01',
  endDate: '2026-10-08',
  dropPercentage: 0.05,
  reinvestDividends: false,
  tradingFeeRate: 0,
  dividendTaxRate: 0,
  userId: 'u@x.com',
}

describe('buildConditionalRequest', () => {
  it('총 예산 모드는 총 투자금과 회당 금액을 보낸다', () => {
    expect(buildConditionalRequest(values, 'AAPL', ctx)).toEqual({
      request: { ...base, investmentMode: 'TOTAL_BUDGET', totalInvestment: 1000000, amountPerPurchase: 100000 },
    })
  })

  it('회당 금액 모드는 회당 금액과 최대 횟수를 보낸다', () => {
    expect(buildConditionalRequest({ ...values, investmentMode: 'PER_PURCHASE' }, 'AAPL', ctx)).toEqual({
      request: { ...base, investmentMode: 'PER_PURCHASE', amountPerPurchase: 100000, maxPurchases: 20 },
    })
  })

  it('수동 환율이면 두 환율을 숫자로 넣는다', () => {
    expect(buildConditionalRequest({ ...values, fxMode: 'manual' }, 'AAPL', ctx)).toMatchObject({
      request: { purchaseFxRate: 1300, currentFxRate: 1350 },
    })
  })

  it.each([
    [{ totalInvestment: '0' }, '올바른 총 투자금을 입력해주세요.'],
    [{ amountPerPurchase: '' }, '올바른 회당 투자금을 입력해주세요.'],
    [{ amountPerPurchase: '2000000' }, '회당 투자금은 총 투자금보다 작아야 합니다.'],
    [{ investmentMode: 'PER_PURCHASE' as const, maxPurchases: '0' }, '올바른 최대 횟수를 입력해주세요.'],
    [{ dropPercentage: '101' }, '하락률은 0~100 사이여야 합니다.'],
    [{ endDate: '2022-01-01' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
  ])('%o 는 막는다', (patch, error) => {
    expect(buildConditionalRequest({ ...values, ...patch }, 'AAPL', ctx)).toEqual({ error })
  })
})
