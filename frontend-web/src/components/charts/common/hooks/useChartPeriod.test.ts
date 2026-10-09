import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useChartPeriod } from './useChartPeriod'

// 기대값은 TZ=Asia/Seoul 전제 (vitest.config.ts 에서 고정)
describe('useChartPeriod 시작일', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    // KST 오전 5시. UTC 로는 아직 전날이다
    vi.setSystemTime(new Date('2026-03-31T05:00:00+09:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('1년은 오늘에서 1년 전 같은 날이다. KST 새벽에도 하루 밀리지 않는다', () => {
    const { result } = renderHook(() => useChartPeriod())

    expect(result.current.getStartDateFromPeriod('1y', '2020-01-15')).toBe('2025-03-31')
    expect(result.current.getStartDateFromPeriod('20y', '2020-01-15')).toBe('2006-03-31')
  })

  it('매수일부터는 원래 시작일을 쓴다', () => {
    const { result } = renderHook(() => useChartPeriod())

    expect(result.current.getStartDateFromPeriod('purchase', '2020-01-15')).toBe('2020-01-15')
  })

  it('직접설정은 고른 날을 쓰고, 안 골랐으면 원래 시작일을 쓴다', () => {
    const { result } = renderHook(() => useChartPeriod())

    expect(result.current.getStartDateFromPeriod('custom', '2020-01-15', '2022-06-01')).toBe('2022-06-01')
    expect(result.current.getStartDateFromPeriod('custom', '2020-01-15', '')).toBe('2020-01-15')
  })
})
