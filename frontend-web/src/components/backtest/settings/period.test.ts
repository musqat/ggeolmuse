import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { dateBefore, fromLabel, parseAgo, periodIssue, presetStart, usePeriod } from './period'

// 기대값은 TZ=Asia/Seoul 전제 (vitest.config.ts 에서 고정)
describe('기간 빠른 고르기 날짜', () => {
  it('끝 날짜에서 N주 · N달 · N년 전을 센다', () => {
    expect(dateBefore('2026-11-08', 2, 'WEEK')).toBe('2026-10-25')
    expect(dateBefore('2026-11-08', 3, 'MONTH')).toBe('2026-08-08')
    expect(dateBefore('2026-11-08', 1, 'YEAR')).toBe('2025-11-08')
  })

  it('그 달에 같은 날짜가 없으면 그 달 말일로 센다', () => {
    expect(dateBefore('2026-03-31', 1, 'MONTH')).toBe('2026-02-28')
    expect(dateBefore('2024-02-29', 1, 'YEAR')).toBe('2023-02-28')
  })

  it('센 시작일이 주말이면 다음 월요일', () => {
    expect(presetStart('2026-11-08', 1, 'YEAR')).toBe('2025-11-10')
    expect(presetStart('2026-10-08', 1, 'YEAR')).toBe('2025-10-08')
  })

  it('몇 주 · 달 · 년 전은 1 ~ 99 정수만 읽는다', () => {
    expect(parseAgo('1')).toBe(1)
    expect(parseAgo('99')).toBe(99)
    expect(parseAgo('0')).toBeUndefined()
    expect(parseAgo('100')).toBeUndefined()
    expect(parseAgo('')).toBeUndefined()
  })

  it('끝 날짜가 오늘이면 오늘부터, 아니면 그 날짜부터', () => {
    expect(fromLabel('2026-11-08', '2026-11-08')).toBe('오늘부터')
    expect(fromLabel('2026-06-30', '2026-11-08')).toBe('6월 30일부터')
  })
})

describe('기간 칸 오류', () => {
  const base = { start: '2025-11-10', end: '2026-11-08', agoOn: true, agoCount: '1' }

  it('빠른 고르기가 켜져 있고 숫자가 비거나 범위 밖이면 오류', () => {
    expect(periodIssue(base)).toBeNull()
    expect(periodIssue({ ...base, agoCount: '' })).toEqual({ kind: 'error', text: '몇 주 · 달 · 년 전인지 1 이상' })
    expect(periodIssue({ ...base, agoCount: '100' })).toEqual({ kind: 'error', text: '99 이하' })
    expect(periodIssue({ ...base, agoOn: false, agoCount: '' })).toBeNull()
  })

  it('끝 날짜가 시작일과 같거나 앞이면 오류', () => {
    expect(periodIssue({ ...base, agoOn: false, start: '2026-11-08' })).toEqual({
      kind: 'error',
      text: '끝 날짜가 시작일과 같거나 앞',
    })
  })
})

describe('usePeriod', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-08T12:00:00+09:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('처음은 오늘부터 1년 전이고 끝 날짜는 비워 오늘로 둔다', () => {
    const { result } = renderHook(() => usePeriod())

    expect(result.current).toMatchObject({ start: '2025-10-08', end: '', agoCount: '1', agoUnit: 'YEAR', agoOn: true })
  })

  it('숫자 · 단위를 고치면 끝 날짜에서 다시 세고, 끝 날짜를 옮기면 시작일이 따라간다', () => {
    const { result } = renderHook(() => usePeriod())

    act(() => result.current.setAgoUnit('MONTH'))
    expect(result.current.start).toBe('2026-09-08')
    act(() => result.current.setAgoCount('6'))
    expect(result.current.start).toBe('2026-04-08')
    act(() => result.current.setEnd('2026-06-30'))
    expect(result.current.start).toBe('2025-12-30')
  })

  it('숫자 칸은 숫자만 남기고, 비우면 시작일을 그대로 둔다', () => {
    const { result } = renderHook(() => usePeriod())

    act(() => result.current.setAgoCount('2년'))
    expect(result.current.agoCount).toBe('2')
    expect(result.current.start).toBe('2024-10-08')
    act(() => result.current.setAgoCount(''))
    expect(result.current.start).toBe('2024-10-08')
  })

  it('시작일을 직접 고르면 빠른 고르기를 끄고, 끝 날짜를 옮겨도 시작일은 그대로', () => {
    const { result } = renderHook(() => usePeriod())

    act(() => result.current.setStart('2026-01-05'))
    expect(result.current).toMatchObject({ start: '2026-01-05', agoOn: false })
    act(() => result.current.setEnd('2026-07-31'))
    expect(result.current.start).toBe('2026-01-05')
    act(() => result.current.setAgoUnit('WEEK'))
    expect(result.current).toMatchObject({ agoOn: true, start: '2026-07-24' })
  })
})
