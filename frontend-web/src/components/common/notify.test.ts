import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { notify, TOAST_MS, ACTION_TOAST_MS, dismissToast, holdToast, releaseToast, settleConfirm, readToasts, readConfirm } from './notify'

beforeEach(() => vi.useFakeTimers())
afterEach(() => {
  readToasts().forEach((t) => dismissToast(t.id))
  settleConfirm(false)
  vi.useRealTimers()
})

describe('알림', () => {
  it('종류와 문구를 쌓고 시간이 지나면 걷는다', () => {
    notify.success('저장했습니다')
    notify.error('실패했습니다')

    expect(readToasts().map((t) => [t.kind, t.message])).toEqual([['success', '저장했습니다'], ['error', '실패했습니다']])
    vi.advanceTimersByTime(TOAST_MS)
    expect(readToasts()).toEqual([])
  })

  it('닫기를 누르면 그 알림만 빠진다', () => {
    notify.info('하나')
    notify.info('둘')

    dismissToast(readToasts()[0].id)
    expect(readToasts().map((t) => t.message)).toEqual(['둘'])
  })

  it('단추 하나를 받는 알림은 더 오래 남고 그 알림의 id 를 돌려준다', () => {
    const onClick = vi.fn()
    const id = notify.info('설정을 처음으로 돌림', { label: '되돌리기', onClick })

    expect(readToasts()).toEqual([{ id, kind: 'info', message: '설정을 처음으로 돌림', action: { label: '되돌리기', onClick } }])
    vi.advanceTimersByTime(TOAST_MS)
    expect(readToasts()).toHaveLength(1)
    vi.advanceTimersByTime(ACTION_TOAST_MS - TOAST_MS)
    expect(readToasts()).toEqual([])
  })

  it('잡아 둔 알림은 시간이 지나도 남고, 놓으면 처음부터 다시 센다', () => {
    const id = notify.info('직전 설정을 불러옴', { label: '되돌리기', onClick: vi.fn() })

    holdToast(id)
    vi.advanceTimersByTime(ACTION_TOAST_MS * 2)
    expect(readToasts()).toHaveLength(1)
    releaseToast(id)
    vi.advanceTimersByTime(ACTION_TOAST_MS - 1)
    expect(readToasts()).toHaveLength(1)
    vi.advanceTimersByTime(1)
    expect(readToasts()).toEqual([])
  })
})

describe('확인 창', () => {
  it('확인을 누르면 true, 취소면 false 로 끝난다', async () => {
    const first = notify.confirm('지울까요?', { danger: true })
    expect(readConfirm()?.message).toBe('지울까요?')
    settleConfirm(true)
    await expect(first).resolves.toBe(true)

    const second = notify.confirm('계속할까요?')
    settleConfirm(false)
    await expect(second).resolves.toBe(false)
    expect(readConfirm()).toBeNull()
  })

  it('새 확인 창이 뜨면 먼저 떠 있던 것은 취소로 끝난다', async () => {
    const first = notify.confirm('하나')
    const second = notify.confirm('둘')

    await expect(first).resolves.toBe(false)
    settleConfirm(true)
    await expect(second).resolves.toBe(true)
  })
})
