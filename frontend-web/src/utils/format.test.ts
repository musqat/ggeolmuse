import { describe, it, expect } from 'vitest'
import {
  formatNumberWithCommas,
  parseNumberFromFormatted,
  formatToKoreanWon,
  formatReadableWon,
  formatMarketCap,
  formatUsd,
  formatKrw,
  formatQuantity,
  formatShares,
  formatSigned,
  formatPercent,
} from './format'

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
    // 소수부 문자열 유지 (.05 가 .5 로 바뀌지 않음)
    expect(formatNumberWithCommas('1234.05')).toBe('1,234.05')
  })

  it('소수점을 막 찍은 상태를 유지한다', () => {
    // 입력 중간 상태
    expect(formatNumberWithCommas('1234.')).toBe('1,234.')
  })

  it('점만 있으면 앞에 0을 더해 돌려준다', () => {
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

  it('콤마가 든 문자열도 받는다', () => {
    expect(formatToKoreanWon('1,000,000')).toBe('100만원')
  })
})

describe('formatReadableWon', () => {
  it('억 · 만 · 그 아래를 모두 쓰고 천 단위 쉼표를 둔다', () => {
    expect(formatReadableWon(120000000)).toBe('1억 2,000만원')
    expect(formatReadableWon(1234567)).toBe('123만 4,567원')
    expect(formatReadableWon(9000)).toBe('9,000원')
    expect(formatReadableWon(1000000)).toBe('100만원')
  })

  it('소수는 반올림하고 0 · 음수 · 숫자가 아니면 0원', () => {
    expect(formatReadableWon(337500.4)).toBe('33만 7,500원')
    expect(formatReadableWon(0)).toBe('0원')
    expect(formatReadableWon(-5)).toBe('0원')
    expect(formatReadableWon(NaN)).toBe('0원')
  })
})

describe('formatMarketCap', () => {
  it.each([
    [3_120_000_000_000, '$3.12T'],
    [845_210_000_000, '$845.21B'],
    [12_300_000, '$12.30M'],
    [950_000, '$950,000'],
  ])('%d 는 %s', (value, expected) => {
    expect(formatMarketCap(value)).toBe(expected)
  })

  it('자릿수를 줄 수 있다', () => {
    expect(formatMarketCap(3_120_000_000_000, 1)).toBe('$3.1T')
  })

  it.each([undefined, null, 0])('%s 는 -', (value) => {
    expect(formatMarketCap(value)).toBe('-')
  })
})

describe('formatUsd', () => {
  it('천 단위 쉼표를 넣고 소수 둘째 자리까지 보인다', () => {
    expect(formatUsd(1234567.891)).toBe('$1,234,567.89')
    expect(formatUsd(0)).toBe('$0.00')
  })

  it('음수는 부호를 통화 기호 앞에 둔다', () => {
    expect(formatUsd(-12.3)).toBe('-$12.30')
    expect(formatUsd(-1234.5)).toBe('-$1,234.50')
  })

  it('반올림해 0 이 되는 음수는 부호 없이 보인다', () => {
    expect(formatUsd(-0.004)).toBe('$0.00')
  })

  it('소수 자리 상한을 바꿀 수 있다', () => {
    expect(formatUsd(0.485, 6)).toBe('$0.485')
    expect(formatUsd(1.2, 6)).toBe('$1.20')
    expect(formatUsd(1234.6, 0)).toBe('$1,235')
  })

  it('값이 없으면 -', () => {
    expect(formatUsd(undefined)).toBe('-')
    expect(formatUsd(null)).toBe('-')
  })
})

describe('formatKrw', () => {
  it('천 단위 쉼표, 음수는 부호를 통화 기호 앞에 둔다', () => {
    expect(formatKrw(1234567)).toBe('₩1,234,567')
    expect(formatKrw(-1200)).toBe('-₩1,200')
    expect(formatKrw(0)).toBe('₩0')
  })

  it('값이 없으면 -', () => {
    expect(formatKrw(undefined)).toBe('-')
  })
})

describe('formatQuantity', () => {
  it('소수 여섯째 자리까지, 끝의 0 없이 보인다', () => {
    expect(formatQuantity(237.89990234375)).toBe('237.899902')
    expect(formatQuantity(237.9)).toBe('237.9')
    expect(formatQuantity(10)).toBe('10')
  })

  it('천 단위 쉼표를 넣는다', () => {
    expect(formatQuantity(12345.5)).toBe('12,345.5')
  })

  it('0 은 0, 값이 없으면 -', () => {
    expect(formatQuantity(0)).toBe('0')
    expect(formatQuantity(undefined)).toBe('-')
  })
})

describe('formatShares', () => {
  it('수량 뒤에 주를 띄우지 않고 둔다', () => {
    expect(formatShares(237.9)).toBe('237.9주')
    expect(formatShares(1500)).toBe('1,500주')
  })

  it('값이 없으면 -', () => {
    expect(formatShares(null)).toBe('-')
  })
})

describe('formatSigned', () => {
  it('양수는 +, 음수는 - 를 통화 기호 앞에 둔다', () => {
    expect(formatSigned(1234.5, formatUsd)).toBe('+$1,234.50')
    expect(formatSigned(-1234.5, formatUsd)).toBe('-$1,234.50')
  })

  it('반올림해 0 이 되는 값은 부호 없이 보인다', () => {
    expect(formatSigned(0.001, formatPercent)).toBe('0.00%')
    expect(formatSigned(-0.004, formatUsd)).toBe('$0.00')
  })
})
