import { describe, it, expect } from 'vitest'
import { parseBand, parseBandPercent } from './maBand'

describe('parseBandPercent', () => {
  it('0 이상이고 요청 비율로 바꿔도 1 아래인 퍼센트만 받는다', () => {
    expect(parseBandPercent(' 2.5 ')).toBe(2.5)
    expect(parseBandPercent('0')).toBe(0)
    expect(parseBandPercent('99.999999')).toBe(99.999999)
    expect(parseBandPercent('99.9999999')).toBeUndefined()
    expect(parseBandPercent('100')).toBeUndefined()
    expect(parseBandPercent('')).toBeUndefined()
    expect(parseBandPercent('.')).toBeUndefined()
  })
})

describe('parseBand', () => {
  it('평균 기간과 위 · 아래 비율을 요청 값으로, 비율 하나라도 반올림해 1 이 되면 받지 않는다', () => {
    expect(parseBand('200', '2.5', '2.5')).toStrictEqual({ window: 200, upperRate: 0.025, lowerRate: 0.025 })
    expect(parseBand('200', '99.9999999', '2.5')).toBeUndefined()
    expect(parseBand('200', '2.5', '99.99999999')).toBeUndefined()
  })
})
