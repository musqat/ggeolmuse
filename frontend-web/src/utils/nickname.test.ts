import { describe, it, expect } from 'vitest'
import { NICKNAME_RULE } from './nickname'

describe('NICKNAME_RULE', () => {
  it.each(['투자자', 'ab', 'Investor_01', '가'.repeat(20)])('2 ~ 20자의 영문 · 숫자 · 한글 · _ 면 맞다: %s', (nickname) => {
    expect(NICKNAME_RULE.test(nickname)).toBe(true)
  })

  it.each([
    ['한 글자', 'a'],
    ['21자', '가'.repeat(21)],
    ['띄어쓰기', '투자 자'],
    ['기호', '투자자!'],
    ['빈 값', ''],
  ])('%s면 안 맞다', (_, nickname) => {
    expect(NICKNAME_RULE.test(nickname)).toBe(false)
  })
})
