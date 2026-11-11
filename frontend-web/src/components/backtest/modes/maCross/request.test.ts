import { describe, it, expect } from 'vitest'
import { buildMaCrossRequest, type MaCrossValues } from './request'

const ctx = { today: '2026-10-08' }

const values: MaCrossValues = {
  startDate: '2023-01-01',
  endDate: '',
  investment: '1000000',
  shortWindow: '20',
  longWindow: '60',
  reinvestDividends: false,
  tradingFeeRate: '0',
  dividendTax: false,
  capitalGainsTax: false,
  periodEnd: 'HOLD',
  fxMode: 'auto',
  manualPurchaseFxRate: '1300',
  manualCurrentFxRate: '1350',
}

describe('buildMaCrossRequest', () => {
  it('원금 · 두 기간을 숫자로 싣고 빈 종료일은 오늘로 보낸다', () => {
    expect(buildMaCrossRequest(values, 'AAPL', ctx)).toStrictEqual({
      request: {
        symbol: 'AAPL',
        startDate: '2023-01-01',
        endDate: '2026-10-08',
        investmentAmount: 1000000,
        shortWindow: 20,
        longWindow: 60,
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
      buildMaCrossRequest({ ...values, reinvestDividends: true, periodEnd: 'SELL_AT_END', fxMode: 'manual' }, 'AAPL', ctx)
    ).toMatchObject({
      request: { reinvestDividends: false, periodEnd: 'SELL_AT_END', purchaseFxRate: 1300, currentFxRate: 1350 },
    })
  })

  it('따로 고른 신호 종목은 signalSymbol 로 싣는다', () => {
    expect(buildMaCrossRequest({ ...values, signalSymbol: '^GSPC' }, 'SPY', ctx)).toMatchObject({
      request: { symbol: 'SPY', signalSymbol: '^GSPC' },
    })
  })

  it('부분 익절 단계는 둔 때만 수익률 · 팔 퍼센트를 비율로 싣는다', () => {
    expect(buildMaCrossRequest({ ...values, partialTakeProfits: [{ gain: '12.5', sell: '33.3' }] }, 'SPY', ctx)).toMatchObject({
      request: { partialTakeProfits: [{ gainRate: 0.125, sellRate: 0.333 }] },
    })
    const off = buildMaCrossRequest({ ...values, partialTakeProfits: [] }, 'SPY', ctx)
    expect('request' in off && 'partialTakeProfits' in off.request).toBe(false)
  })

  it('트레일링 스탑은 적은 때만 하락 · 팔 퍼센트를 비율로 싣는다', () => {
    expect(buildMaCrossRequest({ ...values, trailingStop: '12.5', trailingSell: '100' }, 'SPY', ctx)).toMatchObject({
      request: { trailingStopRate: 0.125, trailingSellRate: 1 },
    })
    const off = buildMaCrossRequest(values, 'SPY', ctx)
    expect('request' in off && 'trailingStopRate' in off.request).toBe(false)
    expect('request' in off && 'trailingSellRate' in off.request).toBe(false)
  })

  it('짧은 기간 2 · 긴 기간 250 까지 보낸다', () => {
    expect(buildMaCrossRequest({ ...values, shortWindow: '2', longWindow: '250' }, 'AAPL', ctx)).toMatchObject({
      request: { shortWindow: 2, longWindow: 250 },
    })
  })

  it.each([
    [{ investment: '' }, '올바른 원금을 입력해주세요.'],
    [{ investment: '0' }, '올바른 원금을 입력해주세요.'],
    [{ shortWindow: '' }, '이동평균 기간은 2 ~ 250 사이 정수로, 짧은 기간을 긴 기간보다 짧게 입력해주세요.'],
    [{ shortWindow: '1' }, '이동평균 기간은 2 ~ 250 사이 정수로, 짧은 기간을 긴 기간보다 짧게 입력해주세요.'],
    [{ longWindow: '251' }, '이동평균 기간은 2 ~ 250 사이 정수로, 짧은 기간을 긴 기간보다 짧게 입력해주세요.'],
    [{ shortWindow: '20.5' }, '이동평균 기간은 2 ~ 250 사이 정수로, 짧은 기간을 긴 기간보다 짧게 입력해주세요.'],
    [{ shortWindow: '60' }, '이동평균 기간은 2 ~ 250 사이 정수로, 짧은 기간을 긴 기간보다 짧게 입력해주세요.'],
    [{ shortWindow: '90' }, '이동평균 기간은 2 ~ 250 사이 정수로, 짧은 기간을 긴 기간보다 짧게 입력해주세요.'],
    [{ trailingStop: '0' }, '고점 대비 하락 비율은 0보다 크고 100보다 작아야 합니다.'],
    [{ trailingStop: '10', trailingSell: '101' }, '고점 대비 하락 때 팔 비율은 0보다 크고 100 이하여야 합니다.'],
    [{ partialTakeProfits: [{ gain: '20', sell: '0' }] }, '부분 익절 1단계 팔 비율은 0보다 크고 100 이하여야 합니다.'],
    [{ endDate: '2022-01-01' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
    [{ endDate: '2023-01-01' }, '시작일은 종료일보다 빠른 날짜여야 합니다.'],
  ])('%o 는 막는다', (patch, error) => {
    expect(buildMaCrossRequest({ ...values, ...patch }, 'AAPL', ctx)).toEqual({ error })
  })
})
