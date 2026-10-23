import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import AiChatButton from '../chat/AiChatButton'
import { FormDialog } from './FormDialog'
import { Modal } from './Modal'
import { Notifications } from './Notifications'
import { notify, dismissToast, settleConfirm, readToasts } from './notify'

vi.mock('../chat/AiChatModal', () => ({ default: () => null }))
vi.mock('../../hooks/useAuthModal', () => ({ useAuthModal: () => ({ openLogin: vi.fn(), openSignup: vi.fn() }) }))
vi.mock('../../hooks/useAiChat', () => ({ useAiChat: () => ({ openChat: vi.fn(), closeChat: vi.fn() }) }))

afterEach(() => {
  readToasts().forEach((t) => dismissToast(t.id))
  settleConfirm(false)
})

// 요소를 감싼 떠 있는 바탕(fixed)의 z 값
const zOf = (element: HTMLElement) => Number(element.closest<HTMLElement>('.fixed')?.style.zIndex || 0)

describe('떠 있는 층 순서', () => {
  it('알림은 확인 창 위에, 확인 창은 열린 창 위에, 창은 AI 채팅 버튼 위에 뜬다', () => {
    render(
      <MemoryRouter>
        <AiChatButton />
        <Notifications />
        <FormDialog title="회원 탈퇴" confirmLabel="탈퇴하기" confirmDisabled={false} onConfirm={vi.fn()} onCancel={vi.fn()}>
          <p>탈퇴 창 내용</p>
        </FormDialog>
        <Modal isOpen onClose={vi.fn()} title="KRW 입금">
          <p>입금 창 내용</p>
        </Modal>
      </MemoryRouter>
    )
    act(() => { void notify.confirm('정말로 탈퇴하시겠습니까?') })
    act(() => notify.error('입금에 실패했습니다'))

    const chatButton = Number(screen.getByRole('button', { name: 'AI 종목 분석' }).style.zIndex)
    const dialogs = [zOf(screen.getByText('탈퇴 창 내용')), zOf(screen.getByText('입금 창 내용'))]

    expect(Math.min(...dialogs)).toBeGreaterThan(chatButton)
    expect(zOf(screen.getByText('정말로 탈퇴하시겠습니까?'))).toBeGreaterThan(Math.max(...dialogs))
    expect(zOf(screen.getByText('입금에 실패했습니다'))).toBeGreaterThan(zOf(screen.getByText('정말로 탈퇴하시겠습니까?')))
  })
})
