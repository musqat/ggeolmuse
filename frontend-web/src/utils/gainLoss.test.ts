import { describe, it, expect } from 'vitest'
import { hasValue, gainLossTone, gainLossClass, gainLossBoxClass } from './gainLoss'
import { formatSigned, formatKrw, formatUsd, formatPercent, formatBalanceAmount, formatFxRate } from './format'

describe('gainLoss', () => {
  it('null · undefined · NaN 은 값이 없는 것으로 본다', () => {
    expect(hasValue(0)).toBe(true)
    expect(hasValue(-3.5)).toBe(true)
    expect(hasValue(null)).toBe(false)
    expect(hasValue(undefined)).toBe(false)
    expect(hasValue(Number.NaN)).toBe(false)
  })

  it('양수는 이익, 음수는 손실, 0 은 중립, 값이 없으면 없음', () => {
    expect(gainLossTone(12.5)).toBe('gain')
    expect(gainLossTone(0)).toBe('flat')
    expect(gainLossTone(-0)).toBe('flat')
    expect(gainLossTone(-0.01)).toBe('loss')
    expect(gainLossTone(undefined)).toBe('none')
  })

  it('글자색은 초록 · 빨강 · 회색이고 0 은 색을 넣지 않는다', () => {
    expect(gainLossClass(1)).toBe('text-gain')
    expect(gainLossClass(-1)).toBe('text-loss')
    expect(gainLossClass(0)).toBe('')
    expect(gainLossClass(null)).toBe('text-tx-3')
  })

  it('아이콘 배경도 이익 · 손실로 나누고 0 이나 값이 없으면 기본 배경', () => {
    expect(gainLossBoxClass(5)).toBe('bg-gain/15')
    expect(gainLossBoxClass(-5)).toBe('bg-loss/15')
    expect(gainLossBoxClass(0)).toBe('bg-brand-bg')
    expect(gainLossBoxClass(undefined)).toBe('bg-brand-bg')
  })

  it('부호를 통화 기호 앞에 두고 형식에 맞춘다', () => {
    expect(formatSigned(150000, formatKrw)).toBe('+₩150,000')
    expect(formatSigned(-12.345, formatUsd)).toBe('-$12.35')
    expect(formatSigned(-123456, formatKrw)).toBe('-₩123,456')
    expect(formatSigned(-30.02, formatFxRate)).toBe('-₩30.02')
    expect(formatSigned(-2.21, formatPercent)).toBe('-2.21%')
  })

  it('0 은 부호 없이 보인다', () => {
    expect(formatSigned(0, formatKrw)).toBe('₩0')
    expect(formatSigned(-0, formatUsd)).toBe('$0.00')
    expect(formatSigned(0, formatPercent)).toBe('0.00%')
  })

  it('달러는 천 단위 쉼표를 넣어 센트까지 보인다', () => {
    expect(formatUsd(1234567.891)).toBe('$1,234,567.89')
    expect(formatUsd(0.5)).toBe('$0.50')
    expect(formatSigned(-5418.38, formatUsd)).toBe('-$5,418.38')
  })

  it('환율은 쉼표를 넣어 소수 둘째 자리까지 보인다', () => {
    expect(formatFxRate(1325.5)).toBe('₩1,325.50')
    expect(formatFxRate(1261.87)).toBe('₩1,261.87')
  })

  it('값이 없으면 - 만 보여준다', () => {
    expect(formatSigned(undefined, formatKrw)).toBe('-')
    expect(formatSigned(null, formatPercent)).toBe('-')
  })
})

describe('formatBalanceAmount', () => {
  it('부호는 통화 기호 앞에 두고 달러는 센트까지, 원은 원 단위까지 보인다', () => {
    expect(formatBalanceAmount(-12.3, 'USD')).toBe('-$12.30')
    expect(formatBalanceAmount(1234.5, 'USD')).toBe('$1,234.50')
    expect(formatBalanceAmount(1500, 'KRW')).toBe('₩1,500')
    expect(formatBalanceAmount(-1500, 'KRW')).toBe('-₩1,500')
  })

  it('반올림해 0 이 되는 마이너스는 부호 없이 보인다', () => {
    expect(formatBalanceAmount(-0.001, 'USD')).toBe('$0.00')
    expect(formatBalanceAmount(-0.4, 'KRW')).toBe('₩0')
  })
})
