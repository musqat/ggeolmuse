import { describe, it, expect } from 'vitest'
import { isGain, signPrefix, gainLossClass, gainLossBoxClass } from './gainLoss'

describe('gainLoss', () => {
  it('0 이상이면 이익으로 본다', () => {
    expect(isGain(0)).toBe(true)
    expect(isGain(12.5)).toBe(true)
    expect(isGain(-0.01)).toBe(false)
  })

  it('값이 없으면 0 으로 본다', () => {
    expect(isGain(undefined)).toBe(true)
    expect(isGain(null)).toBe(true)
    expect(isGain(Number.NaN)).toBe(true)
  })

  it('이익이면 + 를 붙이고 손실이면 비운다', () => {
    expect(signPrefix(100)).toBe('+')
    expect(signPrefix(-100)).toBe('')
  })

  it('이익은 초록, 손실은 빨강', () => {
    expect(gainLossClass(1)).toBe('text-green-600')
    expect(gainLossClass(-1)).toBe('text-red-600')
  })

  it('아이콘 배경도 이익·손실로 나눈다', () => {
    expect(gainLossBoxClass(5)).toBe('bg-green-500/15')
    expect(gainLossBoxClass(-5)).toBe('bg-red-500/15')
  })
})
