import { describe, it, expect } from 'vitest'
import { buildDcaRequest, type DcaValues } from './request'

const ctx = { userId: 'u@x.com', today: '2026-10-08' }

const values: DcaValues = {
  startDate: '2023-01-01',
  endDate: '',
  monthlyAmount: '100000',
  purchaseDay: '15',
  investmentInterval: '1',
  reinvestDividends: false,
  tradingFeeRate: '0',
  dividendTax: false,
  fxMode: 'auto',
  manualPurchaseFxRate: '1300',
  manualCurrentFxRate: '1350',
}

describe('buildDcaRequest', () => {
  it('종료일이 비면 오늘로 채운다', () => {
    expect(buildDcaRequest(values, 'AAPL', ctx)).toEqual({
      request: {
        symbol: 'AAPL',
        startDate: '2023-01-01',
        endDate: '2026-10-08',
        monthlyAmount: 100000,
        purchaseDay: 15,
        investmentInterval: 1,
        reinvestDividends: false,
        tradingFeeRate: 0,
        dividendTaxRate: 0,
        userId: 'u@x.com',
      },
    })
  })

  it('수동 환율이면 두 환율을 숫자로 넣는다', () => {
    expect(buildDcaRequest({ ...values, fxMode: 'manual' }, 'AAPL', ctx)).toMatchObject({
      request: { purchaseFxRate: 1300, currentFxRate: 1350 },
    })
  })

  it.each([
    [{ monthlyAmount: '0' }, '올바른 월 투자 금액을 입력해주세요.'],
    [{ purchaseDay: '29' }, '투자일은 1~28 사이여야 합니다.'],
    [{ purchaseDay: 'x' }, '투자일은 1~28 사이여야 합니다.'],
    [{ endDate: '2023-01-01' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
  ])('%o 는 막는다', (patch, error) => {
    expect(buildDcaRequest({ ...values, ...patch }, 'AAPL', ctx)).toEqual({ error })
  })
})
