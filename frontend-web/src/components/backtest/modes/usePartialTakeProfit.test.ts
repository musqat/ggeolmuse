import { describe, it, expect } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { usePartialTakeProfit } from './usePartialTakeProfit'
import { countErrors } from '../settings/checks'

describe('usePartialTakeProfit', () => {
  it('고칠 칸은 단계마다 앞 칸 오류 하나로 센다', () => {
    const { result } = renderHook(() => usePartialTakeProfit())

    act(() => result.current.field.onStepsChange([{ gain: '', sell: '' }]))
    expect(countErrors(result.current.messages)).toBe(1)

    act(() =>
      result.current.field.onStepsChange([
        { gain: '', sell: '' },
        { gain: '10', sell: '0' },
        { gain: '20', sell: '50' },
      ])
    )
    expect(result.current.messages.map((message) => message?.text)).toEqual([
      '1단계 수익률 비어 있음',
      '2단계 팔 비율 0 초과 100 이하',
      undefined,
    ])
    expect(countErrors(result.current.messages)).toBe(2)
  })
})
