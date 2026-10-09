import { describe, it, expect } from 'vitest'
import { parsePeriod, rangeForPeriod } from './chartRange'

describe('parsePeriod', () => {
  it('아는 값은 그대로, 없거나 모르는 값은 1y 다', () => {
    expect(parsePeriod('3m')).toBe('3m')
    expect(parsePeriod('custom')).toBe('custom')
    expect(parsePeriod(null)).toBe('1y')
    expect(parsePeriod('2y')).toBe('1y')
  })
})

describe('rangeForPeriod', () => {
  it('개월 기간은 오늘에서 그만큼 전 같은 날부터, 그날이 없으면 그 달 말일부터다', () => {
    expect(rangeForPeriod('1m', '2026-03-31')).toEqual({
      status: 'ready',
      startDate: '2026-02-28',
      endDate: '2026-03-31',
    })
    expect(rangeForPeriod('10y', '2026-03-31')).toEqual({
      status: 'ready',
      startDate: '2016-03-31',
      endDate: '2026-03-31',
    })
  })

  it('전체는 1970-01-01 부터다', () => {
    expect(rangeForPeriod('all', '2026-03-31')).toEqual({
      status: 'ready',
      startDate: '1970-01-01',
      endDate: '2026-03-31',
    })
  })

  it('직접설정은 두 날짜가 다 있어야 하고, 시작일이 종료일보다 앞서야 한다', () => {
    expect(rangeForPeriod('custom', '2026-03-31', '2024-01-02', '')).toEqual({ status: 'incomplete' })
    expect(rangeForPeriod('custom', '2026-03-31', '2024-06-30', '2024-06-30')).toEqual({
      status: 'invalid',
      message: '시작일은 종료일보다 이전이어야 합니다.',
    })
    expect(rangeForPeriod('custom', '2026-03-31', '2024-01-02', '2024-06-28')).toEqual({
      status: 'ready',
      startDate: '2024-01-02',
      endDate: '2024-06-28',
    })
  })
})
