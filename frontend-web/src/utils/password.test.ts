import { describe, it, expect } from 'vitest'
import { PASSWORD_RULE } from './password'

describe('PASSWORD_RULE', () => {
  it.each(['abcd123!', 'abcd123#', 'Abc-1234', 'a1!' + 'x'.repeat(97)])('영문 · 숫자 · 특수문자가 있고 8 ~ 100자면 맞다: %s', (password) => {
    expect(PASSWORD_RULE.test(password)).toBe(true)
  })

  it.each([
    ['짧음', 'ab1!'],
    ['숫자 없음', 'abcdefg!'],
    ['영문 없음', '1234567!'],
    ['특수문자 없음', 'abcd1234'],
    ['101자', 'a1!' + 'x'.repeat(98)],
  ])('%s이면 안 맞다', (_, password) => {
    expect(PASSWORD_RULE.test(password)).toBe(false)
  })
})
