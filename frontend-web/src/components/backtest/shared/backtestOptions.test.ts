import { describe, it, expect } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { DEFAULT_OPTIONS, optionValues, toOptionFields, useBacktestOptions } from './backtestOptions'

describe('세부 설정 값', () => {
  it('기본값은 배당세 15% · 배당 현금 · 수수료 0% · 환율 자동이고 직접 입력 환율은 비움', () => {
    expect(DEFAULT_OPTIONS).toEqual({
      reinvestDividends: false,
      tradingFeeRate: '0',
      dividendTax: true,
      fxMode: 'auto',
      manualPurchaseFxRate: '',
      manualCurrentFxRate: '',
    })
    expect(toOptionFields(DEFAULT_OPTIONS)).toStrictEqual({ reinvestDividends: false, tradingFeeRate: 0, dividendTaxRate: 0.15 })
  })

  it('수수료 칸을 비우면 0, 직접 입력 환율은 적은 쪽만 보내고 빈 쪽은 그 날짜 시장 환율', () => {
    expect(toOptionFields({ ...DEFAULT_OPTIONS, tradingFeeRate: '' })).toMatchObject({ tradingFeeRate: 0 })
    expect(toOptionFields({ ...DEFAULT_OPTIONS, fxMode: 'manual', manualPurchaseFxRate: '1130' })).toStrictEqual({
      reinvestDividends: false,
      tradingFeeRate: 0,
      dividendTaxRate: 0.15,
      purchaseFxRate: 1130,
    })
    expect(toOptionFields({ ...DEFAULT_OPTIONS, fxMode: 'manual', manualCurrentFxRate: '1450' })).not.toHaveProperty('purchaseFxRate')
    expect(toOptionFields({ ...DEFAULT_OPTIONS, fxMode: 'auto', manualPurchaseFxRate: '1130' })).not.toHaveProperty('purchaseFxRate')
  })

  it('값만 뽑고, 저장한 값을 넣으면 없거나 모양이 다른 칸은 기본값으로 둔다', () => {
    const { result } = renderHook(() => useBacktestOptions())

    act(() => result.current.load({ tradingFeeRate: '0.25', dividendTax: false, fxMode: 'manual', reinvestDividends: 'yes' }))
    expect(optionValues(result.current)).toEqual({ ...DEFAULT_OPTIONS, tradingFeeRate: '0.25', dividendTax: false, fxMode: 'manual' })
    act(() => result.current.load({ fxMode: 'other' }))
    expect(result.current.fxMode).toBe('auto')
  })
})
