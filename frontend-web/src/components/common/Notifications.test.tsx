import { describe, it, expect, afterEach, vi } from 'vitest'
import { render, screen, act, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Notifications } from './Notifications'
import { notify, dismissToast, settleConfirm, readToasts, ACTION_TOAST_MS } from './notify'

afterEach(() => {
  readToasts().forEach((t) => dismissToast(t.id))
  settleConfirm(false)
})

describe('Notifications', () => {
  it('알림 문구를 보이고 닫기를 누르면 사라진다', async () => {
    render(<Notifications />)
    act(() => notify.success('저장했습니다'))

    expect(screen.getByText('저장했습니다')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '닫기' }))
    expect(screen.queryByText('저장했습니다')).not.toBeInTheDocument()
  })

  it('단추가 있는 알림은 그 단추로 포커스를 옮기고, 누르면 할 일을 하고 닫힌 뒤 원래 자리로 포커스를 돌린다', async () => {
    render(<><button>설정 초기화</button><Notifications /></>)
    const trigger = screen.getByRole('button', { name: '설정 초기화' })
    trigger.focus()
    const onClick = vi.fn()
    act(() => { notify.info('설정을 처음으로 돌림', { label: '되돌리기', onClick }) })

    const undo = screen.getByRole('button', { name: '되돌리기' })
    expect(undo).toHaveFocus()
    await userEvent.click(undo)

    expect(onClick).toHaveBeenCalledOnce()
    expect(screen.queryByText('설정을 처음으로 돌림')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })

  it('단추가 있는 알림은 Esc 로 닫고, 마우스가 올라가 있는 동안은 시간이 지나도 남는다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      render(<Notifications />)
      act(() => { notify.info('직전 설정을 불러옴', { label: '되돌리기', onClick: vi.fn() }) })
      const message = screen.getByText('직전 설정을 불러옴')

      fireEvent.mouseEnter(message.parentElement!)
      act(() => { vi.advanceTimersByTime(ACTION_TOAST_MS * 2) })
      expect(message).toBeInTheDocument()
      fireEvent.keyDown(screen.getByRole('button', { name: '되돌리기' }), { key: 'Escape' })
      expect(screen.queryByText('직전 설정을 불러옴')).not.toBeInTheDocument()
    } finally {
      vi.useRealTimers()
    }
  })

  it('확인 창의 버튼이 약속을 끝낸다', async () => {
    render(<Notifications />)
    let asked!: Promise<boolean>
    act(() => { asked = notify.confirm('지울까요?', { danger: true, confirmText: '삭제' }) })

    expect(screen.getByText('지울까요?')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))
    await expect(asked).resolves.toBe(true)
    expect(screen.queryByText('지울까요?')).not.toBeInTheDocument()
  })
})
