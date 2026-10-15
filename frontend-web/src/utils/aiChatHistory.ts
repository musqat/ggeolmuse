export interface ChatTurn {
  role: 'user' | 'ai';
  text: string;
}

// 지금 대화. 서버 대화 ID 와 종목, 화면에 보인 말풍선
export interface ChatSession {
  conversationId: string;
  symbol: string;
  name: string | null;
  turns: ChatTurn[];
}

const KEY = 'ggeolmuse_ai_chat';
const OLD_KEY = 'ggeolmuse_ai_chat_history'; // 종목 대화 전 모양(말풍선 배열만)
const MAX_TURNS = 40; // 최근 40개만 보관 (영구 저장 아님)

export function loadChatSession(): ChatSession | null {
  try {
    localStorage.removeItem(OLD_KEY);
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const saved = JSON.parse(raw) as Partial<ChatSession>;
    if (typeof saved.conversationId !== 'string' || typeof saved.symbol !== 'string' || !Array.isArray(saved.turns)) {
      return null;
    }
    return { conversationId: saved.conversationId, symbol: saved.symbol, name: saved.name ?? null, turns: saved.turns };
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
