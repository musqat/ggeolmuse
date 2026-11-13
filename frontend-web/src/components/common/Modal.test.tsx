import { describe, it, expect, vi } from 'vitest'
import { useState, type ReactNode } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Modal } from './Modal'

// 단추로 여는 환전 창(닫으면 포커스가 단추로 복귀)
function Opener({ footer }: { footer?: ReactNode }) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button onClick={() => setOpen(true)}>환전 열기</button>
      <Modal isOpen={open} onClose={() => setOpen(false)} title="환전" footer={footer}>
        <input aria-label="금액" />
      </Modal>
    </>
  )
}

// 창을 열면서 사라지는 단추(모바일 메뉴 안 로그인 단추)
function VanishingOpener() {
  const [open, setOpen] = useState(false)
  const [menu, setMenu] = useState(true)
  return (
    <main tabIndex={-1}>
      {menu && <button onClick={() => { setOpen(true); setMenu(false) }}>로그인</button>}
      <Modal isOpen={open} onClose={() => setOpen(false)} title="로그인"><input aria-label="이메일" /></Modal>
    </main>
  )
}

// 창을 닫으면서 사라지는 단추(로그인하면 없어지는 머리말 로그인 단추)
function OpenerGoneOnClose() {
  const [open, setOpen] = useState(false)
  const [loggedIn, setLoggedIn] = useState(false)
  return (
    <main tabIndex={-1}>
      {!loggedIn && <button onClick={() => setOpen(true)}>로그인</button>}
      <Modal isOpen={open} onClose={() => setOpen(false)} title="로그인">
        <button onClick={() => { setLoggedIn(true); setOpen(false) }}>로그인하기</button>
      </Modal>
    </main>
  )
}

// 창 안 단추로 여는 확인 창
function NestedConfirm() {
  const [confirming, setConfirming] = useState(false)
  return (
    <Modal isOpen onClose={vi.fn()} title="계좌">
      <button onClick={() => setConfirming(true)}>계좌 삭제</button>
      <Modal isOpen={confirming} onClose={() => setConfirming(false)} title="확인" layer="confirm"><button>예</button></Modal>
    </Modal>
  )
}

async function openExchange(footer?: ReactNode) {
  const user = userEvent.setup()
  const view = render(<Opener footer={footer} />)
  await user.click(screen.getByRole('button', { name: '환전 열기' }))
  return { user, ...view }
}

