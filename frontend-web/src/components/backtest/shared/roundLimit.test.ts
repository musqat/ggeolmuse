import { describe, it, expect } from 'vitest'
import {
  MAX_COMPARISON_ROUND_LOAD,
  MAX_ROUND_LOAD,
  affordableRounds,
  comparisonRoundIssue,
  conditionalLoad,
  conditionalRoundIssue,
  dcaLoad,
  dcaRoundIssue,
} from './roundLimit'
import { parseLocalDate, toLocalDateString } from '../../../utils/dateUtils'

const END = '2025-12-31'

// END 까지 평일이 n 일이 되는 시작일
const startFor = (n: number) => {
  const day = parseLocalDate(END)
  let count = 0
  for (;;) {
    if (day.getDay() % 6 !== 0 && ++count === n) return toLocalDateString(day)
    day.setDate(day.getDate() - 1)
  }
}

const yearsBefore = (years: number) => `${2025 - years}-12-31`

describe('적립식 회차 상한', () => {
  it('상한은 매일 적립 4,000회 × 평일 4,000일, 전략 비교 칩 합은 칩 하나 상한의 3배', () => {
    expect(MAX_ROUND_LOAD).toBe(4000 * 4000)
    expect(MAX_COMPARISON_ROUND_LOAD).toBe(3 * MAX_ROUND_LOAD)
  })

  it('매일 4,000회까지 통과, 4,001회면 최대 몇 년인지 안내', () => {
    expect(dcaRoundIssue({ cadence: 'DAILY', months: 1, first: startFor(4000), end: END })).toBeNull()
    expect(dcaRoundIssue({ cadence: 'DAILY', months: 1, first: startFor(4001), end: END })).toBe(
      '매일 적립은 최대 약 15년 — 기간 줄이기 · 주기 늘리기'
    )
  })

  it('매주 · N개월마다는 회차가 적은 만큼 더 긴 기간까지 통과', () => {
    expect(dcaRoundIssue({ cadence: 'WEEKLY', months: 1, first: yearsBefore(34), end: END })).toBeNull()
    expect(dcaRoundIssue({ cadence: 'WEEKLY', months: 1, first: yearsBefore(36), end: END })).toBe(
      '매주 적립은 최대 약 34년 — 기간 줄이기 · 주기 늘리기'
    )
    expect(dcaRoundIssue({ cadence: 'MONTHLY', months: 1, first: yearsBefore(70), end: END })).toBeNull()
    expect(dcaRoundIssue({ cadence: 'MONTHLY', months: 1, first: yearsBefore(75), end: END })).toBe(
      '매달 적립은 최대 약 71년 — 기간 줄이기 · 주기 늘리기'
    )
    expect(dcaRoundIssue({ cadence: 'MONTHS', months: 3, first: yearsBefore(130), end: END })).toBe(
      '3개월마다 적립은 최대 약 123년 — 기간 줄이기 · 주기 늘리기'
    )
  })

  it('원금 · 예산으로 살 수 있는 회차까지만 셈', () => {
    const plan = { cadence: 'DAILY' as const, months: 1, first: yearsBefore(30), end: END }

    expect(dcaLoad({ ...plan, affordable: 1000 })).toBe(1000 * dcaLoad({ ...plan, affordable: 1 }))
    expect(dcaRoundIssue({ ...plan, affordable: 1000 })).toBeNull()
    expect(dcaRoundIssue(plan)).not.toBeNull()
  })
})

describe('조건부 매수 회차 상한', () => {
  it('예산 · 최대 횟수가 없으면 평일마다 살 수 있다고 보고 4,000일까지 통과', () => {
    expect(conditionalRoundIssue({ start: startFor(4000), end: END, sells: false })).toBeNull()
    expect(conditionalRoundIssue({ start: startFor(5000), end: END, sells: false })).toBe(
      '이 기간이면 최대 매수 3,200회까지 — 최대 매수 횟수 정하기 · 기간 줄이기'
    )
  })

  it('최대 횟수 · 예산으로 살 수 있는 회차까지만 셈', () => {
    const start = yearsBefore(60)
    const days = conditionalLoad({ start, end: END, max: 1, sells: false })

    expect(conditionalLoad({ start, end: END, max: 300, sells: false })).toBe(300 * days)
    expect(conditionalLoad({ start, end: END, affordable: 10, sells: false })).toBe(10 * days)
    expect(conditionalRoundIssue({ start, end: END, max: 300, affordable: 10, sells: false })).toBeNull()
  })

  it('매도 규칙이 있으면 판 돈으로 다시 사서 예산 · 최대 횟수와 상관없이 평일마다 셈', () => {
    expect(conditionalRoundIssue({ start: yearsBefore(30), end: END, max: 5, affordable: 5, sells: true })).toBe(
      '매도 규칙이 있는 조건부 매수는 최대 약 15년 — 기간 줄이기'
    )
  })
})

describe('원금으로 살 수 있는 회차', () => {
  it('원금 ÷ 회당 금액 내림, 원금이나 회당 금액이 0 보다 큰 수가 아니면 제한 없음', () => {
    expect(affordableRounds('1000000', 300000)).toBe(3)
    expect(affordableRounds('', 100000)).toBeUndefined()
    expect(affordableRounds('abc', 100000)).toBeUndefined()
    expect(affordableRounds('0', 100000)).toBeUndefined()
    expect(affordableRounds('1000000', Number.NaN)).toBeUndefined()
  })

  it('회차 셈이 숫자가 아니면 회차 안내 없음', () => {
    expect(dcaRoundIssue({ cadence: 'DAILY', months: 1, first: startFor(4001), end: END, affordable: Number.NaN })).toBeNull()
    expect(conditionalRoundIssue({ start: startFor(5000), end: END, affordable: Number.NaN, sells: false })).toBeNull()
  })
})

describe('전략 비교 칩 합', () => {
  it('칩 하나 상한의 3배까지 통과', () => {
    expect(comparisonRoundIssue(MAX_COMPARISON_ROUND_LOAD)).toBeNull()
    expect(comparisonRoundIssue(MAX_COMPARISON_ROUND_LOAD + 1)).toBe(
      '칩 회차 합이 너무 많음 — 칩 빼기 · 기간 줄이기 · 주기 늘리기'
    )
  })
})
