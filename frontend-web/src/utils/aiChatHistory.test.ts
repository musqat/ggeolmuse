import { describe, it, expect, beforeEach } from 'vitest'
import { loadChatSession, saveChatSession } from './aiChatHistory'

const session = {
  conversationId: 'c1',
  symbol: 'AAPL',
  name: 'Apple Inc.',
  turns: [{ role: 'user' as const, text: 'RSI 어때?' }],
}

beforeEach(() => {
  localStorage.clear()
})

describe('aiChatHistory', () => {
  it('저장한 대화를 다시 읽는다', () => {
    saveChatSession(session)
    expect(loadChatSession()).toEqual(session)
  })

  it('말풍선은 최근 40개만 남긴다', () => {
    const turns = Array.from({ length: 45 }, (_, i) => ({ role: 'ai' as const, text: `답${i}` }))
    saveChatSession({ ...session, turns })
    expect(loadChatSession()?.turns.map((t) => t.text)).toEqual(turns.slice(-40).map((t) => t.text))
  })

  it('null 을 저장하면 지운다', () => {
    saveChatSession(session)
    saveChatSession(null)
    expect(localStorage.getItem('ggeolmuse_ai_chat')).toBeNull()
  })

  it('종목 대화 전 모양은 읽지 않고 지운다', () => {
    localStorage.setItem('ggeolmuse_ai_chat_history', JSON.stringify([{ role: 'user', text: 'AAPL 어때?' }]))
    expect(loadChatSession()).toBeNull()
    expect(localStorage.getItem('ggeolmuse_ai_chat_history')).toBeNull()
  })

  it('모양이 틀리면 null', () => {
    localStorage.setItem('ggeolmuse_ai_chat', JSON.stringify({ symbol: 'AAPL' }))
    expect(loadChatSession()).toBeNull()
    localStorage.setItem('ggeolmuse_ai_chat', '{깨진')
    expect(loadChatSession()).toBeNull()
  })
})
