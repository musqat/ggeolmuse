import { describe, it, expect } from 'vitest'
import { splitLabel } from './split'

describe('splitLabel', () => {
  it('1 보다 큰 비율은 몇 주가 몇 주로 늘었는지와 분할', () => {
    expect(splitLabel(4)).toBe('1주→4주 분할')
    expect(splitLabel(10)).toBe('1주→10주 분할')
  })

  it('분수 비율은 가장 작은 정수 쌍', () => {
    expect(splitLabel(1.5)).toBe('2주→3주 분할')
  })

  it('1 보다 작은 비율은 병합(소수 8자리로 잘린 1/3 포함)', () => {
    expect(splitLabel(0.1)).toBe('10주→1주 병합')
    expect(splitLabel(0.33333333)).toBe('3주→1주 병합')
  })

  it('100 이하 정수 쌍이 없으면 소수 넷째 자리까지', () => {
    expect(splitLabel(1.73205081)).toBe('1주→1.7321주 분할')
  })
})
