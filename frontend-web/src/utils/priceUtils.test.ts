import { describe, it, expect } from 'vitest'
import {
  calculateExecutionPrice,
  validatePriceRange,
  formatPrice,
  calculateTotalAmount,
  type OHLCData,
} from './priceUtils'

const ohlc: OHLCData = { open: 100, high: 120, low: 90, close: 110 }

describe('calculateExecutionPrice', () => {
  it('주문 유형에 맞는 가격을 고른다', () => {
    expect(calculateExecutionPrice('open', ohlc)).toBe(100)
    expect(calculateExecutionPrice('high', ohlc)).toBe(120)
    expect(calculateExecutionPrice('low', ohlc)).toBe(90)
    expect(calculateExecutionPrice('close', ohlc)).toBe(110)
  })

  it('지정가는 넘겨준 값을 쓴다', () => {
    expect(calculateExecutionPrice('limit', ohlc, 105)).toBe(105)
  })

  it('지정가인데 값이 없으면 0', () => {
    // 0 반환 — 호출부에서 걸러야 하는 값
    expect(calculateExecutionPrice('limit', ohlc)).toBe(0)
  })

  it('OHLC가 없으면 0', () => {
    expect(calculateExecutionPrice('close', null)).toBe(0)
  })
})

describe('validatePriceRange', () => {
  it('날짜를 안 고르면 안내 문구를 준다', () => {
    const result = validatePriceRange(100, null)
    expect(result.isValid).toBe(false)
    expect(result.message).toBe('날짜를 먼저 선택해주세요.')
  })

  it('범위 안이면 통과', () => {
    expect(validatePriceRange(100, ohlc)).toEqual({ isValid: true })
  })

  it('저가와 고가는 경계 포함으로 통과', () => {
    // 경계값 포함 여부
    expect(validatePriceRange(90, ohlc).isValid).toBe(true)
    expect(validatePriceRange(120, ohlc).isValid).toBe(true)
  })

  it('저가보다 낮으면 막고 범위를 알려준다', () => {
    const result = validatePriceRange(89.99, ohlc)
    expect(result.isValid).toBe(false)
    expect(result.message).toContain('$90.00 ~ $120.00')
    expect(result.message).toContain('$89.99')
  })

  it('고가보다 높으면 막는다', () => {
    expect(validatePriceRange(120.01, ohlc).isValid).toBe(false)
  })
})

describe('formatPrice', () => {
  it('달러 기호와 소수 두 자리', () => {
    expect(formatPrice(10)).toBe('$10.00')
    expect(formatPrice(10.456)).toBe('$10.46')
  })

  it('자릿수를 지정할 수 있다', () => {
    expect(formatPrice(10.4567, 3)).toBe('$10.457')
    expect(formatPrice(10.4, 0)).toBe('$10')
  })
})

describe('calculateTotalAmount', () => {
  it('가격 곱하기 수량', () => {
    expect(calculateTotalAmount(110, 3)).toBe(330)
    expect(calculateTotalAmount(110, 0)).toBe(0)
  })
})
