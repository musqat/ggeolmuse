import { describe, it, expect } from 'vitest'
import { cadenceText, countRounds, parseMonths, scheduleFields, scheduleText, toCadence, toWeekday } from './dcaSchedule'

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

describe('cadenceText', () => {
  it('29일 이후도 그 날짜가 없는 달 규칙 없이 주기와 날짜만 보인다', () => {
    expect(cadenceText('MONTHLY', '3', '2023-01-31')).toBe('매달 31일')
    expect(cadenceText('MONTHS', '6', '2023-01-30')).toBe('6개월마다 30일')
    expect(cadenceText('WEEKLY', '3', '2023-01-01')).toBe('매주 월요일')
  })
})

describe('scheduleFields', () => {
  const period = { startDate: '2023-01-01', endDate: '2026-10-08' }

  it('매달 · N개월마다는 첫 매수일(비우면 시작일)의 며칠과 개월 수를 싣고 첫 매수일은 고른 경우만 싣는다', () => {
    expect(scheduleFields({ cadence: 'MONTHLY', months: 1, firstPurchaseDate: '', ...period })).toEqual({
      frequency: 'MONTHLY',
      purchaseDay: 1,
      investmentInterval: 1,
    })
    expect(scheduleFields({ cadence: 'MONTHS', months: 3, firstPurchaseDate: '2023-02-15', ...period })).toEqual({
      frequency: 'MONTHLY',
      firstPurchaseDate: '2023-02-15',
      purchaseDay: 15,
      investmentInterval: 3,
    })
  })

  it('매주는 주말 첫 매수일을 다음 월요일로 싣고, 그 월요일이 종료일 뒤면 고른 값만 싣는다', () => {
    expect(scheduleFields({ cadence: 'WEEKLY', months: 1, firstPurchaseDate: '', ...period })).toEqual({
      frequency: 'WEEKLY',
      firstPurchaseDate: '2023-01-02',
    })
    expect(
      scheduleFields({ cadence: 'WEEKLY', months: 1, firstPurchaseDate: '', startDate: '2023-01-07', endDate: '2023-01-08' })
    ).toEqual({ frequency: 'WEEKLY' })
  })

  it('매일은 주기만 싣는다', () => {
    expect(scheduleFields({ cadence: 'DAILY', months: 1, firstPurchaseDate: '', ...period })).toEqual({ frequency: 'DAILY' })
  })
})

describe('countRounds', () => {
  it.each([
    ['DAILY', 1, '2023-01-02', '2023-01-13', 10],
    ['DAILY', 1, '2023-01-07', '2023-01-08', 0],
    ['WEEKLY', 1, '2023-01-01', '2023-01-31', 5],
    ['WEEKLY', 1, '2023-01-04', '2023-01-31', 4],
    ['WEEKLY', 1, '2023-01-07', '2023-01-08', 0],
    ['MONTHLY', 1, '2023-01-31', '2023-05-31', 5],
    ['MONTHLY', 1, '2023-01-31', '2023-03-02', 2],
    ['MONTHLY', 1, '2023-01-15', '2023-05-14', 4],
    ['MONTHS', 3, '2023-01-15', '2023-12-31', 4],
    ['MONTHLY', 1, '2023-01-15', '2023-01-14', 0],
  ] as const)('%s %i개월 %s ~ %s 는 %i회(휴장일은 모르고 주말만 뺌)', (cadence, months, first, end, expected) => {
    expect(countRounds(cadence, months, first, end)).toBe(expected)
  })
})

describe('toCadence', () => {
  it.each([
    ['WEEKLY', 'WEEKLY'],
    ['MONTHS', 'MONTHS'],
    ['YEARLY', 'MONTHLY'],
    [undefined, 'MONTHLY'],
  ])('%s → %s', (input, expected) => {
    expect(toCadence(input)).toBe(expected)
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
