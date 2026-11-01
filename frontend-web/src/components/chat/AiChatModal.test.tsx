import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders } from 'axios'
import AiChatModal from './AiChatModal'
import { AiChatProvider } from '../../contexts/AiChatContext'
import { useAiChat } from '../../hooks/useAiChat'
import { aiChatApi } from '../../services/aiChatApi'

const auth = vi.hoisted(() => ({ isAuthenticated: true, isLoading: false }))

vi.mock('../../services/aiChatApi', () => ({ aiChatApi: { startConversation: vi.fn(), ask: vi.fn(), quota: vi.fn() } }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: () => auth }))

const startConversation = vi.mocked(aiChatApi.startConversation)
const ask = vi.mocked(aiChatApi.ask)
const quota = vi.mocked(aiChatApi.quota)

const SYMBOL_INPUT = '종목 코드를 입력해주세요 (예: AAPL)'
const QUESTION_INPUT = '예: RSI 어때? · 1년 추이는? · 배당은?'
const HOUR = 60 * 60 * 1000

// jsdom 에 없는 scrollTo
Element.prototype.scrollTo = vi.fn()

function apiError(status: number, data: unknown) {
  const config = { headers: new AxiosHeaders() }
  return new AxiosError('Request failed', 'ERR_BAD_RESPONSE', config, null, {
    status, statusText: '', headers: {}, config, data,
  })
}

function started(symbol: string, name: string, remaining = 5) {
  return { data: { conversation_id: `c-${symbol}`, symbol, name, remaining } } as never
}

function answered(answer: string, remaining: number) {
  return { data: { answer, remaining, conversation_id: 'c-AAPL', symbol: 'AAPL', rejected: null } } as never
}

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

function tree() {
  return (
    <AiChatProvider>
      <Opener />
      <AiChatModal onRequireLogin={vi.fn()} />
    </AiChatProvider>
  )
}

function renderModal() {
  return render(tree())
}

async function openAndStart() {
  const view = renderModal()
  await userEvent.click(screen.getByRole('button', { name: '채팅 열기' }))
  await userEvent.type(screen.getByPlaceholderText(SYMBOL_INPUT), 'aapl{Enter}')
  return view
}

beforeEach(() => {
  localStorage.clear()
  // 구현까지 비움(실패한 테스트가 남긴 mockResolvedValueOnce 가 다음 테스트로 새지 않게)
  vi.resetAllMocks()
  auth.isAuthenticated = true
  auth.isLoading = false
  quota.mockResolvedValue({ data: { remaining: 5 } } as never)
})

