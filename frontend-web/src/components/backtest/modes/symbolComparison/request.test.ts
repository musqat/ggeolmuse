import { describe, it, expect } from 'vitest'
import { buildSymbolComparisonRequest, type SymbolComparisonValues } from './request'

const ctx = { userId: 'u@x.com', today: '2026-10-08' }

const values: SymbolComparisonValues = {
  symbols: ['AAPL', 'MSFT'],
  purchaseDate: '2023-01-01',
  saleDate: '',
  investment: '1000000',
  reinvestDividends: false,
  tradingFeeRate: '0',
  dividendTax: false,
  fxMode: 'auto',
  manualPurchaseFxRate: '1300',
  manualCurrentFxRate: '1350',
}

describe('buildSymbolComparisonRequest', () => {
  it('매도일이 비면 오늘로 채운다', () => {
    expect(buildSymbolComparisonRequest(values, ctx)).toEqual({
      request: {
        symbols: ['AAPL', 'MSFT'],
        startDate: '2023-01-01',
        endDate: '2026-10-08',
        investmentAmount: 1000000,
        reinvestDividends: false,
        tradingFeeRate: 0,
        dividendTaxRate: 0,
        userId: 'u@x.com',
      },
    })
  })

  it('수동 환율이면 두 환율을 숫자로 넣는다', () => {
    expect(buildSymbolComparisonRequest({ ...values, fxMode: 'manual' }, ctx)).toMatchObject({
      request: { purchaseFxRate: 1300, currentFxRate: 1350 },
    })
  })

  it.each([
    [{ symbols: ['AAPL'] }, '최소 2개 이상의 종목을 선택해주세요.'],
    [{ investment: '' }, '올바른 투자 금액을 입력해주세요.'],
    [{ saleDate: '2023-01-01' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
    [{ purchaseDate: '2026-10-08' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
  ])('%o 는 막는다', (patch, error) => {
    expect(buildSymbolComparisonRequest({ ...values, ...patch }, ctx)).toEqual({ error })
  })
})
