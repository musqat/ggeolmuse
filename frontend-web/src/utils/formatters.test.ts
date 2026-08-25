import { describe, it, expect } from 'vitest'
import {
  formatPrice,
  formatPriceShort,
  formatChange,
  formatPercentage,
  formatVolume,
  formatMarketCap,
} from './formatters'

describe('formatPrice', () => {
  it('소수 네 자리로 자른다', () => {
    expect(formatPrice(1.23456)).toBe('1.2346')
  })

  it('값이 없거나 0이면 N/A', () => {
    expect(formatPrice(undefined)).toBe('N/A')
    expect(formatPrice(0)).toBe('N/A')
  })
})

describe('formatPriceShort', () => {
  it('소수 두 자리', () => {
    expect(formatPriceShort(1.239)).toBe('1.24')
  })
})

describe('formatChange', () => {
  it('오르면 + 를 붙인다', () => {
    expect(formatChange(1.5)).toBe('+1.50')
  })

  it('내리면 - 가 그대로 남는다', () => {
    expect(formatChange(-1.5)).toBe('-1.50')
  })

  it('보합 0도 숫자로 취급한다', () => {
    // formatPrice 와 달리 0 은 N/A 로 안 감
    expect(formatChange(0)).toBe('+0.00')
  })

  it('값이 없으면 N/A', () => {
    expect(formatChange(undefined)).toBe('N/A')
  })
})

describe('formatPercentage', () => {
  it('부호와 % 를 붙인다', () => {
    expect(formatPercentage(2.345)).toBe('+2.35%')
    expect(formatPercentage(-2.345)).toBe('-2.35%')
    expect(formatPercentage(0)).toBe('+0.00%')
  })

  it('값이 없으면 N/A', () => {
    expect(formatPercentage(undefined)).toBe('N/A')
  })
})

describe('formatVolume', () => {
  it('백만 이상은 M', () => {
    expect(formatVolume(1_500_000)).toBe('1.5M')
  })

  it('천 이상은 K', () => {
    expect(formatVolume(1_500)).toBe('1.5K')
  })

  it('천 미만은 콤마만', () => {
    expect(formatVolume(999)).toBe('999')
  })

  it('경계 근처에서 단위가 넘어간다', () => {
    expect(formatVolume(1_000)).toBe('1.0K')
    expect(formatVolume(1_000_000)).toBe('1.0M')
    // 999,999 는 반올림돼서 1000.0K. M 으로 안 넘어감
    expect(formatVolume(999_999)).toBe('1000.0K')
  })
})

describe('formatMarketCap', () => {
  it('단위별로 접미사를 붙인다', () => {
    expect(formatMarketCap(2_500_000_000_000)).toBe('$2.50T')
    expect(formatMarketCap(3_200_000_000)).toBe('$3.20B')
    expect(formatMarketCap(4_100_000)).toBe('$4.10M')
    expect(formatMarketCap(5_600)).toBe('$5.60K')
  })

  it('천 미만은 그대로 쓴다', () => {
    expect(formatMarketCap(999)).toBe('$999')
  })

  it('값이 없거나 0이면 N/A', () => {
    expect(formatMarketCap(undefined)).toBe('N/A')
    expect(formatMarketCap(0)).toBe('N/A')
  })
})
