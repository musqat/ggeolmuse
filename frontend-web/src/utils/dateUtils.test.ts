import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  daysForTimeframe,
  getTodayString,
  getDateRangeForTimeframe,
  subtractDays,
  subtractMonths,
} from './dateUtils'

// 기대값은 TZ=Asia/Seoul 전제 (vitest.config.ts 에서 고정)

describe('daysForTimeframe', () => {
  it('기간 문자열을 일수로 바꾼다', () => {
    expect(daysForTimeframe('1주')).toBe(7)
    expect(daysForTimeframe('1개월')).toBe(30)
    expect(daysForTimeframe('3개월')).toBe(90)
    expect(daysForTimeframe('6개월')).toBe(180)
    expect(daysForTimeframe('1년')).toBe(365)
  })

  it('전체는 50년', () => {
    expect(daysForTimeframe('전체')).toBe(365 * 50)
  })

  it('직접설정은 0 (호출부에서 따로 처리한다)', () => {
    expect(daysForTimeframe('직접설정')).toBe(0)
  })
})

describe('getTodayString', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('낮 시간에는 오늘 날짜를 준다', () => {
    vi.setSystemTime(new Date('2026-08-13T03:00:00Z')) // 한국 12:00
    expect(getTodayString()).toBe('2026-08-13')
  })

  it('한국 자정~오전 9시 사이에도 오늘 날짜를 준다', () => {
    // UTC 로 찍으면 8/12 가 나오는 구간. 로컬 기준인지 확인
    vi.setSystemTime(new Date('2026-08-12T15:30:00Z')) // 한국 8/13 00:30
    expect(getTodayString()).toBe('2026-08-13')
  })

  it('한국 밤 늦은 시간에도 당일', () => {
    vi.setSystemTime(new Date('2026-08-13T14:30:00Z')) // 한국 8/13 23:30
    expect(getTodayString()).toBe('2026-08-13')
  })
})

describe('getDateRangeForTimeframe', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('직접설정이면 넘긴 날짜를 그대로 쓴다', () => {
    expect(getDateRangeForTimeframe('직접설정', '2020-01-01', '2020-12-31')).toEqual({
      startDate: '2020-01-01',
      endDate: '2020-12-31',
    })
  })

  it('직접설정인데 날짜가 없으면 null', () => {
    expect(getDateRangeForTimeframe('직접설정')).toBeNull()
    expect(getDateRangeForTimeframe('직접설정', '2020-01-01')).toBeNull()
  })

  it('기간만큼 거슬러 올라간 시작일을 만든다', () => {
    vi.setSystemTime(new Date('2026-08-13T03:00:00Z'))
    expect(getDateRangeForTimeframe('1개월')).toEqual({
      startDate: '2026-07-14',
      endDate: '2026-08-13',
    })
  })

  it('1주는 7일 전', () => {
    vi.setSystemTime(new Date('2026-08-13T03:00:00Z'))
    expect(getDateRangeForTimeframe('1주')?.startDate).toBe('2026-08-06')
  })

  it('한국 새벽에도 시작일과 종료일이 같은 기준을 쓴다', () => {
    // 한쪽만 UTC 면 하루 어긋난다
    vi.setSystemTime(new Date('2026-08-12T15:30:00Z')) // 한국 8/13 00:30
    expect(getDateRangeForTimeframe('1주')).toEqual({
      startDate: '2026-08-06',
      endDate: '2026-08-13',
    })
  })
})

describe('subtractDays', () => {
  it('월 경계를 넘어간다', () => {
    expect(subtractDays('2026-03-01', 1)).toBe('2026-02-28')
  })

  it('연 경계를 넘어간다', () => {
    expect(subtractDays('2026-01-01', 1)).toBe('2025-12-31')
  })

  it('윤년 2월을 지난다', () => {
    expect(subtractDays('2024-03-01', 1)).toBe('2024-02-29')
  })

  it('0일이면 그대로', () => {
    expect(subtractDays('2026-08-13', 0)).toBe('2026-08-13')
  })
})

describe('subtractMonths', () => {
  it('한 달 전으로 간다', () => {
    expect(subtractMonths('2026-08-13', 1)).toBe('2026-07-13')
  })

  it('연 경계를 넘어간다', () => {
    expect(subtractMonths('2026-01-15', 1)).toBe('2025-12-15')
  })

  it('말일에서 빼면 다음 달로 밀린다', () => {
    // 2/31 → 3/3. Date.setMonth 기본 동작.
    // 2/28 을 기대했다면 코드 수정 필요.
    expect(subtractMonths('2026-03-31', 1)).toBe('2026-03-03')
  })
})
