import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authApi } from '../../services/api'
import { notify } from '../common/notify'
import { NicknameDialog } from './NicknameDialog'

vi.mock('../../services/api', () => ({ authApi: { changeNickname: vi.fn() } }))

function renderDialog() {
  const onClose = vi.fn()
  const onChanged = vi.fn()
  render(<NicknameDialog initialNickname="" onClose={onClose} onChanged={onChanged} />)
  return { onClose, onChanged }
}

beforeEach(() => {
  vi.mocked(authApi.changeNickname).mockResolvedValue({} as never)
  vi.spyOn(notify, 'error').mockImplementation(() => {})
  vi.spyOn(notify, 'success').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

describe('NicknameDialog', () => {
  it('규칙에 안 맞는 닉네임은 보내지 않고 서버와 같은 규칙을 알린다', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.type(screen.getByLabelText('새 닉네임'), '가 나')
    await user.click(screen.getByRole('button', { name: '변경하기' }))

    expect(notify.error).toHaveBeenCalledWith('닉네임은 2 ~ 20자의 영문, 숫자, 한글, _ 만 쓸 수 있습니다.')
    expect(authApi.changeNickname).not.toHaveBeenCalled()
  })

  it('규칙에 맞으면 보내고 창을 닫는다', async () => {
    const user = userEvent.setup()
    const { onClose, onChanged } = renderDialog()

    await user.type(screen.getByLabelText('새 닉네임'), '새이름_1')
    await user.click(screen.getByRole('button', { name: '변경하기' }))

    expect(authApi.changeNickname).toHaveBeenCalledWith({ nickname: '새이름_1' })
    expect(onChanged).toHaveBeenCalledTimes(1)
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
