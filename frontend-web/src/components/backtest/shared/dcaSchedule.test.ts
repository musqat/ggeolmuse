import { describe, it, expect, vi, afterEach } from 'vitest'
import { cadenceText, countRounds, parseMonths, resolveFirstPurchase, scheduleFields, toCadence, toWeekday } from './dcaSchedule'

describe('resolveFirstPurchase', () => {
  const pick = (date: string, start = '2025-01-01') => ({ date, start })

  it('고르지 않았으면 빈 값(시작일을 따라감)', () => {
    expect(resolveFirstPurchase('2025-03-10', 'WEEKLY', pick(''))).toBe('')
  })

  it('고를 때와 시작일이 같으면 고른 날짜, 주말이면 다음 월요일', () => {
    expect(resolveFirstPurchase('2025-01-01', 'MONTHLY', pick('2025-01-15'))).toBe('2025-01-15')
    expect(resolveFirstPurchase('2025-01-01', 'WEEKLY', pick('2025-01-04'))).toBe('2025-01-06')
  })

  it('시작일이 바뀌면 매주는 같은 요일, 매달은 같은 며칠의 시작일 뒤 첫 날로 옮긴다', () => {
    expect(resolveFirstPurchase('2025-03-10', 'WEEKLY', pick('2025-01-08'))).toBe('2025-03-12')
    expect(resolveFirstPurchase('2025-04-20', 'MONTHLY', pick('2025-01-15'))).toBe('2025-05-15')
    expect(resolveFirstPurchase('2025-04-10', 'MONTHS', pick('2025-01-31'))).toBe('2025-04-30')
  })

  it('매일은 시작일이 바뀌면 시작일(주말이면 다음 월요일)', () => {
    expect(resolveFirstPurchase('2025-03-08', 'DAILY', pick('2025-01-15'))).toBe('2025-03-10')
  })
})

describe('며칠 규칙', () => {
  it('고른 며칠이 그 달에 없어도 규칙의 며칠로 안내 · 요청 · 회차를 센다', () => {
    expect(cadenceText('MONTHLY', '3', '2025-04-30', 31)).toBe('매달 31일')
    expect(
      scheduleFields({ cadence: 'MONTHLY', months: 1, firstPurchaseDate: '2025-04-30', monthDay: 31, startDate: '2025-04-10', endDate: '2025-08-31' })
    ).toMatchObject({ firstPurchaseDate: '2025-04-30', purchaseDay: 31 })
    expect(countRounds('MONTHLY', 1, '2025-04-30', '2025-05-30', 31)).toBe(1)
    expect(countRounds('MONTHLY', 1, '2025-04-30', '2025-05-30')).toBe(2)
  })
})

describe('cadenceText', () => {
  it('29일 이후도 그 날짜가 없는 달 규칙 없이 주기와 날짜만 보인다', () => {
    expect(cadenceText('MONTHLY', '3', '2023-01-31')).toBe('매달 31일')
    expect(cadenceText('MONTHS', '6', '2023-01-30')).toBe('6개월마다 30일')
    expect(cadenceText('WEEKLY', '3', '2023-01-04')).toBe('매주 수요일')
  })

  it('매주 월요일(주말 첫 매수일 포함)은 그 주 첫 거래일로 보인다', () => {
    expect(cadenceText('WEEKLY', '3', '2023-01-01')).toBe('매주 첫 거래일')
    expect(cadenceText('WEEKLY', '3', '2023-01-02')).toBe('매주 첫 거래일')
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

  it.each([
    ['2024-06-16', 1],
    ['2024-06-17', 2],
  ] as const)('매달 종료일에 걸린 달은 주말 투자일 ~ 종료일 %s 에 평일이 있을 때만 센다(%i회)', (end, expected) => {
    expect(countRounds('MONTHLY', 1, '2024-05-15', end)).toBe(expected)
  })

  it('매달 종료일이 그 달 말일이거나 종료일 앞 달이면 주말 투자일도 앞 거래일 회차로 센다', () => {
    expect(countRounds('MONTHLY', 1, '2024-07-31', '2024-08-31')).toBe(2)
    expect(countRounds('MONTHLY', 1, '2024-07-31', '2024-09-01')).toBe(2)
  })

  describe('끝 날짜가 오늘', () => {
    afterEach(() => {
      vi.useRealTimers()
    })

    it.each([
      ['DAILY', '2026-10-05', 3],
      ['WEEKLY', '2026-10-01', 1],
      ['MONTHLY', '2026-09-08', 1],
    ] as const)('%s 는 종가가 없는 오늘 회차를 세지 않는다(%s 부터 %i회)', (cadence, first, expected) => {
      vi.useFakeTimers({ toFake: ['Date'] })
      vi.setSystemTime(new Date('2026-10-08T12:00:00+09:00'))

      expect(countRounds(cadence, 1, first, '2026-10-08')).toBe(expected)
      expect(countRounds(cadence, 1, first, '2026-10-07')).toBe(expected)
    })
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
