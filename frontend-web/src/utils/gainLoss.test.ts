import { describe, it, expect } from 'vitest'
import {
  hasValue,
  gainLossTone,
  gainLossClass,
  gainLossBoxClass,
  formatSigned,
  formatKrw,
  formatUsd,
  formatPercent,
} from './gainLoss'

describe('gainLoss', () => {
  it('null · undefined · NaN 은 값이 없는 것으로 본다', () => {
    expect(hasValue(0)).toBe(true)
    expect(hasValue(-3.5)).toBe(true)
    expect(hasValue(null)).toBe(false)
    expect(hasValue(undefined)).toBe(false)
    expect(hasValue(Number.NaN)).toBe(false)
  })

  it('0 이상은 이익, 음수는 손실, 값이 없으면 없음', () => {
    expect(gainLossTone(12.5)).toBe('gain')
    expect(gainLossTone(0)).toBe('gain')
    expect(gainLossTone(-0.01)).toBe('loss')
    expect(gainLossTone(undefined)).toBe('none')
  })

  it('글자색은 초록 · 빨강 · 회색', () => {
    expect(gainLossClass(1)).toBe('text-green-600')
    expect(gainLossClass(-1)).toBe('text-red-600')
    expect(gainLossClass(null)).toBe('text-tx-3')
  })

  it('아이콘 배경도 이익 · 손실로 나누고 값이 없으면 기본 배경', () => {
    expect(gainLossBoxClass(5)).toBe('bg-green-500/15')
    expect(gainLossBoxClass(-5)).toBe('bg-red-500/15')
    expect(gainLossBoxClass(undefined)).toBe('bg-brand-bg')
  })

  it('부호를 붙여 형식에 맞춘다', () => {
    expect(formatSigned(150000, formatKrw)).toBe('+₩150,000')
    // 음수는 지금 화면처럼 통화 기호 뒤에 부호가 붙는다
    expect(formatSigned(-12.345, formatUsd)).toBe('$-12.35')
    expect(formatSigned(0, formatPercent)).toBe('+0.00%')
  })

  it('값이 없으면 - 만 보여준다', () => {
    expect(formatSigned(undefined, formatKrw)).toBe('-')
    expect(formatSigned(null, formatPercent)).toBe('-')
  })
})
