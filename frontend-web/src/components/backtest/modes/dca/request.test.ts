import { describe, it, expect } from 'vitest'
import { buildDcaRequest, type DcaValues } from './request'

const ctx = { today: '2026-10-08' }

const values: DcaValues = {
  startDate: '2023-01-01',
  endDate: '',
  amountPerPurchase: '100000',
  unit: 'AMOUNT',
  shares: '1',
  cadence: 'MONTHLY',
  months: '3',
  firstPurchaseDate: '',
  firstPickedStart: '',
  budget: '',
  reinvestDividends: false,
  tradingFeeRate: '0',
  dividendTax: false,
  fxMode: 'auto',
  manualPurchaseFxRate: '1300',
  manualCurrentFxRate: '1350',
}

describe('buildDcaRequest', () => {
  it('종료일이 비면 오늘로 채우고, 첫 매수일이 비면 요청에서 빼고 시작일의 날짜를 매달 투자일로 보낸다', () => {
    expect(buildDcaRequest(values, 'AAPL', ctx)).toStrictEqual({
      request: {
        symbol: 'AAPL',
        startDate: '2023-01-01',
        endDate: '2026-10-08',
        amountPerPurchase: 100000,
        frequency: 'MONTHLY',
        purchaseDay: 1,
        investmentInterval: 1,
        reinvestDividends: false,
        tradingFeeRate: 0,
        dividendTaxRate: 0,
      },
    })
  })

  it('N개월마다는 매달 주기에 개월 수를, 첫 매수일을 고르면 그 날짜와 그 날짜의 며칠을 보낸다', () => {
    const built = buildDcaRequest({ ...values, cadence: 'MONTHS', months: '3', firstPurchaseDate: '2023-01-31' }, 'AAPL', ctx)

    expect(built).toMatchObject({
      request: { frequency: 'MONTHLY', investmentInterval: 3, purchaseDay: 31, firstPurchaseDate: '2023-01-31' },
    })
  })

  it.each([
    ['WEEKLY', 'WEEKLY'],
    ['DAILY', 'DAILY'],
  ] as const)('%s 는 투자일 · 개월 수 없이 주기만 보낸다', (cadence, frequency) => {
    const built = buildDcaRequest({ ...values, cadence, months: '', firstPurchaseDate: '2023-01-04' }, 'AAPL', ctx)

    expect(built).toMatchObject({ request: { frequency, firstPurchaseDate: '2023-01-04' } })
    expect('request' in built && built.request).not.toHaveProperty('purchaseDay')
    expect('request' in built && built.request).not.toHaveProperty('investmentInterval')
  })

  it('매주는 첫 매수일(비우면 시작일)이 주말이면 다음 월요일을 첫 매수일로 보낸다', () => {
    expect(buildDcaRequest({ ...values, cadence: 'WEEKLY' }, 'AAPL', ctx)).toMatchObject({
      request: { frequency: 'WEEKLY', firstPurchaseDate: '2023-01-02' },
    })
    expect(buildDcaRequest({ ...values, cadence: 'WEEKLY', firstPurchaseDate: '2023-01-07' }, 'AAPL', ctx)).toMatchObject({
      request: { firstPurchaseDate: '2023-01-09' },
    })
  })

  it('매주인데 다음 월요일이 종료일 뒤면 첫 매수일을 옮기지 않는다', () => {
    const built = buildDcaRequest({ ...values, cadence: 'WEEKLY', startDate: '2023-01-07', endDate: '2023-01-08' }, 'AAPL', ctx)

    expect('request' in built && built.request).not.toHaveProperty('firstPurchaseDate')
  })

  it('수동 환율이면 두 환율을 숫자로 넣는다', () => {
    expect(buildDcaRequest({ ...values, fxMode: 'manual' }, 'AAPL', ctx)).toMatchObject({
      request: { purchaseFxRate: 1300, currentFxRate: 1350 },
    })
  })

  it.each([
    [{ amountPerPurchase: '0' }, '올바른 회당 금액을 입력해주세요.'],
    [{ cadence: 'MONTHS', months: '0' }, '개월 수는 1~12 사이의 정수로 입력해주세요.'],
    [{ cadence: 'MONTHS', months: '13' }, '개월 수는 1~12 사이의 정수로 입력해주세요.'],
    [{ cadence: 'MONTHS', months: '' }, '개월 수는 1~12 사이의 정수로 입력해주세요.'],
    [{ endDate: '2023-01-01' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
    [{ unit: 'SHARES', budget: '0' }, '예산은 비우거나 0보다 크게 입력해주세요.'],
    [{ firstPurchaseDate: '2026-10-09' }, '첫 매수일은 시작일과 종료일 사이로 골라주세요.'],
  ] as [Partial<DcaValues>, string][])('%o 는 막는다', (patch, error) => {
    expect(buildDcaRequest({ ...values, ...patch }, 'AAPL', ctx)).toEqual({ error })
  })

  it('주식 수 단위면 회당 금액 대신 회당 주식 수를 보낸다', () => {
    const built = buildDcaRequest({ ...values, unit: 'SHARES', shares: '2', amountPerPurchase: '' }, 'AAPL', ctx)

    expect(built).toMatchObject({ request: { sharesPerPurchase: 2, frequency: 'MONTHLY', purchaseDay: 1 } })
    expect('request' in built && built.request).not.toHaveProperty('amountPerPurchase')
  })

  it.each(['0', '2.5', 'abc', '1000001'])('회당 주식 수 %o 는 막는다', (shares) => {
    expect(buildDcaRequest({ ...values, unit: 'SHARES', shares }, 'AAPL', ctx)).toEqual({
      error: '회당 주식 수는 1 ~ 1,000,000 사이의 정수로 입력해주세요.',
    })
  })

  it('시작일이 바뀌면 고른 첫 매수일을 같은 며칠의 시작일 뒤 첫 날로 옮기고 매달 투자일은 고른 며칠로 보낸다', () => {
    const built = buildDcaRequest(
      { ...values, startDate: '2023-04-10', firstPurchaseDate: '2023-01-31', firstPickedStart: '2023-01-01' },
      'AAPL',
      ctx
    )

    expect(built).toMatchObject({ request: { startDate: '2023-04-10', firstPurchaseDate: '2023-04-30', purchaseDay: 31 } })
  })

  it('예산은 주식 수 단위일 때만 적립식 한도로 보낸다', () => {
    expect(buildDcaRequest({ ...values, unit: 'SHARES', budget: '5000000' }, 'AAPL', ctx)).toMatchObject({
      request: { sharesPerPurchase: 1, totalInvestmentLimit: 5000000 },
    })
    const byAmount = buildDcaRequest({ ...values, budget: '5000000' }, 'AAPL', ctx)
    expect('request' in byAmount && byAmount.request).not.toHaveProperty('totalInvestmentLimit')
  })

  it('매달이면 개월 칸 값은 보지 않는다', () => {
    expect(buildDcaRequest({ ...values, months: '' }, 'AAPL', ctx)).toMatchObject({ request: { investmentInterval: 1 } })
  })
})
