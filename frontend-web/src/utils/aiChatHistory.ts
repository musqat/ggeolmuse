export interface ChatTurn {
  role: 'user' | 'ai';
  text: string;
}

const KEY = 'ggeolmuse_ai_chat_history';
const MAX_TURNS = 40; // 최근 40개만 보관 (영구 저장 아님)

export function loadChatHistory(): ChatTurn[] {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as ChatTurn[]) : [];
  } catch {
    return [];
  }
}

export function saveChatHistory(turns: ChatTurn[]): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(turns.slice(-MAX_TURNS)));
  } catch {
    // 저장 실패 무시
  }
}

export function clearChatHistory(): void {
  localStorage.removeItem(KEY);
}
