import { describe, it, expect } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useExecutionTiming } from './useExecutionTiming'

describe('useExecutionTiming', () => {
  it('기본은 신호 난 날 종가, 고른 값을 요약 글 · 직전 설정에 싣고 되살리며 고를 수 없는 값은 신호 난 날 종가로', () => {
    const { result } = renderHook(() => useExecutionTiming())
    expect(result.current.timing).toBe('SAME_DAY_CLOSE')
    expect(result.current.text).toBe('')

    act(() => result.current.field.onChange('NEXT_DAY_OPEN'))
    expect(result.current.saved).toEqual({ executionTiming: 'NEXT_DAY_OPEN' })
    expect(result.current.text).toBe('다음 거래일 시가에 체결')

    act(() => result.current.load({ executionTiming: 'OPEN' }))
    expect(result.current.timing).toBe('SAME_DAY_CLOSE')
    act(() => result.current.load({ executionTiming: 'NEXT_DAY_OPEN' }))
    expect(result.current.timing).toBe('NEXT_DAY_OPEN')
  })
})