describe('Modal', () => {
  it('제목을 이름으로 가진 모달 대화 상자로 알린다', () => {
    render(<Modal isOpen onClose={vi.fn()} title="환전">내용</Modal>)

    const dialog = screen.getByRole('dialog', { name: '환전' })
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(dialog).toHaveTextContent('내용')
  })

  it('닫혀 있으면 그리지 않는다', () => {
    render(<Modal isOpen={false} onClose={vi.fn()} title="환전">내용</Modal>)

    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('닫기 버튼을 누르면 onClose 를 부른다', async () => {
    const onClose = vi.fn()
    render(<Modal isOpen onClose={onClose} title="환전">내용</Modal>)

    await userEvent.click(screen.getByRole('button', { name: '닫기' }))

    expect(onClose).toHaveBeenCalledOnce()
  })

  it('Esc 를 누르면 onClose 를 부른다', async () => {
    const onClose = vi.fn()
    render(<Modal isOpen onClose={onClose} title="환전"><input aria-label="금액" /></Modal>)

    await userEvent.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalledOnce()
  })

  it('열면 닫기 버튼 대신 내용의 첫 칸으로 포커스, 닫으면 연 단추로 포커스 복귀', async () => {
    const { user } = await openExchange()

    expect(screen.getByRole('textbox', { name: '금액' })).toHaveFocus()
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByRole('button', { name: '환전 열기' })).toHaveFocus()
  })

  it('내용에 칸이 없으면 아래 줄의 첫 버튼으로 포커스', () => {
    render(
      <Modal isOpen onClose={vi.fn()} title="삭제" footer={<><button>취소</button><button>삭제하기</button></>}>
        <p>지울까요?</p>
      </Modal>
    )

    expect(screen.getByRole('button', { name: '취소' })).toHaveFocus()
  })

  it('Tab 은 끝에서 처음으로, Shift+Tab 은 처음에서 끝으로 창 안에서만 돈다', async () => {
    const { user } = await openExchange(<button>환전하기</button>)

    await user.tab()
    expect(screen.getByRole('button', { name: '환전하기' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('button', { name: '닫기' })).toHaveFocus()
    await user.tab({ shift: true })
    expect(screen.getByRole('button', { name: '환전하기' })).toHaveFocus()
  })

  it('열린 동안 바탕을 inert 로 막고 본문 스크롤을 잠갔다가 닫으면 되돌린다', async () => {
    const { user, container } = await openExchange()

    expect(container).toHaveAttribute('inert')
    expect(document.body.style.overflow).toBe('hidden')
    await user.keyboard('{Escape}')

    expect(container).not.toHaveAttribute('inert')
    expect(document.body.style.overflow).toBe('')
  })

  it('창 위에 뜬 확인 창은 Esc 로 그것만 닫고 아래 창은 그동안 inert', async () => {
    const outer = vi.fn()
    const inner = vi.fn()
    render(
      <>
        <Modal isOpen onClose={outer} title="계좌"><input aria-label="계좌명" /></Modal>
        <Modal isOpen onClose={inner} title="확인" layer="confirm"><button>예</button></Modal>
      </>
    )

    expect(screen.getByRole('button', { name: '예' })).toHaveFocus()
    expect(screen.getByRole('dialog', { name: '계좌' }).closest('[inert]')).not.toBeNull()
    await userEvent.keyboard('{Escape}')

    expect(inner).toHaveBeenCalledOnce()
    expect(outer).not.toHaveBeenCalled()
  })

  it('위 창을 닫으면 아래 창의 inert 를 풀고 그 창 안 연 단추로 포커스, 아래 창이 남은 동안 스크롤 잠금 유지', async () => {
    const user = userEvent.setup()
    render(<NestedConfirm />)
    const outer = screen.getByRole('dialog', { name: '계좌' })

    await user.click(screen.getByRole('button', { name: '계좌 삭제' }))
    expect(outer.closest('[inert]')).not.toBeNull()
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog', { name: '확인' })).toBeNull()
    expect(outer.closest('[inert]')).toBeNull()
    expect(screen.getByRole('button', { name: '계좌 삭제' })).toHaveFocus()
    expect(document.body.style.overflow).toBe('hidden')
  })

  it('창을 열면서 연 단추가 사라지면 닫은 뒤 main 으로 포커스', async () => {
    const user = userEvent.setup()
    render(<VanishingOpener />)

    await user.click(screen.getByRole('button', { name: '로그인' }))
    expect(screen.getByRole('textbox', { name: '이메일' })).toHaveFocus()
    await user.keyboard('{Escape}')

    expect(screen.getByRole('main')).toHaveFocus()
  })

  it('창을 닫으면서 연 단추가 사라지면 main 으로 포커스', async () => {
    const user = userEvent.setup()
    render(<OpenerGoneOnClose />)

    await user.click(screen.getByRole('button', { name: '로그인' }))
    await user.click(screen.getByRole('button', { name: '로그인하기' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByRole('main')).toHaveFocus()
  })

  it('closeOnBackdrop 이면 바탕을 눌러 닫고, 없으면 바탕을 눌러도 그대로', async () => {
    const onClose = vi.fn()
    const { rerender } = render(<Modal isOpen onClose={onClose} title="검색">내용</Modal>)
    const backdrop = () => screen.getByRole('dialog').previousElementSibling as HTMLElement

    await userEvent.click(backdrop())
    expect(onClose).not.toHaveBeenCalled()

    rerender(<Modal isOpen onClose={onClose} title="검색" closeOnBackdrop>내용</Modal>)
    await userEvent.click(backdrop())
    expect(onClose).toHaveBeenCalledOnce()
  })
})
