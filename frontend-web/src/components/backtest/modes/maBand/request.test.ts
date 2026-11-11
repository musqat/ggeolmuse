import { describe, it, expect } from 'vitest'
import { buildMaBandRequest, type MaBandValues } from './request'

const ctx = { today: '2026-10-08' }

const values: MaBandValues = {
  startDate: '2023-01-01',
  endDate: '',
  investment: '1000000',
  bandWindow: '200',
  upperPercent: '2.5',
  lowerPercent: '2.5',
  reinvestDividends: false,
  tradingFeeRate: '0',
  dividendTax: false,
  capitalGainsTax: false,
  periodEnd: 'HOLD',
  fxMode: 'auto',
  manualPurchaseFxRate: '1300',
  manualCurrentFxRate: '1350',
}

const BAND_ERROR = '밴드 기간은 2 ~ 250 사이 정수로, 위 · 아래 비율은 0 이상 100 미만으로 입력해주세요.'

describe('buildMaBandRequest', () => {
  it('원금 · 평균 기간을 숫자로, 위 · 아래 퍼센트를 소수 비율로 싣고 빈 종료일은 오늘로 보낸다', () => {
    expect(buildMaBandRequest(values, 'SPY', ctx)).toStrictEqual({
      request: {
        symbol: 'SPY',
        startDate: '2023-01-01',
        endDate: '2026-10-08',
        investmentAmount: 1000000,
        bandWindow: 200,
        upperRate: 0.025,
        lowerRate: 0.025,
        reinvestDividends: false,
        tradingFeeRate: 0,
        dividendTaxRate: 0,
        capitalGainsTax: false,
        periodEnd: 'HOLD',
      },
    })
  })

  it('배당 재투자는 골라도 false 로, 기간 끝 매도 · 수동 환율은 고른 대로 싣는다', () => {
    expect(
      buildMaBandRequest({ ...values, reinvestDividends: true, periodEnd: 'SELL_AT_END', fxMode: 'manual' }, 'SPY', ctx)
    ).toMatchObject({
      request: { reinvestDividends: false, periodEnd: 'SELL_AT_END', purchaseFxRate: 1300, currentFxRate: 1350 },
    })
  })

  it('평균 기간 2 · 250 과 비율 0 · 99.9 까지 보낸다', () => {
    expect(buildMaBandRequest({ ...values, bandWindow: '2', upperPercent: '0', lowerPercent: '99.9' }, 'SPY', ctx)).toMatchObject({
      request: { bandWindow: 2, upperRate: 0, lowerRate: 0.999 },
    })
    expect(buildMaBandRequest({ ...values, bandWindow: '250', upperPercent: '3.5' }, 'SPY', ctx)).toMatchObject({
      request: { bandWindow: 250, upperRate: 0.035 },
    })
  })

  it.each([
    [{ investment: '' }, '올바른 원금을 입력해주세요.'],
    [{ investment: '0' }, '올바른 원금을 입력해주세요.'],
    [{ bandWindow: '' }, BAND_ERROR],
    [{ bandWindow: '1' }, BAND_ERROR],
    [{ bandWindow: '251' }, BAND_ERROR],
    [{ bandWindow: '20.5' }, BAND_ERROR],
    [{ upperPercent: '' }, BAND_ERROR],
    [{ upperPercent: '100' }, BAND_ERROR],
    [{ lowerPercent: '' }, BAND_ERROR],
    [{ lowerPercent: '120' }, BAND_ERROR],
    [{ lowerPercent: '.' }, BAND_ERROR],
    [{ endDate: '2022-01-01' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
    [{ endDate: '2023-01-01' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
  ])('%o 는 막는다', (patch, error) => {
    expect(buildMaBandRequest({ ...values, ...patch }, 'SPY', ctx)).toEqual({ error })
  })
})
