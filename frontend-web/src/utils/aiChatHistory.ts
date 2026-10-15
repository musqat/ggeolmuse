export interface ChatTurn {
  role: 'user' | 'ai';
  text: string;
}

// 지금 대화. 서버 대화 ID 와 종목, 화면에 보인 말풍선, 마지막 응답 시각(ms)
export interface ChatSession {
  conversationId: string;
  symbol: string;
  name: string | null;
  turns: ChatTurn[];
  updatedAt: number;
}

const KEY = 'ggeolmuse_ai_chat';
const OLD_KEY = 'ggeolmuse_ai_chat_history'; // 종목 대화 전 모양(말풍선 배열만)
const MAX_TURNS = 40; // 최근 40개만 보관 (영구 저장 아님)
const SESSION_TTL_MS = 5 * 60 * 60 * 1000; // 서버 대화 수명(마지막 답부터 5시간)

export function loadChatSession(): ChatSession | null {
  try {
    localStorage.removeItem(OLD_KEY);
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const saved = JSON.parse(raw) as Partial<ChatSession>;
    if (typeof saved.conversationId !== 'string' || typeof saved.symbol !== 'string' || !Array.isArray(saved.turns)) {
      return null;
    }
    // 서버에서 끝난 대화는 읽지 않고 지운다
    if (typeof saved.updatedAt !== 'number' || Date.now() - saved.updatedAt > SESSION_TTL_MS) {
      localStorage.removeItem(KEY);
      return null;
    }
    return {
      conversationId: saved.conversationId,
      symbol: saved.symbol,
      name: saved.name ?? null,
      turns: saved.turns,
      updatedAt: saved.updatedAt,
    };
  } catch {
    return null;
  }
}

export function saveChatSession(session: ChatSession | null): void {
  try {
    if (session) {
      localStorage.setItem(KEY, JSON.stringify({ ...session, turns: session.turns.slice(-MAX_TURNS) }));
    } else {
      localStorage.removeItem(KEY);
    }
  } catch {
    // 저장 실패 무시
  }
}
