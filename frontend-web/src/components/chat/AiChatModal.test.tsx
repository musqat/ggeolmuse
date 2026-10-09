import { describe, it, expect, vi } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AiChatModal from './AiChatModal'
import { AiChatProvider } from '../../contexts/AiChatContext'
import { useAiChat } from '../../hooks/useAiChat'
import { aiChatApi } from '../../services/aiChatApi'

vi.mock('../../services/aiChatApi', () => ({ aiChatApi: { sendMessage: vi.fn() } }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: () => ({ isAuthenticated: true }) }))

const sendMessage = vi.mocked(aiChatApi.sendMessage)

// jsdom 에는 scrollTo 가 없다
Element.prototype.scrollTo = vi.fn()

// 차트의 "AI 분석" 버튼 자리
function Opener() {
  const { openChat } = useAiChat()
  return (
    <>
      <button onClick={() => openChat()}>채팅 열기</button>
      <button onClick={() => openChat('TSLA')}>TSLA 분석</button>
    </>
  )
}

describe('AiChatModal', () => {
  it('답을 기다리는 중에 종목 분석을 누르면 답이 온 뒤 그 종목을 한 번 분석한다', async () => {
    let resolveFirst!: (value: unknown) => void
    sendMessage
      .mockReturnValueOnce(new Promise((resolve) => { resolveFirst = resolve }) as never)
      .mockResolvedValueOnce({ data: { answer: 'TSLA 답', remaining: 3 } } as never)
    render(
      <AiChatProvider>
        <Opener />
        <AiChatModal onRequireLogin={vi.fn()} />
      </AiChatProvider>
    )

    await userEvent.click(screen.getByRole('button', { name: '채팅 열기' }))
    await userEvent.type(screen.getByPlaceholderText('예: AAPL 어때?'), '안녕{Enter}')
    await userEvent.click(screen.getByRole('button', { name: 'TSLA 분석' }))
    expect(sendMessage).toHaveBeenCalledTimes(1)

    await act(async () => { resolveFirst({ data: { answer: '첫 답', remaining: 4 } }) })

    expect(await screen.findByText('TSLA 답')).toBeInTheDocument()
    expect(sendMessage).toHaveBeenLastCalledWith('TSLA 분석해줘', 'TSLA')
    expect(sendMessage).toHaveBeenCalledTimes(2)
  })
})
