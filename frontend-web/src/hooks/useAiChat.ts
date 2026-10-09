import { createContext, useContext } from 'react';

// AI 채팅 창 컨텍스트와 훅. Provider 는 contexts/AiChatContext.tsx 에 있다
export interface AiChatContextValue {
  isOpen: boolean;
  // 자동 분석할 종목 (버튼으로 열 때 설정). null이면 일반 채팅.
  autoSymbol: string | null;
  openChat: (symbol?: string) => void;
  closeChat: () => void;
  // 자동 분석 소비 후 초기화 (모달이 한 번 처리하고 비움)
  consumeAutoSymbol: () => void;
}

export const AiChatContext = createContext<AiChatContextValue | undefined>(undefined);

export const useAiChat = (): AiChatContextValue => {
  const ctx = useContext(AiChatContext);
  if (!ctx) throw new Error('useAiChat must be used within AiChatProvider');
  return ctx;
};
