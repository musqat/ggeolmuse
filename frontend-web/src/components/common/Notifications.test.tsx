import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Notifications } from './Notifications'
import { notify, dismissToast, settleConfirm, readToasts } from './notify'

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
