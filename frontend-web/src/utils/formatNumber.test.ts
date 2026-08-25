import { describe, it, expect } from 'vitest'
import {
  formatNumberWithCommas,
  parseNumberFromFormatted,
  formatToKoreanWon,
} from './formatNumber'

describe('formatNumberWithCommas', () => {
  it('천단위로 콤마를 넣는다', () => {
    expect(formatNumberWithCommas('1234567')).toBe('1,234,567')
    expect(formatNumberWithCommas(1234567)).toBe('1,234,567')
  })

  it('이미 들어 있는 콤마는 지우고 다시 넣는다', () => {
    // 콤마 중복 여부
    expect(formatNumberWithCommas('1,234,567')).toBe('1,234,567')
  })

  it('소수부는 콤마를 넣지 않고 그대로 둔다', () => {
    expect(formatNumberWithCommas('1234.56')).toBe('1,234.56')
  })

  it('소수부 앞자리 0을 살린다', () => {
    // 소수부 문자열 유지 (.05 → .5 로 뭉개지는지)
    expect(formatNumberWithCommas('1234.05')).toBe('1,234.05')
  })

  it('소수점을 막 찍은 상태를 유지한다', () => {
    // 입력 중간 상태
    expect(formatNumberWithCommas('1234.')).toBe('1,234.')
  })

  it('점만 있으면 0을 붙여 돌려준다', () => {
    expect(formatNumberWithCommas('.')).toBe('0.')
  })

  it('음수도 처리한다', () => {
    expect(formatNumberWithCommas('-1234')).toBe('-1,234')
  })

  it('빈 값과 숫자가 아닌 값은 빈 문자열', () => {
    expect(formatNumberWithCommas('')).toBe('')
    expect(formatNumberWithCommas('abc')).toBe('')
  })

  it('0은 0으로 남는다', () => {
    expect(formatNumberWithCommas('0')).toBe('0')
  })
})

describe('parseNumberFromFormatted', () => {
  it('콤마를 걷어내고 숫자로 만든다', () => {
    expect(parseNumberFromFormatted('1,234,567')).toBe(1234567)
    expect(parseNumberFromFormatted('1,234.56')).toBe(1234.56)
  })

  it('빈 값과 숫자가 아닌 값은 0', () => {
    expect(parseNumberFromFormatted('')).toBe(0)
    expect(parseNumberFromFormatted('abc')).toBe(0)
  })
})

describe('formatToKoreanWon', () => {
  it('0은 0원', () => {
    expect(formatToKoreanWon(0)).toBe('0원')
  })

  it('만 단위', () => {
    expect(formatToKoreanWon(10000)).toBe('1만원')
    expect(formatToKoreanWon(1000000)).toBe('100만원')
  })

  it('억 단위', () => {
    expect(formatToKoreanWon(100000000)).toBe('1억원')
  })

  it('억과 만이 같이 있으면 사이를 띄운다', () => {
    expect(formatToKoreanWon(123450000)).toBe('1억 2345만원')
  })

  it('억이나 만이 있으면 만 미만은 버린다', () => {
    // 6789원 절삭
    expect(formatToKoreanWon(123456789)).toBe('1억 2345만원')
  })

  it('만 미만만 있으면 그 값을 그대로 쓴다', () => {
    expect(formatToKoreanWon(5000)).toBe('5000원')
  })

  it('콤마 붙은 문자열도 받는다', () => {
    expect(formatToKoreanWon('1,000,000')).toBe('100만원')
  })
})
