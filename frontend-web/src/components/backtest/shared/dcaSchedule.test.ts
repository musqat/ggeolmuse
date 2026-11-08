import { describe, it, expect } from 'vitest'
import { parseMonths, scheduleText, toWeekday } from './dcaSchedule'

describe('scheduleText', () => {
  it('매일은 거래일마다로 보인다', () => {
    expect(scheduleText('DAILY', '3', '2023-01-04')).toBe('거래일마다')
  })

  it('매주는 첫 매수일의 요일, 주말이면 월요일로 보인다', () => {
    expect(scheduleText('WEEKLY', '3', '2023-01-04')).toBe('매주 수요일')
    expect(scheduleText('WEEKLY', '3', '2023-01-07')).toBe('매주 월요일')
    expect(scheduleText('WEEKLY', '3', '2023-01-01')).toBe('매주 월요일')
  })

  it('매달 · N개월마다는 첫 매수일의 날짜로 보이고, 개월 수가 비면 물음표로 둔다', () => {
    expect(scheduleText('MONTHLY', '3', '2023-01-15')).toBe('매달 15일')
    expect(scheduleText('MONTHS', '3', '2023-01-15')).toBe('3개월마다 15일')
    expect(scheduleText('MONTHS', '', '2023-01-15')).toBe('?개월마다 15일')
  })

  it('29일 이후면 그 날짜가 없는 달은 그 달 마지막 거래일이라고 같이 보인다', () => {
    expect(scheduleText('MONTHLY', '3', '2023-01-31')).toBe('매달 31일 · 31일이 없는 달은 그 달 마지막 거래일')
    expect(scheduleText('MONTHLY', '3', '2023-01-28')).toBe('매달 28일')
  })
})

describe('toWeekday', () => {
  it.each([
    ['2023-01-07', '2023-01-09'],
    ['2023-01-01', '2023-01-02'],
    ['2023-01-04', '2023-01-04'],
    ['2023-12-31', '2024-01-01'],
  ])('%s → %s', (input, expected) => {
    expect(toWeekday(input)).toBe(expected)
  })
})

describe('parseMonths', () => {
  it.each([
    ['1', 1],
    ['12', 12],
    ['0', undefined],
    ['13', undefined],
    ['1.5', undefined],
    ['', undefined],
    ['x', undefined],
  ])('%s → %s', (input, expected) => {
    expect(parseMonths(input)).toBe(expected)
  })
})
