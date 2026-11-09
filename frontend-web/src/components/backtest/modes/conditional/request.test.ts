import { describe, it, expect } from 'vitest'
import { buildConditionalRequest, type ConditionalValues } from './request'

const ctx = { today: '2026-10-08' }

const values: ConditionalValues = {
  startDate: '2023-01-01',
  endDate: '',
  amountPerPurchase: '100000',
  unit: 'AMOUNT',
  shares: '1',
  totalInvestment: '1000000',
  maxPurchases: '',
  direction: 'DROP',
  dropPercentage: '5',
  takeProfit: '',
  stopLoss: '',
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
  direction: 'DROP',
  dropPercentage: 0.05,
  reinvestDividends: false,
  tradingFeeRate: 0,
  dividendTaxRate: 0,
}

describe('buildConditionalRequest', () => {
  it('회당 금액 · 예산을 보내고 비운 최대 횟수는 싣지 않는다', () => {
    expect(buildConditionalRequest(values, 'AAPL', ctx)).toStrictEqual({
      request: { ...base, amountPerPurchase: 100000, totalInvestment: 1000000 },
    })
  })

  it('예산을 비우면 싣지 않고 최대 횟수만 보낸다', () => {
    expect(buildConditionalRequest({ ...values, totalInvestment: '', maxPurchases: '20' }, 'AAPL', ctx)).toStrictEqual({
      request: { ...base, amountPerPurchase: 100000, maxPurchases: 20 },
    })
  })

  it('예산 · 최대 횟수를 모두 비우면 회당 금액만 보낸다', () => {
    expect(buildConditionalRequest({ ...values, totalInvestment: ' ' }, 'AAPL', ctx)).toStrictEqual({
      request: { ...base, amountPerPurchase: 100000 },
    })
  })

  it('주식 수 단위면 회당 금액 칸은 보지 않고 회당 주식 수를 보낸다', () => {
    expect(
      buildConditionalRequest({ ...values, unit: 'SHARES', shares: '2', amountPerPurchase: '2000000' }, 'AAPL', ctx)
    ).toStrictEqual({
      request: { ...base, sharesPerPurchase: 2, totalInvestment: 1000000 },
    })
  })

  it.each(['0', '-1', '1.5', '1000001'])('회당 주식 수 %o 는 막는다', (shares) => {
    expect(buildConditionalRequest({ ...values, unit: 'SHARES', shares }, 'AAPL', ctx)).toEqual({
      error: '회당 주식 수는 1 ~ 1,000,000 사이의 정수로 입력해주세요.',
    })
  })

  it('수동 환율이면 두 환율을 숫자로 넣는다', () => {
    expect(buildConditionalRequest({ ...values, fxMode: 'manual' }, 'AAPL', ctx)).toMatchObject({
      request: { purchaseFxRate: 1300, currentFxRate: 1350 },
    })
  })

  it.each([
    [{ amountPerPurchase: '' }, '올바른 회당 금액을 입력해주세요.'],
    [{ amountPerPurchase: '0' }, '올바른 회당 금액을 입력해주세요.'],
    [{ totalInvestment: '0' }, '예산은 비우거나 0보다 크게 입력해주세요.'],
    [{ amountPerPurchase: '2000000' }, '회당 금액이 예산보다 큽니다.'],
    [{ maxPurchases: '0' }, '최대 횟수는 비우거나 1 이상의 정수로 입력해주세요.'],
    [{ maxPurchases: '2.5' }, '최대 횟수는 비우거나 1 이상의 정수로 입력해주세요.'],
    [{ dropPercentage: '101' }, '하락률은 0~100 사이여야 합니다.'],
    [{ endDate: '2022-01-01' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
  ])('%o 는 막는다', (patch, error) => {
    expect(buildConditionalRequest({ ...values, ...patch }, 'AAPL', ctx)).toEqual({ error })
  })

  it('익절 · 손절을 비우면 두 칸을 싣지 않는다', () => {
    const built = buildConditionalRequest(values, 'AAPL', ctx)
    expect('request' in built && 'takeProfitRate' in built.request).toBe(false)
    expect('request' in built && 'stopLossRate' in built.request).toBe(false)
  })

  it('익절 · 손절은 퍼센트를 비율로 바꿔 싣고, 함께 못 쓰는 배당 재투자는 끈다', () => {
    expect(buildConditionalRequest({ ...values, takeProfit: '20', stopLoss: '10', reinvestDividends: true }, 'AAPL', ctx))
      .toMatchObject({ request: { takeProfitRate: 0.2, stopLossRate: 0.1, reinvestDividends: false } })
  })

  it.each([
    [{ takeProfit: '0' }, '익절 비율은 0보다 커야 합니다.'],
    [{ stopLoss: '100' }, '손절 비율은 0보다 크고 100보다 작아야 합니다.'],
    [{ stopLoss: '-1' }, '손절 비율은 0보다 크고 100보다 작아야 합니다.'],
  ])('익절 · 손절 %o 는 막는다', (patch, error) => {
    expect(buildConditionalRequest({ ...values, ...patch }, 'AAPL', ctx)).toEqual({ error })
  })

  it('고른 방향을 싣고, 비율 검사 문구는 그 방향의 이름으로 보인다', () => {
    const built = buildConditionalRequest({ ...values, direction: 'RISE' }, 'AAPL', ctx)
    expect('request' in built && built.request.direction).toBe('RISE')

    expect(buildConditionalRequest({ ...values, direction: 'RISE', dropPercentage: '101' }, 'AAPL', ctx)).toEqual({
      error: '상승률은 0~100 사이여야 합니다.',
    })
    expect(buildConditionalRequest({ ...values, direction: 'BOTH', dropPercentage: '0' }, 'AAPL', ctx)).toEqual({
      error: '변동률은 0~100 사이여야 합니다.',
    })
  })
})