describe('AiChatModal', () => {
  it('종목을 입력하면 대화를 시작하고 남은 횟수를 보인다', async () => {
    startConversation.mockResolvedValueOnce(started('AAPL', 'Apple Inc.'))
    await openAndStart()

    expect(await screen.findByText('AAPL · Apple Inc.')).toBeInTheDocument()
    expect(startConversation).toHaveBeenCalledWith('aapl')
    expect(screen.getByText('오늘 남은 횟수: 5회')).toBeInTheDocument()
    expect(screen.getByPlaceholderText(QUESTION_INPUT)).toBeInTheDocument()
  })

  it('없는 종목이면 입력 단계에 머물며 서버 문구를 보인다', async () => {
    startConversation.mockRejectedValueOnce(
      apiError(404, { code: 'symbol_not_found', message: '종목을 찾을 수 없습니다. 종목 코드로 입력해 주세요 (예: AAPL).' })
    )
    await openAndStart()

    expect(await screen.findByText('종목을 찾을 수 없습니다. 종목 코드로 입력해 주세요 (예: AAPL).')).toBeInTheDocument()
    expect(screen.getByPlaceholderText(SYMBOL_INPUT)).toBeInTheDocument()
  })

  it('질문하면 답과 남은 횟수를 보인다', async () => {
    startConversation.mockResolvedValueOnce(started('AAPL', 'Apple Inc.'))
    ask.mockResolvedValueOnce(answered('RSI 는 **중립**입니다.', 4))
    await openAndStart()

    await userEvent.type(await screen.findByPlaceholderText(QUESTION_INPUT), 'RSI 어때?{Enter}')

    expect(await screen.findByText('중립')).toBeInTheDocument()
    expect(ask).toHaveBeenCalledWith('c-AAPL', 'RSI 어때?')
    expect(screen.getByText('오늘 남은 횟수: 4회')).toBeInTheDocument()
  })

  it('대화가 만료되면 종목 입력으로 돌아간다', async () => {
    startConversation.mockResolvedValueOnce(started('AAPL', 'Apple Inc.'))
    ask.mockRejectedValueOnce(
      apiError(404, { code: 'conversation_not_found', message: '대화가 만료되었습니다. 종목을 다시 입력해 주세요.' })
    )
    await openAndStart()

    await userEvent.type(await screen.findByPlaceholderText(QUESTION_INPUT), 'RSI 어때?{Enter}')

    expect(await screen.findByText('대화가 만료되었습니다. 종목을 다시 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByPlaceholderText(SYMBOL_INPUT)).toBeInTheDocument()
  })

  it('이유 코드 없는 503 은 지금 환경에서 쓸 수 없다고 보인다', async () => {
    startConversation.mockRejectedValueOnce(apiError(503, { error: '서비스 사용 불가' }))
    await openAndStart()

    expect(await screen.findByText('AI 분석 기능은 현재 환경에서 사용할 수 없습니다.')).toBeInTheDocument()
  })

  it('종목 바꾸기를 누르면 종목 입력으로 돌아간다', async () => {
    startConversation.mockResolvedValueOnce(started('AAPL', 'Apple Inc.'))
    await openAndStart()

    await userEvent.click(await screen.findByRole('button', { name: '종목 바꾸기' }))

    expect(screen.getByPlaceholderText(SYMBOL_INPUT)).toBeInTheDocument()
    expect(localStorage.getItem('ggeolmuse_ai_chat')).toBeNull()
  })

  it('저장된 대화가 있으면 새로고침 뒤에도 그 종목 대화로 연다', async () => {
    localStorage.setItem('ggeolmuse_ai_chat', JSON.stringify({
      conversationId: 'c-AAPL', symbol: 'AAPL', name: 'Apple Inc.', turns: [{ role: 'ai', text: '이전 답' }],
      updatedAt: Date.now() - HOUR,
    }))
    renderModal()

    await userEvent.click(screen.getByRole('button', { name: '채팅 열기' }))

    expect(screen.getByText('AAPL · Apple Inc.')).toBeInTheDocument()
    expect(screen.getByText('이전 답')).toBeInTheDocument()
    expect(startConversation).not.toHaveBeenCalled()
  })

  it('5시간이 지난 대화는 종목 입력부터 다시 묻는다', async () => {
    localStorage.setItem('ggeolmuse_ai_chat', JSON.stringify({
      conversationId: 'c-AAPL', symbol: 'AAPL', name: 'Apple Inc.', turns: [{ role: 'ai', text: '이전 답' }],
      updatedAt: Date.now() - 6 * HOUR,
    }))
    renderModal()

    await userEvent.click(screen.getByRole('button', { name: '채팅 열기' }))

    expect(screen.getByPlaceholderText(SYMBOL_INPUT)).toBeInTheDocument()
    expect(screen.queryByText('이전 답')).not.toBeInTheDocument()
  })

  it('같은 종목 대화가 열려 있으면 차트 버튼이 새 대화를 만들지 않는다', async () => {
    localStorage.setItem('ggeolmuse_ai_chat', JSON.stringify({
      conversationId: 'c-TSLA', symbol: 'TSLA', name: 'Tesla, Inc.', turns: [], updatedAt: Date.now(),
    }))
    renderModal()

    await userEvent.click(screen.getByRole('button', { name: 'TSLA 분석' }))

    expect(screen.getByText('TSLA · Tesla, Inc.')).toBeInTheDocument()
    expect(startConversation).not.toHaveBeenCalled()
  })

  it('차트 버튼으로 열면 질문 없이 그 종목 대화를 시작한다', async () => {
    startConversation.mockResolvedValueOnce(started('TSLA', 'Tesla, Inc.'))
    renderModal()

    await userEvent.click(screen.getByRole('button', { name: 'TSLA 분석' }))

    expect(await screen.findByText('TSLA · Tesla, Inc.')).toBeInTheDocument()
    expect(startConversation).toHaveBeenCalledWith('TSLA')
    expect(ask).not.toHaveBeenCalled()
  })

  it('로그인이 풀리면 대화 기록을 지운다', async () => {
    startConversation.mockResolvedValueOnce(started('AAPL', 'Apple Inc.'))
    const view = await openAndStart()
    await screen.findByText('AAPL · Apple Inc.')
    expect(localStorage.getItem('ggeolmuse_ai_chat')).not.toBeNull()

    auth.isAuthenticated = false
    view.rerender(tree())

    expect(await screen.findByText('로그인하면 AI 종목 분석을 받아볼 수 있어요.')).toBeInTheDocument()
    expect(localStorage.getItem('ggeolmuse_ai_chat')).toBeNull()
  })

  it('답을 기다리는 중에 로그인이 풀리면 늦게 온 답을 기록하지 않는다', async () => {
    let resolveAnswer!: (value: unknown) => void
    startConversation.mockResolvedValueOnce(started('AAPL', 'Apple Inc.'))
    ask.mockReturnValueOnce(new Promise((resolve) => { resolveAnswer = resolve }) as never)
    const view = await openAndStart()
    await userEvent.type(await screen.findByPlaceholderText(QUESTION_INPUT), 'RSI 어때?{Enter}')

    auth.isAuthenticated = false
    view.rerender(tree())
    await act(async () => { resolveAnswer(answered('늦은 답', 4)) })

    expect(localStorage.getItem('ggeolmuse_ai_chat')).toBeNull()
  })

  it('로그인을 확인하는 동안에는 기록을 지우지 않는다', () => {
    const saved = JSON.stringify({ conversationId: 'c1', symbol: 'AAPL', name: null, turns: [], updatedAt: Date.now() })
    localStorage.setItem('ggeolmuse_ai_chat', saved)
    auth.isAuthenticated = false
    auth.isLoading = true

    renderModal()

    expect(localStorage.getItem('ggeolmuse_ai_chat')).toBe(saved)
  })

  it('답을 기다리는 중에 차트 버튼을 누르면 답을 보인 뒤 그 종목으로 바꿀 버튼을 띄운다', async () => {
    let resolveAnswer!: (value: unknown) => void
    startConversation
      .mockResolvedValueOnce(started('AAPL', 'Apple Inc.'))
      .mockResolvedValueOnce(started('TSLA', 'Tesla, Inc.'))
    ask.mockReturnValueOnce(new Promise((resolve) => { resolveAnswer = resolve }) as never)
    await openAndStart()
    await userEvent.type(await screen.findByPlaceholderText(QUESTION_INPUT), 'RSI 어때?{Enter}')

    await userEvent.click(screen.getByRole('button', { name: 'TSLA 분석' }))
    expect(screen.getByRole('button', { name: 'TSLA 대화로 바꾸기' })).toBeDisabled()

    await act(async () => { resolveAnswer(answered('첫 답', 4)) })

    expect(await screen.findByText('첫 답')).toBeInTheDocument()
    expect(screen.getByText('AAPL · Apple Inc.')).toBeInTheDocument()
    expect(startConversation).toHaveBeenCalledTimes(1)

    await userEvent.click(screen.getByRole('button', { name: 'TSLA 대화로 바꾸기' }))

    expect(await screen.findByText('TSLA · Tesla, Inc.')).toBeInTheDocument()
    expect(startConversation).toHaveBeenLastCalledWith('TSLA')
    expect(screen.queryByRole('button', { name: 'TSLA 대화로 바꾸기' })).not.toBeInTheDocument()
  })

  it('바꾸기 버튼을 두고 질문을 이어 가면 버튼을 거둔다', async () => {
    let resolveAnswer!: (value: unknown) => void
    startConversation.mockResolvedValueOnce(started('AAPL', 'Apple Inc.'))
    ask
      .mockReturnValueOnce(new Promise((resolve) => { resolveAnswer = resolve }) as never)
      .mockResolvedValueOnce(answered('둘째 답', 3))
    await openAndStart()
    await userEvent.type(await screen.findByPlaceholderText(QUESTION_INPUT), 'RSI 어때?{Enter}')
    await userEvent.click(screen.getByRole('button', { name: 'TSLA 분석' }))
    await act(async () => { resolveAnswer(answered('첫 답', 4)) })

    await userEvent.type(screen.getByPlaceholderText(QUESTION_INPUT), '배당은?{Enter}')

    expect(await screen.findByText('둘째 답')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'TSLA 대화로 바꾸기' })).not.toBeInTheDocument()
  })

  it('대화 중에 차트 버튼으로 다른 종목을 열면 종목을 확인하는 동안 그렇게 보인다', async () => {
    localStorage.setItem('ggeolmuse_ai_chat', JSON.stringify({
      conversationId: 'c-AAPL', symbol: 'AAPL', name: 'Apple Inc.', turns: [], updatedAt: Date.now(),
    }))
    startConversation.mockReturnValueOnce(new Promise(() => {}) as never)
    renderModal()

    await userEvent.click(screen.getByRole('button', { name: 'TSLA 분석' }))

    expect(screen.getByText('종목 확인 중...')).toBeInTheDocument()
    expect(screen.queryByText('분석 중...')).not.toBeInTheDocument()
  })

  it('대화 중에 차트 종목으로 시작하지 못하면 지금 대화를 두고 오류에 그 종목을 붙인다', async () => {
    localStorage.setItem('ggeolmuse_ai_chat', JSON.stringify({
      conversationId: 'c-AAPL', symbol: 'AAPL', name: 'Apple Inc.', turns: [{ role: 'ai', text: '이전 답' }],
      updatedAt: Date.now(),
    }))
    startConversation.mockRejectedValueOnce(
      apiError(404, { code: 'symbol_not_found', message: '종목을 찾을 수 없습니다. 종목 코드로 입력해 주세요 (예: AAPL).' })
    )
    renderModal()

    await userEvent.click(screen.getByRole('button', { name: 'TSLA 분석' }))

    expect(await screen.findByText('TSLA: 종목을 찾을 수 없습니다. 종목 코드로 입력해 주세요 (예: AAPL).')).toBeInTheDocument()
    expect(screen.getByText('AAPL · Apple Inc.')).toBeInTheDocument()
    expect(screen.getByText('이전 답')).toBeInTheDocument()
  })

  it('저장된 대화를 다시 열면 남은 횟수를 서버에 물어 보인다', async () => {
    localStorage.setItem('ggeolmuse_ai_chat', JSON.stringify({
      conversationId: 'c-AAPL', symbol: 'AAPL', name: 'Apple Inc.', turns: [], updatedAt: Date.now(),
    }))
    quota.mockResolvedValueOnce({ data: { remaining: 3 } } as never)
    renderModal()

    await userEvent.click(screen.getByRole('button', { name: '채팅 열기' }))

    expect(await screen.findByText('오늘 남은 횟수: 3회')).toBeInTheDocument()
  })

  it('남은 횟수를 한 번 알면 다시 묻지 않는다', async () => {
    startConversation.mockResolvedValueOnce(started('AAPL', 'Apple Inc.'))
    await openAndStart()
    await screen.findByText('AAPL · Apple Inc.')

    expect(quota).toHaveBeenCalledTimes(1)
  })

  it('늦게 온 남은 횟수 응답은 그 사이 받은 값을 덮지 않는다', async () => {
    let resolveQuota!: (value: unknown) => void
    quota.mockReturnValueOnce(new Promise((resolve) => { resolveQuota = resolve }) as never)
    startConversation.mockResolvedValueOnce(started('AAPL', 'Apple Inc.', 4))
    await openAndStart()
    expect(await screen.findByText('오늘 남은 횟수: 4회')).toBeInTheDocument()

    await act(async () => { resolveQuota({ data: { remaining: 5 } }) })

    expect(screen.getByText('오늘 남은 횟수: 4회')).toBeInTheDocument()
  })

  it('남은 횟수를 묻지 못하면 오류 없이 숨긴다', async () => {
    quota.mockRejectedValueOnce(apiError(503, {}))
    renderModal()

    await userEvent.click(screen.getByRole('button', { name: '채팅 열기' }))
    await act(async () => {})

    expect(quota).toHaveBeenCalledTimes(1)
    expect(screen.queryByText(/오늘 남은 횟수/)).not.toBeInTheDocument()
    expect(screen.queryByText('AI 분석 기능은 현재 환경에서 사용할 수 없습니다.')).not.toBeInTheDocument()
  })

  it('입력칸 길이를 서버 제한에 맞춘다', async () => {
    startConversation.mockResolvedValueOnce(started('AAPL', 'Apple Inc.'))
    renderModal()
    await userEvent.click(screen.getByRole('button', { name: '채팅 열기' }))
    expect(screen.getByPlaceholderText(SYMBOL_INPUT)).toHaveAttribute('maxlength', '16')

    await userEvent.type(screen.getByPlaceholderText(SYMBOL_INPUT), 'aapl{Enter}')

    expect(await screen.findByPlaceholderText(QUESTION_INPUT)).toHaveAttribute('maxlength', '500')
  })

  it('입력 검증(422)에 걸리면 입력을 확인하라고 보인다', async () => {
    startConversation.mockRejectedValueOnce(apiError(422, { detail: [{ msg: 'String should have at most 32 characters' }] }))
    await openAndStart()

    expect(await screen.findByText('입력 내용을 확인해 주세요.')).toBeInTheDocument()
  })
})
