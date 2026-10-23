import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useAdminAction } from './useAdminAction'
import { notify } from '@/components/common/notify'

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

// 밖에서 resolve 하는 Promise
const deferred = () => {
  let resolve!: () => void
  const promise = new Promise<void>((r) => { resolve = r })
  return { promise, resolve }
}

describe('useAdminAction', () => {
  it('작업이 끝나면 loading 이 꺼지고 true 를 돌려준다', async () => {
    const { result } = renderHook(() => useAdminAction())

    let ok = false
    await act(async () => { ok = await result.current.run({ call: async () => {}, failure: () => '실패' }) })

    expect(ok).toBe(true)
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('실패하면 실패 문구를 error 에 두고 false 를 돌려준다', async () => {
    const { result } = renderHook(() => useAdminAction())

    let ok = true
    await act(async () => {
      ok = await result.current.run({ call: async () => { throw new Error('boom') }, failure: () => '목록 조회에 실패했습니다.' })
    })

    expect(ok).toBe(false)
    expect(result.current.error).toBe('목록 조회에 실패했습니다.')
    expect(result.current.loading).toBe(false)
  })

  it('확인을 취소하면 작업을 부르지 않고 false 를 돌려준다', async () => {
    vi.spyOn(notify, 'confirm').mockResolvedValue(false)
    const call = vi.fn()
    const { result } = renderHook(() => useAdminAction())

    let ok = true
    await act(async () => { ok = await result.current.run({ confirm: { text: '지울까요?' }, call, failure: () => '실패' }) })

    expect(ok).toBe(false)
    expect(call).not.toHaveBeenCalled()
  })

  it('작업 안에서 부른 다시 불러오기가 끝날 때까지 loading 이 켜져 있다', async () => {
    const reload = deferred()
    const { result } = renderHook(() => useAdminAction())

    let outer!: Promise<boolean>
    act(() => {
      outer = result.current.run({
        call: async () => {
          void result.current.run({ call: () => reload.promise, failure: () => '다시 받기 실패' })
        },
        failure: () => '작업 실패',
      })
    })
    await act(async () => { await outer })

    expect(result.current.loading).toBe(true)

    await act(async () => {
      reload.resolve()
      await reload.promise
    })

    expect(result.current.loading).toBe(false)
  })
})
