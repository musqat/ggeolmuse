import { describe, it, expect } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { DEFAULT_OPTIONS, optionValues, taxSaleFields, toOptionFields, useBacktestOptions, yearEndFields } from './backtestOptions'

describe('세부 설정 값', () => {
  it('기본값은 끝까지 보유 · 배당세 15% · 양도세 22%(현금이 생길 때 냄 · 연말 정리 끔) · 배당 현금 · 수수료 0% · 환율 자동이고 직접 입력 환율은 비움', () => {
    expect(DEFAULT_OPTIONS).toEqual({
      periodEnd: 'HOLD',
      reinvestDividends: false,
      tradingFeeRate: '0',
      dividendTax: true,
      capitalGainsTax: true,
      taxSale: 'NONE',
      yearEndHarvest: false,
      fxMode: 'auto',
      manualPurchaseFxRate: '',
      manualCurrentFxRate: '',
    })
    expect(toOptionFields(DEFAULT_OPTIONS)).toStrictEqual({
      reinvestDividends: false,
      tradingFeeRate: 0,
      dividendTaxRate: 0.15,
      capitalGainsTax: true,
      periodEnd: 'HOLD',
    })
  })

  it('양도세를 끄면 false, 마지막 거래일에 팔기는 SELL_AT_END 로 보낸다', () => {
    expect(toOptionFields({ ...DEFAULT_OPTIONS, capitalGainsTax: false, periodEnd: 'SELL_AT_END' })).toMatchObject({
      capitalGainsTax: false,
      periodEnd: 'SELL_AT_END',
    })
  })

  it('수수료 칸을 비우면 0, 직접 입력 환율은 적은 쪽만 보내고 빈 쪽은 그 날짜 시장 환율', () => {
    expect(toOptionFields({ ...DEFAULT_OPTIONS, tradingFeeRate: '' })).toMatchObject({ tradingFeeRate: 0 })
    expect(toOptionFields({ ...DEFAULT_OPTIONS, fxMode: 'manual', manualPurchaseFxRate: '1130' })).toStrictEqual({
      reinvestDividends: false,
      tradingFeeRate: 0,
      dividendTaxRate: 0.15,
      capitalGainsTax: true,
      periodEnd: 'HOLD',
      purchaseFxRate: 1130,
    })
    expect(toOptionFields({ ...DEFAULT_OPTIONS, fxMode: 'manual', manualCurrentFxRate: '1450' })).not.toHaveProperty('purchaseFxRate')
    expect(toOptionFields({ ...DEFAULT_OPTIONS, fxMode: 'auto', manualPurchaseFxRate: '1130' })).not.toHaveProperty('purchaseFxRate')
  })

  it('양도세 판매 설정은 양도세를 켜고 팔아 낼 때만 taxSale 로 싣고 다른 옵션 칸에는 넣지 않는다', () => {
    expect(taxSaleFields({ ...DEFAULT_OPTIONS, taxSale: 'SELL' })).toStrictEqual({ taxSale: 'SELL' })
    expect(taxSaleFields(DEFAULT_OPTIONS)).toStrictEqual({})
    expect(taxSaleFields({ ...DEFAULT_OPTIONS, taxSale: 'SELL', capitalGainsTax: false })).toStrictEqual({})
    const selling = { ...DEFAULT_OPTIONS, taxSale: 'SELL' as const }
    expect(toOptionFields(selling)).not.toHaveProperty('taxSale')
  })

  it('연말 정리는 양도세를 켜고 연말 정리를 켰을 때만 yearEndHarvest 로 싣고 다른 옵션 칸에는 넣지 않는다', () => {
    expect(yearEndFields({ ...DEFAULT_OPTIONS, yearEndHarvest: true })).toStrictEqual({ yearEndHarvest: true })
    expect(yearEndFields(DEFAULT_OPTIONS)).toStrictEqual({})
    expect(yearEndFields({ ...DEFAULT_OPTIONS, yearEndHarvest: true, capitalGainsTax: false })).toStrictEqual({})
    const harvesting = { ...DEFAULT_OPTIONS, yearEndHarvest: true }
    expect(toOptionFields(harvesting)).not.toHaveProperty('yearEndHarvest')
  })

  it('값만 뽑고, 저장한 값을 넣으면 없거나 모양이 다른 칸은 기본값으로 둔다', () => {
    const { result } = renderHook(() => useBacktestOptions())

    act(() => result.current.load({ tradingFeeRate: '0.25', dividendTax: false, fxMode: 'manual', reinvestDividends: 'yes' }))
    expect(optionValues(result.current)).toEqual({ ...DEFAULT_OPTIONS, tradingFeeRate: '0.25', dividendTax: false, fxMode: 'manual' })
    act(() => result.current.load({ fxMode: 'other', periodEnd: 'SELL', capitalGainsTax: 'no' }))
    expect(result.current).toMatchObject({ fxMode: 'auto', periodEnd: 'HOLD', capitalGainsTax: true })
    act(() => result.current.load({ periodEnd: 'SELL_AT_END', capitalGainsTax: false, taxSale: 'SELL' }))
    expect(optionValues(result.current)).toEqual({ ...DEFAULT_OPTIONS, periodEnd: 'SELL_AT_END', capitalGainsTax: false, taxSale: 'SELL' })
    act(() => result.current.load({ taxSale: 'LOSS_FIRST' }))
    expect(result.current.taxSale).toBe('NONE')
  })
})
